package com.guo.common.req;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * I10 出票请求：business 把同一订单的全部车票一次性交给 member，
 * member 侧按 (order_id, passenger_id) 唯一键幂等写入。
 */
@Getter
@Setter
@ToString
public class TicketIssueReq {

    private Long orderId;

    private List<MemberTicketReq> tickets;
}
