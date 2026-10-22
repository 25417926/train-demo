package com.guo.common.event;

import com.guo.common.req.MemberTicketReq;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * I10 TICKET_CREATE 事件：business 出票落库成功后发布，member 消费并写会员车票。
 *
 * <p>车票明细随事件下发（事件携带状态），member 不需要跨库反查订单。</p>
 */
@Getter
@Setter
public class TicketCreateEvent {

    private Long orderId;

    private String requestId;

    private List<MemberTicketReq> tickets;
}
