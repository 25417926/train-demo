package com.guo.member.service;

import com.guo.common.req.MemberTicketReq;
import com.guo.common.req.TicketIssueReq;
import com.guo.member.domain.Ticket;
import com.guo.member.mapper.TicketMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I10 验收：按 (order_id, passenger_id) 幂等写票、按订单精确删票。
 */
class TicketServiceTest {

    private TicketMapper ticketMapper;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(TicketMapper.class);
        ticketService = new TicketService();
        org.springframework.test.util.ReflectionTestUtils.setField(ticketService, "ticketMapper", ticketMapper);
    }

    @Test
    void issueUsesInsertIgnoreAndCountsOnlyNewTickets() {
        when(ticketMapper.insertIgnore(any(Ticket.class))).thenReturn(1, 0);

        TicketIssueReq req = issueReq();
        req.getTickets().add(req.getTickets().get(0));
        int inserted = ticketService.issue(req);

        assertEquals(1, inserted);
        verify(ticketMapper, org.mockito.Mockito.times(2)).insertIgnore(any(Ticket.class));
    }

    @Test
    void issueRejectsRequestWithoutOrderId() {
        TicketIssueReq req = issueReq();
        req.setOrderId(null);

        assertThrows(IllegalArgumentException.class, () -> ticketService.issue(req));
        verify(ticketMapper, never()).insertIgnore(any(Ticket.class));
    }

    @Test
    void deleteByOrderIdDeletesOnlyThatOrder() {
        when(ticketMapper.deleteByOrderId(8001L)).thenReturn(2);

        assertEquals(2, ticketService.deleteByOrderId(8001L));
        verify(ticketMapper).deleteByOrderId(8001L);
    }

    private TicketIssueReq issueReq() {
        MemberTicketReq ticket = new MemberTicketReq();
        ticket.setPassengerId(6001L);
        ticket.setMemberId(7001L);
        ticket.setPassengerName("张三");
        ticket.setTrainCode("G100");

        TicketIssueReq req = new TicketIssueReq();
        req.setOrderId(8001L);
        req.setTickets(new java.util.ArrayList<>(Collections.singletonList(ticket)));
        return req;
    }
}
