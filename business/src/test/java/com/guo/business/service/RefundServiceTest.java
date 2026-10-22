package com.guo.business.service;

import com.guo.business.TypeEnum.OrderCloseReasonEnum;
import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Payment;
import com.guo.business.domain.Refund;
import com.guo.business.mapper.Confirm_OrderMapper;
import com.guo.business.mapper.PaymentMapper;
import com.guo.business.mapper.RefundMapper;
import com.guo.business.req.RefundReq;
import com.guo.business.resp.RefundResp;
import com.guo.common.exception.ExceptionConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I13 验收：退款中不释放、成功只释放一次、失败回滚订单与流水。
 */
class RefundServiceTest {

    private static final long ORDER_ID = 8101L;
    private static final long MEMBER_ID = 7101L;
    private static final long PAYMENT_ID = 9101L;
    private static final long REFUND_ID = 9201L;
    private static final String REFUND_KEY = "REFUND:" + ORDER_ID;

    private RefundMapper refundMapper;
    private PaymentMapper paymentMapper;
    private Confirm_OrderMapper confirmOrderMapper;
    private PaymentService paymentService;
    private OrderCloseService orderCloseService;
    private RefundService service;

    @BeforeEach
    void setUp() {
        refundMapper = mock(RefundMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        confirmOrderMapper = mock(Confirm_OrderMapper.class);
        paymentService = mock(PaymentService.class);
        orderCloseService = mock(OrderCloseService.class);
        when(paymentMapper.selectByOrderId(ORDER_ID)).thenReturn(payment());

        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        service = new RefundService(refundMapper, paymentMapper, confirmOrderMapper,
                paymentService, orderCloseService, txManager);
    }

    @Test
    void successReleasesOnceAndMarksOrderRefunded() {
        Confirm_Order order = order("Y", "S");
        when(confirmOrderMapper.selectByPrimaryKey(ORDER_ID)).thenReturn(order);
        when(confirmOrderMapper.updateStatusCas(eq(ORDER_ID), eq("Y"), eq("G"), any(Date.class)))
                .thenReturn(1);
        when(confirmOrderMapper.updateStatusAndTicketCas(eq(ORDER_ID), eq("G"), eq("R"), eq("N"),
                any(Date.class))).thenReturn(1);
        when(refundMapper.markSuccess(eq(REFUND_KEY), any(Date.class), any(Date.class))).thenReturn(1);
        when(refundMapper.selectByIdempotentKey(REFUND_KEY)).thenReturn(refund("S"));

        OrderCloseService.CloseContext context = new OrderCloseService.CloseContext();
        when(orderCloseService.releaseInTransaction(eq(order), eq(OrderCloseReasonEnum.REFUND)))
                .thenReturn(context);

        RefundResp resp = service.refund(req(), MEMBER_ID);

        assertEquals("S", resp.getRefundStatus());
        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundMapper).insertOrResetRefunding(captor.capture());
        assertEquals(REFUND_KEY, captor.getValue().getIdempotentKey());
        assertEquals("I", captor.getValue().getRefundStatus());
        verify(paymentService, times(1)).markRefunded(PAYMENT_ID);
        verify(orderCloseService, times(1)).releaseInTransaction(eq(order), eq(OrderCloseReasonEnum.REFUND));
        verify(orderCloseService, times(1)).releaseAfterCommit(context);
    }

    @Test
    void repeatedRefundAfterSuccessReturnsExistingWithoutReleasingAgain() {
        when(confirmOrderMapper.selectByPrimaryKey(ORDER_ID)).thenReturn(order("R", "N"));
        when(refundMapper.selectByIdempotentKey(REFUND_KEY)).thenReturn(refund("S"));

        RefundResp resp = service.refund(req(), MEMBER_ID);

        assertEquals("S", resp.getRefundStatus());
        verify(confirmOrderMapper, never()).updateStatusCas(any(), any(), any(), any());
        verify(orderCloseService, never()).releaseInTransaction(any(), any());
        verify(orderCloseService, never()).releaseAfterCommit(any());
    }

    @Test
    void releaseFailureRollsBackOrderAndKeepsInventoryReserved() {
        Confirm_Order order = order("Y", "S");
        when(confirmOrderMapper.selectByPrimaryKey(ORDER_ID)).thenReturn(order);
        when(confirmOrderMapper.updateStatusCas(eq(ORDER_ID), eq("Y"), eq("G"), any(Date.class)))
                .thenReturn(1);
        when(confirmOrderMapper.updateStatusAndTicketCas(eq(ORDER_ID), eq("G"), eq("R"), eq("N"),
                any(Date.class))).thenReturn(1);
        when(refundMapper.markSuccess(eq(REFUND_KEY), any(Date.class), any(Date.class))).thenReturn(1);
        when(orderCloseService.releaseInTransaction(eq(order), eq(OrderCloseReasonEnum.REFUND)))
                .thenThrow(new IllegalStateException("redis down"));

        assertThrows(IllegalStateException.class, () -> service.refund(req(), MEMBER_ID));

        // 退款中已落 REFUNDING 流水，但库存释放未提交；失败后订单回 Y、流水 F
        verify(refundMapper).insertOrResetRefunding(any(Refund.class));
        verify(orderCloseService, never()).releaseAfterCommit(any());
        verify(confirmOrderMapper).updateStatusAndTicketCas(eq(ORDER_ID), eq("G"), eq("Y"), eq("S"),
                any(Date.class));
        verify(refundMapper).markFailed(eq(REFUND_KEY), contains("redis down"), any(Date.class));
    }

    @Test
    void refundRejectedWhenTicketsAreStillIssuing() {
        when(confirmOrderMapper.selectByPrimaryKey(ORDER_ID)).thenReturn(order("Y", "I"));

        assertThrows(ExceptionConsole.class, () -> service.refund(req(), MEMBER_ID));

        verify(confirmOrderMapper, never()).updateStatusCas(any(), any(), any(), any());
        verify(orderCloseService, never()).releaseInTransaction(any(), any());
    }

    private RefundReq req() {
        RefundReq req = new RefundReq();
        req.setOrderId(ORDER_ID);
        req.setReason("行程变更");
        return req;
    }

    private Confirm_Order order(String status, String ticketStatus) {
        Confirm_Order order = new Confirm_Order();
        order.setId(ORDER_ID);
        order.setMemberId(MEMBER_ID);
        order.setStatus(status);
        order.setTicketStatus(ticketStatus);
        return order;
    }

    private Payment payment() {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setOrderId(ORDER_ID);
        payment.setMemberId(MEMBER_ID);
        payment.setAmount(new BigDecimal("88.50"));
        return payment;
    }

    private Refund refund(String status) {
        Refund refund = new Refund();
        refund.setId(REFUND_ID);
        refund.setOrderId(ORDER_ID);
        refund.setPaymentId(PAYMENT_ID);
        refund.setMemberId(MEMBER_ID);
        refund.setAmount(new BigDecimal("88.50"));
        refund.setIdempotentKey(REFUND_KEY);
        refund.setRefundStatus(status);
        return refund;
    }
}
