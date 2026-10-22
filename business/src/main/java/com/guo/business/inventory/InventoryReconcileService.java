package com.guo.business.inventory;

import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_SeatExample;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.Daily_Train_TicketExample;
import com.guo.business.domain.InventoryHold;
import com.guo.business.mapper.Daily_Train_SeatMapper;
import com.guo.business.mapper.Daily_Train_TicketMapper;
import com.guo.business.mapper.InventoryHoldMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * I11 库存对账：每分钟比对 Redis 实时库存与 MySQL 账本，差异以 MySQL 为准回写 Redis。
 *
 * <p>三个比对口径：</p>
 * <ul>
 *     <li>ticket：{@code inv:t} 余票字段 vs {@code daily_train_ticket} 余票计数；差异回写 Redis。</li>
 *     <li>seat：{@code inv:seat} 座位区间占用 vs {@code inventory_hold} 聚合（叠加库内历史售卖位）；
 *     差异回写 Redis，不修改库。</li>
 *     <li>seat_ledger：{@code inventory_hold} 聚合 vs {@code daily_train_seat.sell}；只记录指标与日志，
 *     不写库，避免与出票事务并发覆盖。</li>
 * </ul>
 *
 * <p>对账幂等：回调一致后重复执行不再产生写入。在途 hold（Redis {@code hold:*}）覆盖的座位跳过修正，
 * 避免把刚预扣、尚未落库的占位当成差异清掉。</p>
 */
@Service
public class InventoryReconcileService {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryReconcileService.class);

    private static final int SCAN_BATCH = 500;

    private static final String FIELD_YDZ = "ydz";
    private static final String FIELD_EDZ = "edz";
    private static final String FIELD_RW = "rw";
    private static final String FIELD_YW = "yw";

    private static final String HOLD_PATTERN = InventoryKey.holdPrefix() + "*";

    private final Daily_Train_TicketMapper dailyTrainTicketMapper;
    private final Daily_Train_SeatMapper dailyTrainSeatMapper;
    private final InventoryHoldMapper inventoryHoldMapper;
    private final StringRedisTemplate redis;

    /** redis_inventory_drift{scope} 的当前值 */
    private final AtomicLong ticketDriftMetric = new AtomicLong();
    private final AtomicLong seatDriftMetric = new AtomicLong();
    private final AtomicLong ledgerDriftMetric = new AtomicLong();
    private final Counter fixCounter;

    public InventoryReconcileService(Daily_Train_TicketMapper dailyTrainTicketMapper,
                                     Daily_Train_SeatMapper dailyTrainSeatMapper,
                                     InventoryHoldMapper inventoryHoldMapper,
                                     StringRedisTemplate redis,
                                     MeterRegistry meterRegistry) {
        this.dailyTrainTicketMapper = dailyTrainTicketMapper;
        this.dailyTrainSeatMapper = dailyTrainSeatMapper;
        this.inventoryHoldMapper = inventoryHoldMapper;
        this.redis = redis;
        Gauge.builder("redis_inventory_drift", ticketDriftMetric, AtomicLong::doubleValue)
                .tag("scope", "ticket").register(meterRegistry);
        Gauge.builder("redis_inventory_drift", seatDriftMetric, AtomicLong::doubleValue)
                .tag("scope", "seat").register(meterRegistry);
        Gauge.builder("redis_inventory_drift", ledgerDriftMetric, AtomicLong::doubleValue)
                .tag("scope", "seat_ledger").register(meterRegistry);
        this.fixCounter = Counter.builder("reconcile_fix_total").register(meterRegistry);
    }

    /**
     * 按日期对账全部车次（车次集合以 daily_train_ticket 为准）。
     */
    public InventoryReconcileReport reconcileDate(Date date) {
        long begin = System.nanoTime();
        String dateText = InventoryKey.formatDate(date);
        List<String> trainCodes = dailyTrainTicketMapper.selectTrainCodesByDate(date);

        InventoryReconcileReport total = new InventoryReconcileReport();
        total.setDate(dateText);
        total.setTrainCode("ALL");
        for (String trainCode : trainCodes) {
            total.add(reconcileTrain(date, trainCode));
        }
        total.setElapsedMs((System.nanoTime() - begin) / 1_000_000L);

        publishDrift(total.getTicketDrift(), total.getSeatDrift(), total.getLedgerDrift());
        LOG.info("库存对账完成 date={} trains={} ticketDrift={} seatDrift={} ledgerDrift={} fixed={} elapsed={}ms",
                dateText, total.getTrainCount(), total.getTicketDrift(), total.getSeatDrift(),
                total.getLedgerDrift(), total.getFixedCount(), total.getElapsedMs());
        return total;
    }

    /**
     * 单车次对账：ticket / seat 差异回写 Redis，seat_ledger 差异只记录。
     */
    public InventoryReconcileReport reconcileTrain(Date date, String trainCode) {
        long begin = System.nanoTime();
        String dateText = InventoryKey.formatDate(date);

        List<Daily_Train_Ticket> tickets = selectTickets(date, trainCode);
        List<Daily_Train_Seat> seats = selectSeats(date, trainCode);
        List<InventoryHold> holds = inventoryHoldMapper.selectHeldByTrain(date, trainCode);
        Set<Long> protectedSeats = activeHoldSeatIds();

        InventoryReconcileReport report = new InventoryReconcileReport();
        report.setDate(dateText);
        report.setTrainCode(trainCode);
        report.setTrainCount(1);
        report.setTicketDrift(reconcileTickets(dateText, trainCode, tickets));
        report.setSeatDrift(reconcileSeats(dateText, trainCode, seats, holds, protectedSeats));
        report.setLedgerDrift(checkSeatLedger(dateText, trainCode, seats, holds));
        report.setFixedCount(report.getTicketDrift() + report.getSeatDrift());
        report.setElapsedMs((System.nanoTime() - begin) / 1_000_000L);

        publishDrift(report.getTicketDrift(), report.getSeatDrift(), report.getLedgerDrift());
        if (report.getTicketDrift() + report.getSeatDrift() + report.getLedgerDrift() > 0) {
            LOG.warn("库存对账存在偏差 date={} trainCode={} ticketDrift={} seatDrift={} ledgerDrift={} fixed={}",
                    dateText, trainCode, report.getTicketDrift(), report.getSeatDrift(),
                    report.getLedgerDrift(), report.getFixedCount());
        }
        return report;
    }

    /**
     * 余票对比：以 daily_train_ticket 为标准逐字段回写 inv:t，库中不存在的 key 直接清理。
     */
    private long reconcileTickets(String dateText, String trainCode, List<Daily_Train_Ticket> tickets) {
        long drift = 0;
        Set<String> dbKeys = new HashSet<>();
        for (Daily_Train_Ticket ticket : tickets) {
            if (ticket.getStartIndex() == null || ticket.getEndIndex() == null) {
                continue;
            }
            String key = InventoryKey.ticket(dateText, trainCode, ticket.getStartIndex(), ticket.getEndIndex());
            dbKeys.add(key);

            Map<String, String> expected = ticketFields(ticket);
            Map<Object, Object> actual = redis.opsForHash().entries(key);
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                String actualValue = actual.get(entry.getKey()) == null
                        ? null : String.valueOf(actual.get(entry.getKey()));
                if (entry.getValue().equals(actualValue)) {
                    continue;
                }
                drift++;
                fixCounter.increment();
                redis.opsForHash().put(key, entry.getKey(), entry.getValue());
                LOG.warn("对账修正 scope=ticket date={} trainCode={} range=[{}, {}) field={} redis={} db={}",
                        dateText, trainCode, ticket.getStartIndex(), ticket.getEndIndex(),
                        entry.getKey(), actualValue, entry.getValue());
            }
        }

        for (String key : scanKeys(InventoryKey.ticketPattern(dateText, trainCode))) {
            if (dbKeys.contains(key)) {
                continue;
            }
            long fieldCount = redis.opsForHash().size(key);
            drift += fieldCount;
            fixCounter.increment(fieldCount);
            redis.delete(key);
            LOG.warn("对账修正 scope=ticket date={} trainCode={} key={} 库中无对应余票记录，已删除 fields={}",
                    dateText, trainCode, key, fieldCount);
        }
        return drift;
    }

    /**
     * 座位占用对比：期望值 = inventory_hold 聚合 ∪ 库内 sell 位（无账本关联时记 LEGACY）。
     */
    private long reconcileSeats(String dateText, String trainCode, List<Daily_Train_Seat> seats,
                                List<InventoryHold> holds, Set<Long> protectedSeats) {
        Map<Long, Map<String, String>> expected = buildExpectedSeats(seats, holds);

        Map<Long, String> redisKeyBySeat = new LinkedHashMap<>();
        for (String key : scanKeys(InventoryKey.seatPattern(dateText, trainCode))) {
            Long seatId = parseSeatId(key, InventoryKey.seatPrefix(dateText, trainCode));
            if (seatId != null) {
                redisKeyBySeat.put(seatId, key);
            }
        }

        Set<Long> seatIds = new LinkedHashSet<>();
        seatIds.addAll(expected.keySet());
        seatIds.addAll(redisKeyBySeat.keySet());

        long drift = 0;
        for (Long seatId : seatIds) {
            if (protectedSeats.contains(seatId)) {
                // 在途 hold 覆盖的座位跳过，避免误删尚未落库的预扣
                continue;
            }
            String key = redisKeyBySeat.getOrDefault(seatId, InventoryKey.seat(dateText, trainCode, seatId));
            Map<String, String> want = expected.getOrDefault(seatId, Collections.emptyMap());
            Map<Object, Object> actual = redisKeyBySeat.containsKey(seatId)
                    ? redis.opsForHash().entries(key) : Collections.emptyMap();

            if (want.isEmpty()) {
                if (!actual.isEmpty()) {
                    drift += actual.size();
                    fixCounter.increment(actual.size());
                    redis.delete(key);
                    LOG.warn("对账修正 scope=seat date={} trainCode={} seatId={} 期望无占用，已删除 fields={}",
                            dateText, trainCode, seatId, actual.size());
                }
                continue;
            }

            for (Map.Entry<String, String> entry : want.entrySet()) {
                String actualValue = actual.get(entry.getKey()) == null
                        ? null : String.valueOf(actual.get(entry.getKey()));
                if (entry.getValue().equals(actualValue)) {
                    continue;
                }
                drift++;
                fixCounter.increment();
                redis.opsForHash().put(key, entry.getKey(), entry.getValue());
                LOG.warn("对账修正 scope=seat date={} trainCode={} seatId={} field={} redis={} expected={}",
                        dateText, trainCode, seatId, entry.getKey(), actualValue, entry.getValue());
            }

            for (Object field : actual.keySet()) {
                String fieldName = String.valueOf(field);
                if (want.containsKey(fieldName)) {
                    continue;
                }
                drift++;
                fixCounter.increment();
                redis.opsForHash().delete(key, fieldName);
                LOG.warn("对账修正 scope=seat date={} trainCode={} seatId={} field={} 库中无占用，已删除",
                        dateText, trainCode, seatId, fieldName);
            }
        }
        return drift;
    }

    /**
     * 座位账本比对：inventory_hold 聚合与 daily_train_seat.sell 不一致时只记日志与指标。
     */
    private long checkSeatLedger(String dateText, String trainCode,
                                 List<Daily_Train_Seat> seats, List<InventoryHold> holds) {
        Map<Long, Set<Integer>> holdSegments = new LinkedHashMap<>();
        for (InventoryHold hold : holds) {
            if (hold.getSeatId() == null || hold.getStartIndex() == null || hold.getEndIndex() == null) {
                continue;
            }
            Set<Integer> segments = holdSegments.computeIfAbsent(hold.getSeatId(), id -> new HashSet<>());
            for (int i = hold.getStartIndex() - 1; i < hold.getEndIndex() - 1; i++) {
                segments.add(i);
            }
        }

        long drift = 0;
        Set<Long> checked = new HashSet<>();
        for (Daily_Train_Seat seat : seats) {
            if (seat.getId() == null) {
                continue;
            }
            checked.add(seat.getId());
            Set<Integer> held = holdSegments.getOrDefault(seat.getId(), Collections.emptySet());
            String sell = seat.getSell() == null ? "" : seat.getSell();
            for (int i = 0; i < sell.length(); i++) {
                if (sell.charAt(i) == '1' && !held.contains(i)) {
                    LOG.info("对账差异 scope=seat_ledger date={} trainCode={} seatId={} seg={} 库内已售但无账本记录，仅记录",
                            dateText, trainCode, seat.getId(), i);
                }
            }
            for (Integer seg : held) {
                boolean sold = seg >= 0 && seg < sell.length() && sell.charAt(seg) == '1';
                if (!sold) {
                    drift++;
                    LOG.warn("对账偏差 scope=seat_ledger date={} trainCode={} seatId={} seg={} 账本占用未落库，仅记录",
                            dateText, trainCode, seat.getId(), seg);
                }
            }
        }
        for (Long seatId : holdSegments.keySet()) {
            if (!checked.contains(seatId)) {
                drift++;
                LOG.warn("对账偏差 scope=seat_ledger date={} trainCode={} seatId={} 库中无该座位，仅记录",
                        dateText, trainCode, seatId);
            }
        }
        return drift;
    }

    /**
     * 期望座位占用：先按 hold 聚合，再用库内 sell 位补齐历史占用（LEGACY）。
     */
    private Map<Long, Map<String, String>> buildExpectedSeats(List<Daily_Train_Seat> seats,
                                                              List<InventoryHold> holds) {
        Map<Long, Map<String, String>> expected = new LinkedHashMap<>();
        for (InventoryHold hold : holds) {
            if (hold.getSeatId() == null || hold.getOrderId() == null
                    || hold.getStartIndex() == null || hold.getEndIndex() == null) {
                continue;
            }
            Map<String, String> fields = expected.computeIfAbsent(hold.getSeatId(), id -> new LinkedHashMap<>());
            for (int i = hold.getStartIndex() - 1; i < hold.getEndIndex() - 1; i++) {
                fields.put(InventoryKey.segField(i), String.valueOf(hold.getOrderId()));
            }
        }
        for (Daily_Train_Seat seat : seats) {
            if (seat.getId() == null || seat.getSell() == null) {
                continue;
            }
            for (int i = 0; i < seat.getSell().length(); i++) {
                if (seat.getSell().charAt(i) != '1') {
                    continue;
                }
                expected.computeIfAbsent(seat.getId(), id -> new LinkedHashMap<>())
                        .putIfAbsent(InventoryKey.segField(i), InventoryKey.LEGACY_ORDER);
            }
        }
        return expected;
    }

    /**
     * 在途预扣的座位集合：hold:* 内记录 seatIds，避免把尚未落库的占位当差异清掉。
     */
    private Set<Long> activeHoldSeatIds() {
        Set<Long> seatIds = new HashSet<>();
        for (String holdKey : scanKeys(HOLD_PATTERN)) {
            Object value = redis.opsForHash().get(holdKey, "seatIds");
            if (value == null) {
                continue;
            }
            for (String item : String.valueOf(value).split(",")) {
                if (item.trim().isEmpty()) {
                    continue;
                }
                try {
                    seatIds.add(Long.valueOf(item.trim()));
                } catch (NumberFormatException e) {
                    LOG.warn("hold 凭据内 seatId 非法 holdKey={} value={}", holdKey, item);
                }
            }
        }
        return seatIds;
    }

    private Map<String, String> ticketFields(Daily_Train_Ticket ticket) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (ticket.getYdz() != null) {
            fields.put(FIELD_YDZ, String.valueOf(ticket.getYdz()));
        }
        if (ticket.getEdz() != null) {
            fields.put(FIELD_EDZ, String.valueOf(ticket.getEdz()));
        }
        if (ticket.getRw() != null) {
            fields.put(FIELD_RW, String.valueOf(ticket.getRw()));
        }
        if (ticket.getYw() != null) {
            fields.put(FIELD_YW, String.valueOf(ticket.getYw()));
        }
        return fields;
    }

    private void publishDrift(long ticketDrift, long seatDrift, long ledgerDrift) {
        ticketDriftMetric.set(ticketDrift);
        seatDriftMetric.set(seatDrift);
        ledgerDriftMetric.set(ledgerDrift);
    }

    private List<Daily_Train_Ticket> selectTickets(Date date, String trainCode) {
        Daily_Train_TicketExample example = new Daily_Train_TicketExample();
        example.createCriteria()
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode);
        return dailyTrainTicketMapper.selectByExample(example);
    }

    private List<Daily_Train_Seat> selectSeats(Date date, String trainCode) {
        Daily_Train_SeatExample example = new Daily_Train_SeatExample();
        example.createCriteria()
                .andDateEqualTo(date)
                .andTrainCodeEqualTo(trainCode);
        List<Daily_Train_Seat> seats = dailyTrainSeatMapper.selectByExample(example);
        seats.sort(Comparator.comparing(Daily_Train_Seat::getCarriageIndex)
                .thenComparing(Daily_Train_Seat::getCarriageSeatIndex));
        return seats;
    }

    private static Long parseSeatId(String key, String prefix) {
        if (!key.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.valueOf(key.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new LinkedHashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH).build();
        Cursor<byte[]> cursor = redis.executeWithStickyConnection(
                (RedisCallback<Cursor<byte[]>>) connection -> connection.scan(options));
        try (Cursor<byte[]> closeable = cursor) {
            while (closeable.hasNext()) {
                keys.add(new String(closeable.next(), StandardCharsets.UTF_8));
            }
        }
        return keys;
    }
}
