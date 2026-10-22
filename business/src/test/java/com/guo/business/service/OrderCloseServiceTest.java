package com.guo.business.service;

import com.alibaba.fastjson.JSON;
import com.guo.business.TypeEnum.OrderCloseReasonEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.InventoryHold;
import com.guo.business.feign.MemberFeign;
import com.guo.business.inventory.InventoryResultCode;
import com.guo.business.inventory.InventoryService;
import com.guo.business.inventory.ReleaseCommand;
import com.guo.business.inventory.ReleaseResult;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.mapper.InventoryHoldMapper;
import com.guo.business.mapper.OrderCloseMapper;
import com.guo.business.purchase.model.SeatRecord;
import com.guo.business.req.Confirm_OrderTicketReq;
import com.guo.common.exception.ExceptionConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * I12 验收：超时关单只释放一次、重复关闭 0 副作用、库内账本同步回滚。
 */
class OrderCloseServiceTest {

    private static final long ORDER_ID = 8001L;
    private static final long MEMBER_ID = 7001L;
    private static final long TICKET_ID = 9001L;
    private static final long SEAT_ID = 101L;

    private Confirm_OrderMapper confirmOrderMapper;
    private OrderCloseMapper orderCloseMapper;
    private InventoryHoldMapper inventoryHoldMapper;
    private InventoryService inventoryService;
    private Daily_Train_TicketService dailyTrainTicketService;
    private MemberFeign memberFeign;
    private OrderCloseQueue orderCloseQueue;
    private StringRedisTemplate redis;
    private HashOperations<String, Object, Object> hashOps;
    private OrderCloseService service;

    @BeforeEach
    void setUp() {
        confirmOrderMapper = mock(Confirm_OrderMapper.class);
        orderCloseMapper = mock(OrderCloseMapper.class);
        inventoryHoldMapper = mock(InventoryHoldMapper.class);
        inventoryService = mock(InventoryService.class);
        dailyTrainTicketService = mock(Daily_Train_TicketService.class);
        memberFeign = mock(MemberFeign.class);
        orderCloseQueue = mock(OrderCloseQueue.class);
        redis = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);

        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        service = new OrderCloseService(confirmOrderMapper, orderCloseMapper, inventoryHoldMapper,
                inventoryService, dailyTrainTicketService, memberFeign, orderCloseQueue, redis, txManager);
    }

    @Test
    void timeoutCloseReleasesInventoryOnceAndRepeatedCloseHasNoSideEffect() {
        Confirm_Order order = order("S");
        InventoryHold hold = hold();
        when(confirmOrderMapper.selectByPrimaryKey(ORDER_ID)).thenReturn(order);
        when(orderCloseMapper.closeOrder(eq(ORDER_ID), eq("D"), eq("TIMEOUT"))).thenReturn(1, 0);
        when(inventoryHoldMapper.selectHeldByOrderId(ORDER_ID)).thenReturn(Collections.singletonList(hold));

        SeatRecord seat = new SeatRecord();
        seat.setId(SEAT_ID);
        seat.setSell("110");
        seat.setVersion(0);
        when(orderCloseMapper.selectSeat(SEAT_ID)).thenReturn(seat);
        when(orderCloseMapper.casSeatSell(eq(SEAT_ID), eq("000"), eq(0))).thenReturn(1);
        when(inventoryService.release(any(ReleaseCommand.class)))
                .thenReturn(ReleaseResult.of(InventoryResultCode.OK, 1));

        OrderCloseResult first = service.close(ORDER_ID, OrderCloseReasonEnum.TIMEOUT);
        assertFalse(first.isAlreadyClosed());
        assertEquals(1, first.getReleasedSeats());
        assertTrue(first.isRedisReleased());

        verify(orderCloseMapper, times(1)).closeOrder(ORDER_ID, "D", "TIMEOUT");
        verify(orderCloseMapper, times(1)).releaseQualifications(ORDER_ID);
        verify(orderCloseMapper, times(1)).releaseHolds(eq(ORDER_ID), any(Date.class));
        verify(orderCloseMapper, times(1)).insertReleaseRecord(anyLong(), eq("REL:8001:T"), anyLong(),
                eq(ORDER_ID), eq("T"), eq(1), eq("S"), any(Date.class));
        verify(orderCloseMapper, times(2)).insertOutbox(any());
        verify(orderCloseMapper, times(1)).increaseTicket(TICKET_ID, "ydz", 1);
        verify(orderCloseMapper, times(1)).casSeatSell(SEAT_ID, "000", 0);
        verify(inventoryService, times(1)).release(any(ReleaseCommand.class));
        verify(memberFeign, times(1)).deleteTicketsByOrder(ORDER_ID);
        verify(orderCloseMapper, times(1)).markOutboxSent("TICKET_DELETE:" + ORDER_ID);

        OrderCloseResult second = service.close(ORDER_ID, OrderCloseReasonEnum.TIMEOUT);
        assertTrue(second.isAlreadyClosed());
        // 第二次仍会执行 CAS，但返回 0 行，不再产生任何释放副作用
        verify(orderCloseMapper, times(2)).closeOrder(ORDER_ID, "D", "TIMEOUT");
        verify(inventoryHoldMapper, times(1)).selectHeldByOrderId(ORDER_ID);
        verify(inventoryService, times(1)).release(any(ReleaseCommand.class));
        verify(memberFeign, times(1)).deleteTicketsByOrder(ORDER_ID);
        verify(orderCloseQueue, times(2)).cancel(ORDER_ID);
    }

    @Test
    void cancelRejectsOtherMemberOrder() {
        Confirm_Order order = order("S");
        when(confirmOrderMapper.selectByPrimaryKey(ORDER_ID)).thenReturn(order);

        assertThrows(ExceptionConsole.class, () -> service.cancel(ORDER_ID, 9999L));

        verify(orderCloseMapper, never()).closeOrder(anyLong(), anyString(), anyString());
    }

    @Test
    void cancelAlreadyClosedOrderIsIdempotent() {
        Confirm_Order order = order("C");
        when(confirmOrderMapper.selectByPrimaryKey(ORDER_ID)).thenReturn(order);

        OrderCloseResult result = service.cancel(ORDER_ID, MEMBER_ID);

        assertTrue(result.isAlreadyClosed());
        verify(orderCloseMapper, never()).closeOrder(anyLong(), anyString(), anyString());
        verify(inventoryService, never()).release(any());
    }

    @Test
    void initOrderWithoutHoldReleasesUsingRedisHoldCredential() {
        Confirm_Order order = order("I");
        when(confirmOrderMapper.selectByPrimaryKey(ORDER_ID)).thenReturn(order);
        when(orderCloseMapper.closeOrder(eq(ORDER_ID), eq("D"), eq("TIMEOUT"))).thenReturn(1);
        when(inventoryHoldMapper.selectHeldByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());

        Map<Object, Object> hold = new HashMap<>();
        hold.put("orderId", String.valueOf(ORDER_ID));
        hold.put("seatType", "ydz");
        hold.put("startIndex", "1");
        hold.put("endIndex", "3");
        hold.put("seatIds", String.valueOf(SEAT_ID));
        hold.put("passengerIds", "6001");
        when(hashOps.entries("hold:HOLD-" + ORDER_ID)).thenReturn(hold);
        when(inventoryService.release(any(ReleaseCommand.class)))
                .thenReturn(ReleaseResult.of(InventoryResultCode.OK, 1));

        OrderCloseResult result = service.close(ORDER_ID, OrderCloseReasonEnum.TIMEOUT);

        assertFalse(result.isAlreadyClosed());
        verify(orderCloseMapper, times(1)).insertReleaseRecord(anyLong(), eq("REL:8001:T"), anyLong(),
                eq(ORDER_ID), eq("T"), eq(0), eq("S"), any(Date.class));
        verify(inventoryService, times(1)).release(any(ReleaseCommand.class));
        verify(orderCloseMapper, never()).increaseTicket(anyLong(), anyString(), anyInt());
    }

    private Confirm_Order order(String status) {
        Confirm_Order order = new Confirm_Order();
        order.setId(ORDER_ID);
        order.setMemberId(MEMBER_ID);
        order.setDate(cn.hutool.core.date.DateUtil.parse("2099-01-01"));
        order.setTrainCode("G100");
        order.setStart("北京");
        order.setEnd("上海");
        order.setDailyTrainTicketId(TICKET_ID);
        order.setQualificationId(5001L);
        order.setStatus(status);
        order.setTicketStatus("S");
        Confirm_OrderTicketReq ticket = new Confirm_OrderTicketReq();
        ticket.setPassengerId(6001L);
        ticket.setPassengerType("1");
        ticket.setPassengerName("张三");
        ticket.setPassengerIdCard("110101199001011234");
        ticket.setSeatTypeCode("1");
        order.setTickets(JSON.toJSONString(Collections.singletonList(ticket)));
        return order;
    }

    private InventoryHold hold() {
        InventoryHold hold = new InventoryHold();
        hold.setId(1L);
        hold.setHoldToken("HOLD-" + ORDER_ID);
        hold.setOrderId(ORDER_ID);
        hold.setMemberId(MEMBER_ID);
        hold.setPassengerId(6001L);
        hold.setDate(cn.hutool.core.date.DateUtil.parse("2099-01-01"));
        hold.setTrainCode("G100");
        hold.setSeatId(SEAT_ID);
        hold.setSeatType("1");
        hold.setStartIndex(1);
        hold.setEndIndex(3);
        hold.setStatus("H");
        return hold;
    }
}
