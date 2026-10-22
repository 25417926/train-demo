package com.guo.business.mq;

import com.alibaba.fastjson.JSON;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.inventory.InventoryService;
import com.guo.business.mapper.OrderConfirmPersistMapper;
import com.guo.business.purchase.model.SeatRecord;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.business.service.TicketIssueService;
import com.guo.common.event.TicketCreateEvent;
import com.guo.common.req.MemberTicketReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I09 验收：重复消息只落库一次、座位冲突 3 次后明确失败并释放。
 */
class OrderConfirmTicketServiceTest {

    private OrderConfirmPersistMapper persistMapper;
    private InventoryService inventoryService;
    private StringRedisTemplate redis;
    private HashOperations<String, Object, Object> hashOps;
    private TicketCreateProducer ticketCreateProducer;
    private TicketIssueService ticketIssueService;
    private OrderConfirmTicketService service;

    @BeforeEach
    void setUp() {
        persistMapper = mock(OrderConfirmPersistMapper.class);
        inventoryService = mock(InventoryService.class);
        redis = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        ticketCreateProducer = mock(TicketCreateProducer.class);
        ticketIssueService = mock(TicketIssueService.class);

        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        service = new OrderConfirmTicketService(
                persistMapper, inventoryService, redis, txManager, ticketCreateProducer, ticketIssueService);
    }

    @Test
    void repeatedMessagesOnlyPersistOnce() {
        Confirm_Order order = order("S");
        when(persistMapper.selectOrderByRequestId("R1")).thenReturn(order);

        for (int i = 0; i < 10; i++) {
            service.handle(order.getId(), "R1", "H1");
        }

        verify(persistMapper, times(10)).selectOrderByRequestId("R1");
        verify(persistMapper, never()).casStatus(anyLong(), anyString(), anyString());
        verify(persistMapper, never()).insertHold(any());
        verify(inventoryService, never()).confirm(any());
        verify(ticketCreateProducer, never()).send(any());
    }

    @Test
    void firstDeliveryPersistsOrderSeatTicketAndQualification() {
        Confirm_Order order = order("I");
        when(persistMapper.selectOrderByRequestId("R1")).thenReturn(order);
        when(persistMapper.casStatus(order.getId(), "I", "P")).thenReturn(1);
        when(persistMapper.countHoldsByOrderId(order.getId())).thenReturn(0);
        when(hashOps.entries("hold:H1")).thenReturn(hold());

        SeatRecord seat = new SeatRecord();
        seat.setId(101L);
        seat.setSell("000");
        seat.setVersion(0);
        when(persistMapper.selectSeat(101L)).thenReturn(seat);
        when(persistMapper.casSeatSell(eq(101L), eq("110"), eq(0))).thenReturn(1);
        when(persistMapper.markSuccess(eq(order.getId()), any(Date.class))).thenReturn(1);
        when(ticketIssueService.buildTicketRequests(order)).thenReturn(ticketRequests());

        service.handle(order.getId(), "R1", "H1");

        verify(persistMapper).insertHold(any());
        verify(persistMapper).decreaseTicket(eq(9001L), eq("ydz"), eq(1));
        verify(persistMapper).consumeQualifications(order.getId());
        verify(persistMapper).markSuccess(eq(order.getId()), any(Date.class));
        verify(persistMapper).insertOutbox(any());
        verify(ticketCreateProducer).send(any(TicketCreateEvent.class));
        verify(inventoryService).confirm(any());
    }

    @Test
    void seatConflictReleasesAndMarksFailedAfterThreeAttempts() {
        Confirm_Order order = order("I");
        when(persistMapper.selectOrderByRequestId("R1")).thenReturn(order);
        when(persistMapper.casStatus(order.getId(), "I", "P")).thenReturn(1);
        when(persistMapper.countHoldsByOrderId(order.getId())).thenReturn(0);
        when(hashOps.entries("hold:H1")).thenReturn(hold());

        SeatRecord seat = new SeatRecord();
        seat.setId(101L);
        seat.setSell("000");
        seat.setVersion(0);
        when(persistMapper.selectSeat(101L)).thenReturn(seat);
        when(persistMapper.casSeatSell(anyLong(), anyString(), anyInt())).thenReturn(0);

        service.handle(order.getId(), "R1", "H1");

        verify(persistMapper, times(3)).casSeatSell(eq(101L), eq("110"), eq(0));
        verify(persistMapper, never()).markSuccess(anyLong(), any(Date.class));
        verify(persistMapper).markFailed(order.getId(), "SEAT_CONFLICT");
        verify(inventoryService).release(any());
        verify(inventoryService, never()).confirm(any());
    }

    @Test
    void missingHoldMarksOrderFailedWithoutPersisting() {
        Confirm_Order order = order("P");
        when(persistMapper.selectOrderByRequestId("R1")).thenReturn(order);
        when(persistMapper.countHoldsByOrderId(order.getId())).thenReturn(0);
        when(hashOps.entries("hold:H1")).thenReturn(new HashMap<>());

        service.handle(order.getId(), "R1", "H1");

        verify(persistMapper).markFailed(order.getId(), "HOLD_MISSING");
        verify(persistMapper, never()).insertHold(any());
        verify(inventoryService, never()).confirm(any());
    }

    @Test
    void existingHoldsSkipDuplicateTransaction() {
        Confirm_Order order = order("P");
        when(persistMapper.selectOrderByRequestId("R1")).thenReturn(order);
        when(persistMapper.countHoldsByOrderId(order.getId())).thenReturn(1);

        service.handle(order.getId(), "R1", "H1");

        verify(persistMapper, never()).insertHold(any());
        verify(persistMapper, never()).markSuccess(anyLong(), any(Date.class));
    }

    private Confirm_Order order(String status) {
        Confirm_Order order = new Confirm_Order();
        order.setId(8001L);
        order.setMemberId(7001L);
        order.setDate(new Date());
        order.setTrainCode("G100");
        order.setStart("北京");
        order.setEnd("上海");
        order.setDailyTrainTicketId(9001L);
        order.setRequestId("R1");
        order.setStatus(status);
        order.setTicketStatus("N");
        order.setExpireAt(new Date(System.currentTimeMillis() + 60_000));
        Confirm_OrderTicketReq ticket = new Confirm_OrderTicketReq();
        ticket.setPassengerId(6001L);
        ticket.setPassengerType("1");
        ticket.setPassengerName("张三");
        ticket.setPassengerIdCard("110101199001011234");
        ticket.setSeatTypeCode("1");
        List<Confirm_OrderTicketReq> tickets = Arrays.asList(ticket);
        order.setTickets(JSON.toJSONString(tickets));
        return order;
    }

    private Map<Object, Object> hold() {
        Map<Object, Object> hold = new HashMap<>();
        hold.put("orderId", "8001");
        hold.put("requestId", "R1");
        hold.put("seatType", "ydz");
        hold.put("startIndex", "1");
        hold.put("endIndex", "3");
        hold.put("seatIds", "101");
        hold.put("passengerIds", "6001");
        return hold;
    }

    private List<MemberTicketReq> ticketRequests() {
        MemberTicketReq req = new MemberTicketReq();
        req.setOrderId(8001L);
        req.setPassengerId(6001L);
        req.setTrainCode("G100");
        return Collections.singletonList(req);
    }
}
