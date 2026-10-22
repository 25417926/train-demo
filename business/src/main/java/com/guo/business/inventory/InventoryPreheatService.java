package com.guo.business.inventory;

import com.guo.business.TypeEnum.TrainCarriageTypeEnum;
import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_SeatExample;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.Daily_Train_TicketExample;
import com.guo.business.domain.InventoryHold;
import com.guo.business.mapper.Daily_Train_SeatMapper;
import com.guo.business.mapper.Daily_Train_TicketMapper;
import com.guo.business.mapper.InventoryHoldMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库存 key 初始化与预热（I04）。
 *
 * <p>以 MySQL 为准，把一个车次的余票、座位占用、候选池投影到 Redis：
 * <ul>
 *     <li>inv:t    区间余票（hash，字段 ydz/edz/rw/yw）</li>
 *     <li>inv:seat 座位占用（hash，字段 seg:{i}，值为 orderId 或 LEGACY）</li>
 *     <li>inv:pool 候选座位池（list，元素为 seatId）</li>
 * </ul>
 * 本服务只做全量重建，不做增量维护；重复执行会先删除该车次旧 key 再按库重建，结果一致。</p>
 */
@Service
public class InventoryPreheatService {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryPreheatService.class);

    private static final int SCAN_BATCH = 500;

    private static final String FIELD_YDZ = "ydz";
    private static final String FIELD_EDZ = "edz";
    private static final String FIELD_RW = "rw";
    private static final String FIELD_YW = "yw";

    @Resource
    private Daily_Train_TicketMapper dailyTrainTicketMapper;
    @Resource
    private Daily_Train_SeatMapper dailyTrainSeatMapper;
    @Resource
    private InventoryHoldMapper inventoryHoldMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 按车次单点重建库存 key。
     */
    public InventoryPreheatResult preheatTrain(Date date, String trainCode) {
        long begin = System.currentTimeMillis();
        String dateText = InventoryKey.formatDate(date);

        List<Daily_Train_Ticket> tickets = selectTickets(date, trainCode);
        List<Daily_Train_Seat> seats = selectSeats(date, trainCode);
        List<InventoryHold> holds = inventoryHoldMapper.selectHeldByTrain(date, trainCode);

        int deletedKeyCount = deleteTrainKeys(dateText, trainCode);

        Map<String, Map<String, String>> ticketHashes = buildTicketHashes(dateText, trainCode, tickets);
        Map<String, Map<String, String>> seatHashes = buildSeatHashes(dateText, trainCode, seats, holds);
        Map<String, List<String>> pools = buildPools(dateText, trainCode, tickets, seats);

        writeToRedis(ticketHashes, seatHashes, pools);

        InventoryPreheatResult result = new InventoryPreheatResult();
        result.setDate(dateText);
        result.setTrainCode(trainCode);
        result.setTrainCount(1);
        result.setTicketKeyCount(ticketHashes.size());
        result.setSeatKeyCount(seatHashes.size());
        result.setOccupiedSegmentCount(seatHashes.values().stream().mapToInt(Map::size).sum());
        result.setPoolKeyCount(pools.size());
        result.setPoolSeatCount(pools.values().stream().mapToLong(List::size).sum());
        result.setDeletedKeyCount(deletedKeyCount);
        result.setElapsedMs(System.currentTimeMillis() - begin);

        LOG.info("库存预热完成 date={} trainCode={} tickets={} seatKeys={} occupiedSegments={} "
                        + "poolKeys={} poolSeats={} deletedKeys={} elapsed={}ms",
                dateText, trainCode, tickets.size(), result.getSeatKeyCount(), result.getOccupiedSegmentCount(),
                result.getPoolKeyCount(), result.getPoolSeatCount(), deletedKeyCount, result.getElapsedMs());
        return result;
    }

    /**
     * 按日期预热全部车次（以 daily_train_ticket 的车次集合为准）。
     */
    public InventoryPreheatResult preheatDate(Date date) {
        long begin = System.currentTimeMillis();
        String dateText = InventoryKey.formatDate(date);
        List<String> trainCodes = dailyTrainTicketMapper.selectTrainCodesByDate(date);

        InventoryPreheatResult total = new InventoryPreheatResult();
        total.setDate(dateText);
        total.setTrainCode("ALL");
        for (String trainCode : trainCodes) {
            InventoryPreheatResult one = preheatTrain(date, trainCode);
            total.setTrainCount(total.getTrainCount() + 1);
            total.setTicketKeyCount(total.getTicketKeyCount() + one.getTicketKeyCount());
            total.setSeatKeyCount(total.getSeatKeyCount() + one.getSeatKeyCount());
            total.setOccupiedSegmentCount(total.getOccupiedSegmentCount() + one.getOccupiedSegmentCount());
            total.setPoolKeyCount(total.getPoolKeyCount() + one.getPoolKeyCount());
            total.setPoolSeatCount(total.getPoolSeatCount() + one.getPoolSeatCount());
            total.setDeletedKeyCount(total.getDeletedKeyCount() + one.getDeletedKeyCount());
        }
        total.setElapsedMs(System.currentTimeMillis() - begin);
        LOG.info("整日库存预热完成 date={} trains={} ticketKeys={} seatKeys={} poolKeys={} elapsed={}ms",
                dateText, total.getTrainCount(), total.getTicketKeyCount(), total.getSeatKeyCount(),
                total.getPoolKeyCount(), total.getElapsedMs());
        return total;
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

    private Map<String, Map<String, String>> buildTicketHashes(String dateText, String trainCode,
                                                               List<Daily_Train_Ticket> tickets) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Daily_Train_Ticket ticket : tickets) {
            String key = InventoryKey.ticket(dateText, trainCode, ticket.getStartIndex(), ticket.getEndIndex());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put(FIELD_YDZ, String.valueOf(ticket.getYdz()));
            fields.put(FIELD_EDZ, String.valueOf(ticket.getEdz()));
            fields.put(FIELD_RW, String.valueOf(ticket.getRw()));
            fields.put(FIELD_YW, String.valueOf(ticket.getYw()));
            result.put(key, fields);
        }
        return result;
    }

    private Map<String, Map<String, String>> buildSeatHashes(String dateText, String trainCode,
                                                             List<Daily_Train_Seat> seats,
                                                             List<InventoryHold> holds) {
        Map<Long, List<InventoryHold>> holdsBySeat = holds.stream()
                .filter(hold -> hold.getSeatId() != null)
                .collect(Collectors.groupingBy(InventoryHold::getSeatId));

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Daily_Train_Seat seat : seats) {
            String sell = seat.getSell();
            if (sell == null || sell.isEmpty()) {
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            for (int i = 0; i < sell.length(); i++) {
                if (sell.charAt(i) == '1') {
                    fields.put(InventoryKey.segField(i),
                            orderIdForSegment(holdsBySeat.get(seat.getId()), i));
                }
            }
            if (!fields.isEmpty()) {
                result.put(InventoryKey.seat(dateText, trainCode, seat.getId()), fields);
            }
        }
        return result;
    }

    /**
     * sell 第 i 位对应的订单号；历史占用没有账本记录时用 LEGACY 占位。
     */
    private String orderIdForSegment(List<InventoryHold> holds, int segIndex) {
        if (holds == null || holds.isEmpty()) {
            return InventoryKey.LEGACY_ORDER;
        }
        String orderId = null;
        for (InventoryHold hold : holds) {
            int from = hold.getStartIndex() - 1;
            int to = hold.getEndIndex() - 1;
            if (hold.getOrderId() != null && segIndex >= from && segIndex < to) {
                orderId = String.valueOf(hold.getOrderId());
            }
        }
        return orderId == null ? InventoryKey.LEGACY_ORDER : orderId;
    }

    private Map<String, List<String>> buildPools(String dateText, String trainCode,
                                                 List<Daily_Train_Ticket> tickets,
                                                 List<Daily_Train_Seat> seats) {
        Map<String, List<Daily_Train_Seat>> seatsByType = seats.stream()
                .filter(seat -> seat.getSeatType() != null)
                .collect(Collectors.groupingBy(Daily_Train_Seat::getSeatType));

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Daily_Train_Ticket ticket : tickets) {
            if (!isRangeValid(ticket)) {
                LOG.warn("余票区间非法，跳过候选池构建 date={} trainCode={} startIndex={} endIndex={}",
                        dateText, trainCode, ticket.getStartIndex(), ticket.getEndIndex());
                continue;
            }
            for (TrainCarriageTypeEnum seatType : TrainCarriageTypeEnum.values()) {
                List<Daily_Train_Seat> sameTypeSeats = seatsByType.get(seatType.getCode());
                if (sameTypeSeats == null || sameTypeSeats.isEmpty()) {
                    continue;
                }
                List<String> candidateIds = new ArrayList<>();
                for (Daily_Train_Seat seat : sameTypeSeats) {
                    if (isRangeFree(seat.getSell(), ticket.getStartIndex(), ticket.getEndIndex())) {
                        candidateIds.add(String.valueOf(seat.getId()));
                    }
                }
                if (!candidateIds.isEmpty()) {
                    result.put(InventoryKey.pool(dateText, trainCode, seatType.getCode(),
                            ticket.getStartIndex(), ticket.getEndIndex()), candidateIds);
                }
            }
        }
        return result;
    }

    private boolean isRangeValid(Daily_Train_Ticket ticket) {
        return ticket.getStartIndex() != null
                && ticket.getEndIndex() != null
                && ticket.getStartIndex() > 0
                && ticket.getEndIndex() > ticket.getStartIndex();
    }

    /**
     * 区间 [startIndex, endIndex) 在 sell 位图中全为 0 才可售。
     */
    private boolean isRangeFree(String sell, int startIndex, int endIndex) {
        if (sell == null || sell.isEmpty()) {
            return false;
        }
        int from = startIndex - 1;
        int to = endIndex - 1;
        if (from < 0 || to > sell.length() || from >= to) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (sell.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private void writeToRedis(Map<String, Map<String, String>> ticketHashes,
                              Map<String, Map<String, String>> seatHashes,
                              Map<String, List<String>> pools) {
        ticketHashes.forEach((key, fields) -> stringRedisTemplate.opsForHash().putAll(key, fields));
        seatHashes.forEach((key, fields) -> stringRedisTemplate.opsForHash().putAll(key, fields));
        pools.forEach((key, seatIds) -> stringRedisTemplate.opsForList().rightPushAll(key, seatIds));
    }

    private int deleteTrainKeys(String dateText, String trainCode) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(scanKeys(InventoryKey.ticketPattern(dateText, trainCode)));
        keys.addAll(scanKeys(InventoryKey.seatPattern(dateText, trainCode)));
        keys.addAll(scanKeys(InventoryKey.poolPattern(dateText, trainCode)));
        if (keys.isEmpty()) {
            return 0;
        }
        for (List<String> batch : partition(keys, SCAN_BATCH)) {
            stringRedisTemplate.delete(batch);
        }
        return keys.size();
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new LinkedHashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH).build();
        Cursor<byte[]> cursor = stringRedisTemplate.executeWithStickyConnection(
                (RedisCallback<Cursor<byte[]>>) connection -> connection.scan(options));
        try (Cursor<byte[]> closeable = cursor) {
            while (closeable.hasNext()) {
                keys.add(new String(closeable.next(), StandardCharsets.UTF_8));
            }
        }
        return keys;
    }

    private static List<List<String>> partition(Collection<String> source, int size) {
        List<List<String>> result = new ArrayList<>();
        List<String> batch = new ArrayList<>(size);
        for (String item : source) {
            batch.add(item);
            if (batch.size() == size) {
                result.add(batch);
                batch = new ArrayList<>(size);
            }
        }
        if (!batch.isEmpty()) {
            result.add(batch);
        }
        return result;
    }
}
