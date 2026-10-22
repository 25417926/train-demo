package com.guo.business.mq;

import com.guo.business.domain.Confirm_Order;
import com.guo.business.mapper.OrderConfirmPersistMapper;
import com.guo.business.service.TicketIssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

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
 * I10 验收：事件重投不重复发票、成功后回写状态、超限转死信。
 */
class TicketCreateConsumeServiceTest {

    private static final String BODY = "{\"orderId\":8001,\"requestId\":\"R1\"}";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private OrderConfirmPersistMapper persistMapper;
    private TicketIssueService ticketIssueService;
    private TicketCreateDlqSender dlqSender;
    private TicketCreateConsumeService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        persistMapper = mock(OrderConfirmPersistMapper.class);
        ticketIssueService = mock(TicketIssueService.class);
        dlqSender = mock(TicketCreateDlqSender.class);

        service = new TicketCreateConsumeService(redis, persistMapper, ticketIssueService, dlqSender);
    }

    @Test
    void repeatedDeliveryIssuesTicketsOnce() {
        Confirm_Order order = order("S", "I");
        when(persistMapper.selectOrderById(8001L)).thenReturn(order);
        when(persistMapper.markTicketSuccess(8001L)).thenReturn(1);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);

        service.consume(BODY);
        service.consume(BODY);

        verify(ticketIssueService, times(1)).issueTickets(order);
        verify(persistMapper, times(1)).markTicketSuccess(8001L);
    }

    @Test
    void successMarksTicketStatusAndOutboxSent() {
        Confirm_Order order = order("S", "I");
        when(persistMapper.selectOrderById(8001L)).thenReturn(order);
        when(persistMapper.markTicketSuccess(8001L)).thenReturn(1);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service.consume(BODY);

        verify(ticketIssueService).issueTickets(order);
        verify(persistMapper).markOutboxSent("TICKET_CREATE:8001");
    }

    @Test
    void alreadyIssuedOrderSkipsMemberCall() {
        when(persistMapper.selectOrderById(8001L)).thenReturn(order("S", "S"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service.consume(BODY);

        verify(ticketIssueService, never()).issueTickets(any());
        verify(persistMapper).markOutboxSent("TICKET_CREATE:8001");
    }

    @Test
    void unknownOrderIsAckedWithoutIssue() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        service.consume(BODY);

        verify(ticketIssueService, never()).issueTickets(any());
        verify(persistMapper, never()).markTicketSuccess(any());
    }

    @Test
    void failureIsRetriedFiveTimesThenMovedToDlq() {
        when(persistMapper.selectOrderById(8001L)).thenReturn(order("S", "I"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);
        doThrow(new IllegalStateException("member down"))
                .when(ticketIssueService).issueTickets(any());

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalStateException.class, () -> service.consume(BODY));
        }
        service.consume(BODY);

        verify(ticketIssueService, times(6)).issueTickets(any());
        verify(persistMapper).markTicketFailed(8001L);
        verify(dlqSender).send(eq(BODY), eq("8001"), eq(6), anyString());
    }

    private Confirm_Order order(String status, String ticketStatus) {
        Confirm_Order order = new Confirm_Order();
        order.setId(8001L);
        order.setStatus(status);
        order.setTicketStatus(ticketStatus);
        return order;
    }
}
