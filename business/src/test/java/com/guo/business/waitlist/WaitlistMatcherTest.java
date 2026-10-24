package com.guo.business.waitlist;

import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.Waitlist_Application;
import com.guo.business.inventory.InventoryResultCode;
import com.guo.business.inventory.InventoryService;
import com.guo.business.inventory.ReserveCommand;
import com.guo.business.inventory.ReserveResult;
import com.guo.business.mapper.InventoryHoldMapper;
import com.guo.business.mapper.PurchaseAcceptMapper;
import com.guo.business.mapper.Waitlist_ApplicationMapper;
import com.guo.business.service.Daily_Train_TicketService;
import com.guo.business.service.OrderCloseQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I16 验收：事件幂等、同票档合并锁、FIFO 补位、失败 3 次退避、退避到期按原 seq_no 回队。
 *
 * <p>DB 与库存用 Mock，等待队列/退避队列/幂等标记使用真实 Redis 验证脚本行为。</p>
 */
class WaitlistMatcherTest {

    private static final long MEMBER_ID = 9001L;
    private static final long PASSENGER_ID = 6001L;
    private static final String DATE_TEXT = "2099-04-04";
    private static final String TRAIN_CODE = "T16";
    private static final String START = "北京";
    private static final String END = "上海";
    private static final String SEAT_TYPE = "2";

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private Waitlist_ApplicationMapper waitlistMapper;
    private InventoryHoldMapper inventoryHoldMapper;
    private PurchaseAcceptMapper purchaseAcceptMapper;
    private Daily_Train_TicketService dailyTrainTicketService;
    private InventoryService inventoryService;
    private OrderCloseQueue orderCloseQueue;
    private WaitlistMatcher matcher;
    private WaitlistRetryPromoter promoter;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();

        waitlistMapper = mock(Waitlist_ApplicationMapper.class);
        inventoryHoldMapper = mock(InventoryHoldMapper.class);
        purchaseAcceptMapper = mock(PurchaseAcceptMapper.class);
        dailyTrainTicketService = mock(Daily_Train_TicketService.class);
        inventoryService = mock(InventoryService.class);
        orderCloseQueue = mock(OrderCloseQueue.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        matcher = new WaitlistMatcher(waitlistMapper, inventoryHoldMapper, purchaseAcceptMapper,
                dailyTrainTicketService, inventoryService, orderCloseQueue, redis, transactionManager);
        promoter = new WaitlistRetryPromoter(waitlistMapper, redis, 100);

        cleanUp();
        when(dailyTrainTicketService.selectByUnique(any(), anyString(), anyString(), anyString()))
                .thenReturn(ticket());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        factory.destroy();
    }

    @Test
    void duplicateEventIsAckedWithoutSecondRound() {
        Waitlist_Application application = application(1L, 1L);
        enqueue(application);
        stubPersist(application);
        when(inventoryService.reserve(any(ReserveCommand.class))).thenReturn(ok());

        assertEquals(1, matcher.onRelease(event("E-1")));
        assertEquals(0, matcher.onRelease(event("E-1")), "同一 eventId 重复投递只执行一轮");

        verify(inventoryService, times(1)).reserve(any(ReserveCommand.class));
        verify(orderCloseQueue, times(1)).schedule(anyLong(), any(Date.class));

        ArgumentCaptor<Confirm_Order> captor = ArgumentCaptor.forClass(Confirm_Order.class);
        verify(purchaseAcceptMapper).insertInitOrder(captor.capture());
        Confirm_Order order = captor.getValue();
        assertEquals("H", order.getStatus(), "补位订单为候补待支付");
        assertNotNull(order.getExpireAt());
        verify(waitlistMapper).markMatched(eq(1L), anyLong(), any(Date.class), any(Date.class));
        verify(purchaseAcceptMapper).insertOutbox(any());
    }

    @Test
    void sameBucketEventIsMergedWhileAnotherRoundHoldsLock() {
        Waitlist_Application application = application(1L, 1L);
        enqueue(application);
        redis.opsForValue().set(WaitlistKey.matchLock(bucket()), "other-token", Duration.ofSeconds(2));

        assertEquals(0, matcher.onRelease(event("E-2")));

        assertEquals(1L, redis.opsForZSet().zCard(queueKey()), "被合并的事件不消费队列");
        verify(inventoryService, never()).reserve(any(ReserveCommand.class));
        assertEquals("1", redis.opsForValue().get(WaitlistKey.event("E-2")), "事件仍需落幂等标记");
    }

    @Test
    void headOfQueueIsMatchedFirstAndFailedOneKeepsSeqNo() {
        Waitlist_Application first = application(1L, 1L);
        Waitlist_Application second = application(2L, 2L);
        enqueue(first);
        enqueue(second);
        stubPersist(first);
        stubPersist(second);
        when(inventoryService.reserve(any(ReserveCommand.class)))
                .thenReturn(ok())
                .thenReturn(result(InventoryResultCode.NO_TICKET));

        assertEquals(1, matcher.onRelease(event("E-3")), "余票只够一位时只补成功率最高者");

        assertEquals(1L, redis.opsForZSet().zCard(queueKey()));
        assertEquals(2.0, redis.opsForZSet().score(queueKey(), "2"), "失败申请按原 seq_no 回队");
        assertEquals("1", redis.opsForValue().get(countKey()));
        verify(waitlistMapper, times(1)).markMatched(eq(1L), anyLong(), any(Date.class), any(Date.class));
    }

    @Test
    void threeConsecutiveStockFailuresMoveApplicationToBackoff() {
        Waitlist_Application application = application(3L, 7L);
        enqueue(application);
        when(waitlistMapper.selectById(3L)).thenReturn(application);
        when(inventoryService.reserve(any(ReserveCommand.class)))
                .thenReturn(result(InventoryResultCode.NO_TICKET));

        for (int round = 1; round <= 3; round++) {
            assertEquals(0, matcher.onRelease(event("F-" + round)));
        }

        assertEquals(0L, redis.opsForZSet().zCard(queueKey()), "第 3 次失败后不再回主队列");
        assertEquals("0", redis.opsForValue().get(countKey()));
        Double retryAt = redis.opsForZSet().score(WaitlistKey.retry(bucket()), "3");
        assertNotNull(retryAt, "连续失败 3 次进入退避队列");
        long delay = retryAt.longValue() - System.currentTimeMillis();
        assertTrue(delay > 20_000 && delay <= 30_000, "退避时长约 30s，实际 " + delay);
        assertNull(redis.opsForValue().get(WaitlistKey.fail(3L)));
    }

    @Test
    void promoterRestoresBackoffApplicationWithOriginalSeqNo() {
        Waitlist_Application application = application(4L, 9L);
        when(waitlistMapper.selectById(4L)).thenReturn(application);
        redis.opsForZSet().add(WaitlistKey.retry(bucket()), "4", System.currentTimeMillis() - 1_000);
        // 推进器按索引 Set 遍历票档，不再 KEYS 扫描
        redis.opsForSet().add(WaitlistKey.retryIndex(), bucket());

        promoter.promote();

        assertEquals(9.0, redis.opsForZSet().score(queueKey(), "4"), "按原 seq_no 回队");
        assertEquals(1L, redis.opsForZSet().zCard(queueKey()));
        assertEquals("1", redis.opsForValue().get(countKey()));
        assertEquals(0L, redis.opsForZSet().zCard(WaitlistKey.retry(bucket())));
    }

    @Test
    void promoterDropsApplicationNoLongerWaiting() {
        Waitlist_Application cancelled = application(5L, 11L);
        cancelled.setStatus("C");
        when(waitlistMapper.selectById(5L)).thenReturn(cancelled);
        redis.opsForZSet().add(WaitlistKey.retry(bucket()), "5", System.currentTimeMillis() - 1_000);
        redis.opsForSet().add(WaitlistKey.retryIndex(), bucket());

        promoter.promote();

        assertEquals(0L, redis.opsForZSet().zCard(WaitlistKey.retry(bucket())));
        assertEquals(0L, redis.opsForZSet().zCard(queueKey()));
    }

    @Test
    void pendingBucketIsDrainedAfterLockReleased() {
        Waitlist_Application application = application(10L, 1L);
        enqueue(application);
        stubPersist(application);
        when(inventoryService.reserve(any(ReserveCommand.class))).thenReturn(ok());
        redis.opsForValue().set(WaitlistKey.matchLock(bucket()), "other-token", Duration.ofSeconds(30));

        assertEquals(0, matcher.onRelease(event("P-1")), "锁被占用时先登记待补位");
        assertEquals("1", redis.opsForValue().get(WaitlistKey.matchPending(bucket())));

        // 持锁方结束后由巡检兜底消费，事件不能被丢弃
        redis.delete(WaitlistKey.matchLock(bucket()));
        matcher.drainPending();

        assertEquals(0L, redis.opsForZSet().zCard(queueKey()), "待补位标记最终被消费");
        verify(waitlistMapper).markMatched(eq(10L), anyLong(), any(Date.class), any(Date.class));
        assertNull(redis.opsForValue().get(WaitlistKey.matchPending(bucket())));
    }

    @Test
    void batchReleaseConsumesMoreThanOneRound() {
        int total = 150;
        for (int i = 1; i <= total; i++) {
            Waitlist_Application application = application((long) i, (long) i);
            enqueue(application);
            stubPersist(application);
        }
        when(inventoryService.reserve(any(ReserveCommand.class))).thenReturn(ok());

        assertEquals(total, matcher.onRelease(event("B-1")),
                "单个释放事件连续推进多轮，批量释放不会被截断在 100 个");
        assertEquals(0L, redis.opsForZSet().zCard(queueKey()));
    }

    private void stubPersist(Waitlist_Application application) {
        when(waitlistMapper.selectById(application.getId())).thenReturn(application);
        when(waitlistMapper.selectByIdForUpdate(application.getId())).thenReturn(application);
        when(waitlistMapper.transferQualification(anyLong(), anyLong(), any(Date.class), anyString(),
                anyString(), anyString(), anyString())).thenReturn(1);
        when(waitlistMapper.selectQualificationIdByOrderId(anyLong())).thenReturn(5001L);
        when(waitlistMapper.markMatched(eq(application.getId()), anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(1);
        when(purchaseAcceptMapper.insertInitOrder(any(Confirm_Order.class))).thenReturn(1);
    }

    private void enqueue(Waitlist_Application application) {
        redis.opsForZSet().add(queueKey(), String.valueOf(application.getId()), application.getSeqNo());
        redis.opsForValue().set(countKey(), String.valueOf(redis.opsForZSet().zCard(queueKey())));
    }

    private WaitlistMatchEvent event(String eventId) {
        WaitlistMatchEvent event = new WaitlistMatchEvent();
        event.setEventId(eventId);
        event.setOrderId(8001L);
        event.setReason("T");
        event.setDate(DATE_TEXT);
        event.setTrainCode(TRAIN_CODE);
        event.setStart(START);
        event.setEnd(END);
        event.setSeatType(SEAT_TYPE);
        event.setReleasedSeats(1);
        return event;
    }

    private Waitlist_Application application(Long id, Long seqNo) {
        Waitlist_Application application = new Waitlist_Application();
        application.setId(id);
        application.setRequestId("WL-" + id);
        application.setMemberId(MEMBER_ID);
        application.setPassengerId(PASSENGER_ID);
        application.setPassengerName("张三");
        application.setDate(date());
        application.setTrainCode(TRAIN_CODE);
        application.setStart(START);
        application.setEnd(END);
        application.setSeatType(SEAT_TYPE);
        application.setSeqNo(seqNo);
        application.setStatus("W");
        application.setCreateTime(new Date());
        application.setUpdateTime(new Date());
        return application;
    }

    private Daily_Train_Ticket ticket() {
        Daily_Train_Ticket ticket = new Daily_Train_Ticket();
        ticket.setId(7001L);
        ticket.setStartIndex(1);
        ticket.setEndIndex(3);
        return ticket;
    }

    private ReserveResult ok() {
        return result(InventoryResultCode.OK);
    }

    private ReserveResult result(InventoryResultCode code) {
        if (code != InventoryResultCode.OK) {
            return ReserveResult.of(code, null, null, Collections.emptyList(), code.name());
        }
        return ReserveResult.of(code, "8001", "HOLD-8001", Collections.singletonList("101"), "ok");
    }

    private Date date() {
        return cn.hutool.core.date.DateUtil.parse(DATE_TEXT);
    }

    private String bucket() {
        return WaitlistKey.bucket(DATE_TEXT, TRAIN_CODE, START, END, SEAT_TYPE);
    }

    private String queueKey() {
        return WaitlistKey.queue(bucket());
    }

    private String countKey() {
        return WaitlistKey.count(bucket());
    }

    private void cleanUp() {
        Set<String> keys = new HashSet<>();
        keys.addAll(redis.keys("waitlist:*" + bucket() + "*"));
        keys.addAll(redis.keys("wl:evt:*"));
        keys.addAll(redis.keys("waitlist:fail:*"));
        keys.add(WaitlistKey.matchPendingIndex());
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
        redis.opsForSet().remove(WaitlistKey.retryIndex(), bucket());
    }
}
