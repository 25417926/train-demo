package com.guo.member.mq;

import com.guo.common.req.MemberTicketReq;
import com.guo.common.req.TicketIssueReq;
import com.guo.common.req.TicketStatusUpdateReq;
import com.guo.common.resp.CommonResp;
import com.guo.member.feign.BusinessOrderFeign;
import com.guo.member.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I10 验收：member 消费 TICKET_CREATE 幂等写票，并通过 Feign 回写 business 出票状态。
 */
class TicketCreateConsumeServiceTest {

    private static final String BODY = "{\"orderId\":8001,\"requestId\":\"R1\",\"tickets\":["
            + "{\"orderId\":8001,\"passengerId\":6001,\"trainCode\":\"G100\"}]}";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private TicketService ticketService;
    private BusinessOrderFeign businessOrderFeign;
    private TicketCreateDlqSender dlqSender;
    private TicketCreateConsumeService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        ticketService = mock(TicketService.class);
        businessOrderFeign = mock(BusinessOrderFeign.class);
        dlqSender = mock(TicketCreateDlqSender.class);

        service = new TicketCreateConsumeService(redis, ticketService, businessOrderFeign, dlqSender);
    }

    @Test
    void issuesTicketsAndReportsSuccess() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(businessOrderFeign.ticketStatus(8001L)).thenReturn(resp("I"));
        when(businessOrderFeign.updateTicketStatus(any())).thenReturn(voidResp());

        service.consume(BODY);

        verify(ticketService).issue(any(TicketIssueReq.class));
        ArgumentCaptor<TicketStatusUpdateReq> captor = ArgumentCaptor.forClass(TicketStatusUpdateReq.class);
        verify(businessOrderFeign).updateTicketStatus(captor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().isSuccess());
    }

    @Test
    void repeatedDeliveryIssuesTicketsOnce() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);
        when(businessOrderFeign.ticketStatus(8001L)).thenReturn(resp("I"));
        when(businessOrderFeign.updateTicketStatus(any())).thenReturn(voidResp());

        service.consume(BODY);
        service.consume(BODY);

        verify(ticketService, times(1)).issue(any(TicketIssueReq.class));
    }

    @Test
    void alreadyIssuedOrderSkipsWriteAndReport() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(businessOrderFeign.ticketStatus(8001L)).thenReturn(resp("S"));

        service.consume(BODY);

        verify(ticketService, never()).issue(any());
        verify(businessOrderFeign, never()).updateTicketStatus(any());
    }

    @Test
    void orderNotIssuingSkipsWrite() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(businessOrderFeign.ticketStatus(8001L)).thenReturn(resp("F"));

        service.consume(BODY);

        verify(ticketService, never()).issue(any());
        verify(businessOrderFeign, never()).updateTicketStatus(any());
    }

    @Test
    void failureIsRetriedFiveTimesThenMovedToDlq() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);
        when(businessOrderFeign.ticketStatus(8001L)).thenReturn(resp("I"));
        doThrow(new IllegalStateException("member db down")).when(ticketService).issue(any());

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalStateException.class, () -> service.consume(BODY));
        }
        service.consume(BODY);

        verify(ticketService, times(6)).issue(any());
        ArgumentCaptor<TicketStatusUpdateReq> captor = ArgumentCaptor.forClass(TicketStatusUpdateReq.class);
        verify(businessOrderFeign).updateTicketStatus(captor.capture());
        org.junit.jupiter.api.Assertions.assertFalse(captor.getValue().isSuccess());
        verify(dlqSender).send(eq(BODY), eq("8001"), eq(6), anyString());
    }

    private CommonResp<String> resp(String content) {
        return new CommonResp<>(true, "成功", content, 200);
    }

    private CommonResp<Void> voidResp() {
        return new CommonResp<>(true, "成功", null, 200);
    }
}
