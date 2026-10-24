package com.guo.business.purchase;

import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.inventory.InventoryKey;
import com.guo.business.inventory.InventoryService;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.business.service.Daily_Train_TicketService;
import com.guo.business.waitlist.WaitlistKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I06 受理验收：同键并发 50 次只 1 单；重复乘客返回 DUPLICATE_PURCHASE；失败回滚无残留。
 *
 * <p>依赖本地 Redis（localhost:6379，db 0），不依赖 MySQL 与 RocketMQ：
 * 本地事务写入与消息发送由 Mock 替代，Redis 预扣使用真实 Lua 脚本。</p>
 */
class PurchaseAcceptServiceTest {

    private static final String DATE_TEXT = "2099-01-02";
    private static final String TRAIN_CODE = "T06";
    private static final String SEAT_TYPE = "1";
    private static final int START_INDEX = 1;
    private static final int END_INDEX = 2;
    private static final String SEAT_ID = "201";

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private PurchaseTxWriter txWriter;
    private PurchaseConfirmProducer producer;
    private PurchaseAcceptService acceptService;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();

        InventoryService inventoryService = new InventoryService(redis);
        txWriter = mock(PurchaseTxWriter.class);
        producer = mock(PurchaseConfirmProducer.class);
        when(producer.send(any(PurchaseAcceptContext.class))).thenReturn(PurchaseSendResult.accepted());
        when(txWriter.findOrderId(anyString())).thenReturn(null);

        Daily_Train_TicketService ticketService = mock(Daily_Train_TicketService.class);
        Daily_Train_Ticket dailyTrainTicket = new Daily_Train_Ticket();
        dailyTrainTicket.setStartIndex(START_INDEX);
        dailyTrainTicket.setEndIndex(END_INDEX);
        when(ticketService.selectByUnique(any(Date.class), anyString(), anyString(), anyString()))
                .thenReturn(dailyTrainTicket);

        acceptService = new PurchaseAcceptService(inventoryService, ticketService, txWriter, producer,
                new com.guo.business.service.OrderCloseQueue(redis), redis,
                new com.guo.business.metrics.PurchaseAcceptMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

        cleanUp();
        Map<String, String> ticket = new HashMap<>();
        ticket.put("ydz", "1");
        redis.opsForHash().putAll(ticketKey(), ticket);
        redis.opsForList().rightPush(poolKey(), SEAT_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        factory.destroy();
    }

    @Test
    void sameIdempotencyKeyConcurrentOnlyOneOrder() throws Exception {
        String idempotencyKey = "test-key-concurrent-0001";
        long passengerId = 7001L;
        int attempts = 50;

        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PurchaseApplyResp>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return acceptService.accept(request(passengerId), 9001L, idempotencyKey);
                }));
            }
            start.countDown();

            Set<Long> orderIds = new HashSet<>();
            for (Future<PurchaseApplyResp> future : futures) {
                PurchaseApplyResp resp = future.get(10, TimeUnit.SECONDS);
                assertEquals("I", resp.getStatus());
                orderIds.add(resp.getOrderId());
            }

            assertEquals(1, orderIds.size(), "同键并发只能产生一个订单");
        } finally {
            executor.shutdownNow();
        }

        verify(producer, times(1)).send(any(PurchaseAcceptContext.class));
        assertEquals("0", redis.opsForHash().get(ticketKey(), "ydz"), "余票只应扣减一次");
        assertEquals(0L, redis.opsForList().size(poolKey()), "候选池只应被取走一个座位");
    }

    @Test
    void samePassengerWithAnotherKeyIsDuplicate() {
        long passengerId = 7101L;
        PurchaseApplyResp first = acceptService.accept(request(passengerId), 9001L, "test-key-first-0001");
        assertTrue(first.getOrderId() > 0);

        PurchaseException e = assertThrows(PurchaseException.class,
                () -> acceptService.accept(request(passengerId), 9001L, "test-key-second-0002"));
        assertEquals(PurchaseErrorCode.DUPLICATE_PURCHASE, e.getErrorCode());
    }

    @Test
    void messageFailureRollsBackReservation() {
        when(producer.send(any(PurchaseAcceptContext.class)))
                .thenReturn(new PurchaseSendResult(false, false, new IllegalStateException("broker down")));
        when(txWriter.findOrderId(anyString())).thenReturn(null);

        String idempotencyKey = "test-key-rollback-0001";
        PurchaseException e = assertThrows(PurchaseException.class,
                () -> acceptService.accept(request(7201L), 9001L, idempotencyKey));
        assertEquals(PurchaseErrorCode.ACCEPT_FAILED, e.getErrorCode());

        assertEquals("1", redis.opsForHash().get(ticketKey(), "ydz"), "失败回滚后余票应恢复");
        assertEquals(1L, redis.opsForList().size(poolKey()), "座位应回到候选池");
        assertNull(redis.opsForValue().get(InventoryKey.qualification(DATE_TEXT, TRAIN_CODE, 7201L)),
                "资格应删除");
        assertFalse(redis.hasKey(InventoryKey.purchaseRequest(idempotencyKey)), "幂等状态不应残留");
    }

    @Test
    void waitlistBacklogBlocksOrdinaryPurchaseEvenWithStock() {
        // 座位刚释放、余票充足，但票档仍有候补排队：普通下单必须被拒，余票留给匹配器
        String bucket = WaitlistKey.bucket(DATE_TEXT, TRAIN_CODE, "北京", "上海", SEAT_TYPE);
        redis.opsForValue().set(WaitlistKey.count(bucket), "3");
        assertEquals("1", redis.opsForHash().get(ticketKey(), "ydz"), "预置余票充足");

        PurchaseException e = assertThrows(PurchaseException.class,
                () -> acceptService.accept(request(7301L), 9001L, "test-key-waitlist-0001"));
        assertEquals(PurchaseErrorCode.WAITLIST_PRIORITY, e.getErrorCode());

        verify(producer, never()).send(any(PurchaseAcceptContext.class));
        assertEquals("1", redis.opsForHash().get(ticketKey(), "ydz"), "被拒时不得预扣余票");
        assertEquals(1L, redis.opsForList().size(poolKey()), "座位仍留在候选池");
        assertNull(redis.opsForValue().get(InventoryKey.qualification(DATE_TEXT, TRAIN_CODE, 7301L)),
                "被拒时不得写资格");
    }

    @Test
    void waitlistDrainedAllowsOrdinaryPurchase() {
        // 候补清空后闸门放开，普通下单恢复
        String bucket = WaitlistKey.bucket(DATE_TEXT, TRAIN_CODE, "北京", "上海", SEAT_TYPE);
        redis.opsForValue().set(WaitlistKey.count(bucket), "0");

        PurchaseApplyResp resp = acceptService.accept(request(7302L), 9001L, "test-key-waitlist-0002");

        assertEquals("I", resp.getStatus());
        verify(producer, times(1)).send(any(PurchaseAcceptContext.class));
        assertEquals("0", redis.opsForHash().get(ticketKey(), "ydz"), "余票正常扣减");
    }

    private PurchaseApplyReq request(long passengerId) {
        Confirm_OrderTicketReq ticket = new Confirm_OrderTicketReq();
        ticket.setPassengerId(passengerId);
        ticket.setPassengerType("1");
        ticket.setPassengerName("测试乘客");
        ticket.setPassengerIdCard("110101199001010011");
        ticket.setSeatTypeCode(SEAT_TYPE);

        PurchaseApplyReq req = new PurchaseApplyReq();
        req.setDate(date());
        req.setTrainCode(TRAIN_CODE);
        req.setStart("北京");
        req.setEnd("上海");
        req.setDailyTrainTicketId(1L);
        req.setTickets(Collections.singletonList(ticket));
        return req;
    }

    private Date date() {
        return cn.hutool.core.date.DateUtil.parse(DATE_TEXT);
    }

    private String ticketKey() {
        return InventoryKey.ticket(DATE_TEXT, TRAIN_CODE, START_INDEX, END_INDEX);
    }

    private String poolKey() {
        return InventoryKey.pool(DATE_TEXT, TRAIN_CODE, SEAT_TYPE, START_INDEX, END_INDEX);
    }

    private void cleanUp() {
        Set<String> keys = new HashSet<>(redis.keys("inv:*:" + DATE_TEXT + ":" + TRAIN_CODE + ":*"));
        keys.addAll(redis.keys("qual:" + DATE_TEXT + ":" + TRAIN_CODE + ":*"));
        keys.addAll(redis.keys("purchase:req:test-key-*"));
        keys.addAll(redis.keys("hold:HOLD-*"));
        keys.addAll(redis.keys("rel:ACCEPT-*"));
        keys.addAll(redis.keys("waitlist:count:*"));
        keys.add(InventoryKey.ORDER_CLOSE);
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
