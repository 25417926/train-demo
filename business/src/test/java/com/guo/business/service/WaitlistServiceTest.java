package com.guo.business.service;

import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.Waitlist_Application;
import com.guo.business.feign.MemberFeign;
import com.guo.business.mapper.Waitlist_ApplicationMapper;
import com.guo.business.req.WaitlistApplyReq;
import com.guo.business.resp.WaitlistApplyResp;
import com.guo.business.waitlist.WaitlistKey;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PassengerBriefResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * I15：候补申请/取消。DB 事实源用 Mock，Redis 主队列与计数用真实实例验证。
 */
class WaitlistServiceTest {

    private static final Long MEMBER_ID = 9001L;
    private static final Long PASSENGER_ID = 6001L;
    private static final String DATE_TEXT = "2099-03-03";
    private static final String TRAIN_CODE = "T15";
    private static final String START = "北京";
    private static final String END = "上海";
    private static final String SEAT_TYPE = "2";

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private Waitlist_ApplicationMapper waitlistMapper;
    private Daily_Train_TicketService dailyTrainTicketService;
    private MemberFeign memberFeign;
    private PlatformTransactionManager transactionManager;
    private WaitlistService waitlistService;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();

        waitlistMapper = mock(Waitlist_ApplicationMapper.class);
        dailyTrainTicketService = mock(Daily_Train_TicketService.class);
        memberFeign = mock(MemberFeign.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        waitlistService = new WaitlistService(waitlistMapper, dailyTrainTicketService,
                memberFeign, redis, transactionManager);

        cleanUp();
        when(dailyTrainTicketService.selectByUnique(any(), anyString(), anyString(), anyString()))
                .thenReturn(ticket());
        when(memberFeign.listPassengersByMember(MEMBER_ID)).thenReturn(passengerResp());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        factory.destroy();
    }

    @Test
    void applyTakesSeqAndEnqueuesOnce() {
        when(waitlistMapper.selectByRequestId("wlkey-0001")).thenReturn(null);
        when(waitlistMapper.insert(any(Waitlist_Application.class))).thenReturn(1);

        WaitlistApplyResp resp = waitlistService.apply(request(), MEMBER_ID, "wlkey-0001");

        assertTrue(resp.isCreated());
        assertEquals(1L, resp.getSeqNo());
        assertEquals("W", resp.getStatus());
        assertEquals(1.0, redis.opsForZSet().score(queueKey(), String.valueOf(resp.getApplicationId())));
        assertEquals("1", redis.opsForValue().get(countKey()));
        verify(waitlistMapper, times(1)).insert(any(Waitlist_Application.class));
    }

    @Test
    void duplicateRequestReturnsExistingWithoutIncrementingCountTwice() {
        when(waitlistMapper.selectByRequestId("wlkey-0001")).thenReturn(null);
        when(waitlistMapper.insert(any(Waitlist_Application.class))).thenReturn(1);

        WaitlistApplyResp first = waitlistService.apply(request(), MEMBER_ID, "wlkey-0001");

        ArgumentCaptor<Waitlist_Application> captor = ArgumentCaptor.forClass(Waitlist_Application.class);
        verify(waitlistMapper).insert(captor.capture());
        Waitlist_Application saved = captor.getValue();
        when(waitlistMapper.selectByRequestId("wlkey-0001")).thenReturn(saved);

        WaitlistApplyResp replay = waitlistService.apply(request(), MEMBER_ID, "wlkey-0001");

        assertFalse(replay.isCreated());
        assertEquals(first.getApplicationId(), replay.getApplicationId());
        assertEquals(first.getSeqNo(), replay.getSeqNo());
        assertEquals("1", redis.opsForValue().get(countKey()), "重复申请不得重复计数");
        assertEquals(1L, redis.opsForZSet().zCard(queueKey()));
    }

    @Test
    void duplicateActiveApplicationReturnsExistingAndHealsQueue() {
        when(waitlistMapper.selectByRequestId("wlkey-0002")).thenReturn(null);
        when(waitlistMapper.insert(any(Waitlist_Application.class)))
                .thenThrow(new DuplicateKeyException("uk_active"));
        Waitlist_Application existing = existingApplication();
        when(waitlistMapper.selectByActiveKey(anyString())).thenReturn(existing);

        WaitlistApplyResp resp = waitlistService.apply(request(), MEMBER_ID, "wlkey-0002");

        assertFalse(resp.isCreated());
        assertEquals(existing.getId(), resp.getApplicationId());
        assertEquals(existing.getSeqNo(), resp.getSeqNo());
        assertEquals(1L, redis.opsForZSet().zCard(queueKey()), "已有申请自愈入队");
        assertEquals("1", redis.opsForValue().get(countKey()));
    }

    @Test
    void cancelDequeuesAndAllowsReapplyAtQueueTail() {
        when(waitlistMapper.selectByRequestId(anyString())).thenReturn(null);
        when(waitlistMapper.insert(any(Waitlist_Application.class))).thenReturn(1);

        WaitlistApplyResp first = waitlistService.apply(request(), MEMBER_ID, "wlkey-0003");
        ArgumentCaptor<Waitlist_Application> captor = ArgumentCaptor.forClass(Waitlist_Application.class);
        verify(waitlistMapper).insert(captor.capture());
        Waitlist_Application saved = captor.getValue();
        saved.setId(first.getApplicationId());

        when(waitlistMapper.selectById(first.getApplicationId())).thenReturn(saved);
        when(waitlistMapper.cancel(eq(first.getApplicationId()), eq(MEMBER_ID), any(Date.class))).thenReturn(1);

        WaitlistApplyResp cancelled = waitlistService.cancel(first.getApplicationId(), MEMBER_ID);

        assertEquals("C", cancelled.getStatus());
        assertEquals(0L, redis.opsForZSet().zCard(queueKey()));
        assertEquals("0", redis.opsForValue().get(countKey()));

        // 取消后 active_key 置 NULL，可重新申请并排到队尾（新 seq_no）
        Waitlist_Application afterCancel = existingApplication();
        when(waitlistMapper.selectById(first.getApplicationId())).thenReturn(afterCancel);
        WaitlistApplyResp reapply = waitlistService.apply(request(), MEMBER_ID, "wlkey-0004");

        assertTrue(reapply.isCreated());
        assertEquals(2L, reapply.getSeqNo());
        assertEquals(1L, redis.opsForZSet().zCard(queueKey()));
        assertEquals("1", redis.opsForValue().get(countKey()));
    }

    @Test
    void applyRejectsPassengerNotOwnedByMember() {
        when(waitlistMapper.selectByRequestId("wlkey-0005")).thenReturn(null);
        CommonResp<java.util.List<PassengerBriefResp>> empty = new CommonResp<>(
                true, "成功", Collections.emptyList(), 200);
        when(memberFeign.listPassengersByMember(MEMBER_ID)).thenReturn(empty);

        assertThrows(ExceptionConsole.class,
                () -> waitlistService.apply(request(), MEMBER_ID, "wlkey-0005"));
        verify(waitlistMapper, never()).insert(any(Waitlist_Application.class));
    }

    private WaitlistApplyReq request() {
        WaitlistApplyReq req = new WaitlistApplyReq();
        req.setDate(date());
        req.setTrainCode(TRAIN_CODE);
        req.setStart(START);
        req.setEnd(END);
        req.setSeatType(SEAT_TYPE);
        req.setPassengerId(PASSENGER_ID);
        return req;
    }

    private Daily_Train_Ticket ticket() {
        Daily_Train_Ticket ticket = new Daily_Train_Ticket();
        ticket.setStartIndex(1);
        ticket.setEndIndex(3);
        return ticket;
    }

    private CommonResp<java.util.List<PassengerBriefResp>> passengerResp() {
        PassengerBriefResp passenger = new PassengerBriefResp();
        passenger.setId(PASSENGER_ID);
        passenger.setName("张三");
        return new CommonResp<>(true, "成功", Collections.singletonList(passenger), 200);
    }

    private Waitlist_Application existingApplication() {
        Waitlist_Application application = new Waitlist_Application();
        application.setId(7001L);
        application.setMemberId(MEMBER_ID);
        application.setPassengerId(PASSENGER_ID);
        application.setPassengerName("张三");
        application.setDate(date());
        application.setTrainCode(TRAIN_CODE);
        application.setStart(START);
        application.setEnd(END);
        application.setSeatType(SEAT_TYPE);
        application.setSeqNo(5L);
        application.setStatus("W");
        application.setActiveKey(MEMBER_ID + ":" + PASSENGER_ID + ":" + bucket());
        application.setCreateTime(new Date());
        application.setUpdateTime(new Date());
        return application;
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
        Set<String> keys = new HashSet<>(redis.keys("waitlist:*2099-03-03:T15:*"));
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
