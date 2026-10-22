package com.guo.business.inventory;

import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.InventoryHold;
import com.guo.business.mapper.Daily_Train_SeatMapper;
import com.guo.business.mapper.Daily_Train_TicketMapper;
import com.guo.business.mapper.InventoryHoldMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * I11 对账验收：人为制造余票与座位差异后 1 次对账内修正；重复执行幂等；在途 hold 座位不被误删。
 *
 * <p>依赖本地 Redis（localhost:6379，db 0），测试前后清理 2099-02-02 / R11 的库存 key。</p>
 */
class InventoryReconcileServiceTest {

    private static final String DATE_TEXT = "2099-02-02";
    private static final String TRAIN_CODE = "R11";
    private static final long SEAT_ID = 201L;
    private static final long ORDER_ID = 9001L;
    private static final long IN_FLIGHT_ORDER_ID = 9002L;

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private SimpleMeterRegistry meterRegistry;
    private InventoryReconcileService reconcileService;

    private Daily_Train_TicketMapper ticketMapper;
    private Daily_Train_SeatMapper seatMapper;
    private InventoryHoldMapper holdMapper;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();

        ticketMapper = mock(Daily_Train_TicketMapper.class);
        seatMapper = mock(Daily_Train_SeatMapper.class);
        holdMapper = mock(InventoryHoldMapper.class);
        meterRegistry = new SimpleMeterRegistry();
        reconcileService = new InventoryReconcileService(
                ticketMapper, seatMapper, holdMapper, redis, meterRegistry);

        cleanUp();
        stubDatabase();
        seedDriftedRedis();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        factory.destroy();
    }

    @Test
    void reconcileFixesRedisDriftAndIsIdempotent() {
        InventoryReconcileReport first = reconcileService.reconcileTrain(date(), TRAIN_CODE);

        assertEquals(1, first.getTicketDrift(), "应发现 1 个余票字段差异");
        assertEquals(1, first.getSeatDrift(), "应发现 1 个座位区间差异");
        assertEquals(1, first.getLedgerDrift(), "账本占用未落库应只记录");
        assertEquals(2, first.getFixedCount(), "两项差异都应回写 Redis");
        assertEquals("10", redis.opsForHash().get(ticketKey(), "ydz"), "余票应回写为 MySQL 值");
        assertEquals(String.valueOf(ORDER_ID), redis.opsForHash().get(seatKey(), "seg:0"));
        assertNull(redis.opsForHash().get(seatKey(), "seg:2"), "库中无占用的区间应被删除");

        InventoryReconcileReport second = reconcileService.reconcileTrain(date(), TRAIN_CODE);
        assertEquals(0, second.getTicketDrift(), "一致后余票偏差应为 0");
        assertEquals(0, second.getSeatDrift(), "一致后座位偏差应为 0");
        assertEquals(0, second.getFixedCount(), "一致后不应再写入");
        assertEquals("10", redis.opsForHash().get(ticketKey(), "ydz"));

        assertEquals(0.0, meterRegistry.get("redis_inventory_drift").tag("scope", "ticket")
                .gauge().value(), "指标应反映最近一次对账的余票偏差");
        assertEquals(2.0, meterRegistry.get("reconcile_fix_total").counter().count(),
                "修正次数只统计实际写入");
    }

    @Test
    void inFlightHoldSeatIsNotCorrected() {
        redis.opsForHash().put(holdKey(IN_FLIGHT_ORDER_ID), "seatIds", String.valueOf(SEAT_ID));
        redis.opsForHash().put(seatKey(), "seg:1", String.valueOf(IN_FLIGHT_ORDER_ID));

        InventoryReconcileReport report = reconcileService.reconcileTrain(date(), TRAIN_CODE);

        assertEquals(0, report.getSeatDrift(), "在途 hold 覆盖的座位应跳过比对");
        assertEquals(String.valueOf(IN_FLIGHT_ORDER_ID), redis.opsForHash().get(seatKey(), "seg:1"),
                "在途预扣的占位不能被对账删除");
    }

    private void stubDatabase() {
        Daily_Train_Ticket ticket = new Daily_Train_Ticket();
        ticket.setId(1L);
        ticket.setDate(date());
        ticket.setTrainCode(TRAIN_CODE);
        ticket.setStartIndex(1);
        ticket.setEndIndex(3);
        ticket.setYdz(10);
        ticket.setEdz(-1);
        ticket.setRw(5);
        ticket.setYw(-1);

        Daily_Train_Seat seat = new Daily_Train_Seat();
        seat.setId(SEAT_ID);
        seat.setDate(date());
        seat.setTrainCode(TRAIN_CODE);
        seat.setCarriageIndex(1);
        seat.setCarriageSeatIndex(1);
        seat.setSeatType("1");
        seat.setSell("000");

        InventoryHold hold = new InventoryHold();
        hold.setId(1L);
        hold.setOrderId(ORDER_ID);
        hold.setSeatId(SEAT_ID);
        hold.setStartIndex(1);
        hold.setEndIndex(2);
        hold.setStatus("H");

        when(ticketMapper.selectByExample(any())).thenReturn(Collections.singletonList(ticket));
        when(ticketMapper.selectTrainCodesByDate(any())).thenReturn(Collections.singletonList(TRAIN_CODE));
        when(seatMapper.selectByExample(any())).thenReturn(Collections.singletonList(seat));
        when(holdMapper.selectHeldByTrain(any(), anyString())).thenReturn(Collections.singletonList(hold));
    }

    private void seedDriftedRedis() {
        Map<String, String> ticket = new HashMap<>();
        ticket.put("ydz", "9");
        ticket.put("edz", "-1");
        ticket.put("rw", "5");
        ticket.put("yw", "-1");
        redis.opsForHash().putAll(ticketKey(), ticket);

        redis.opsForHash().put(seatKey(), "seg:0", String.valueOf(ORDER_ID));
        redis.opsForHash().put(seatKey(), "seg:2", "8888");
    }

    private Date date() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(DATE_TEXT);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    private String ticketKey() {
        return InventoryKey.ticket(DATE_TEXT, TRAIN_CODE, 1, 3);
    }

    private String seatKey() {
        return InventoryKey.seat(DATE_TEXT, TRAIN_CODE, SEAT_ID);
    }

    private String holdKey(long orderId) {
        return InventoryKey.hold("HOLD-R11-" + orderId);
    }

    private void cleanUp() {
        Set<String> keys = new HashSet<>(redis.keys("inv:*:" + DATE_TEXT + ":" + TRAIN_CODE + ":*"));
        keys.addAll(redis.keys("hold:HOLD-R11-*"));
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
