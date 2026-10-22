package com.guo.common.req;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * I10 出票结果回写：member 出票完成后通过 Feign 通知 business 更新
 * confirm_order.ticket_status 与 order_outbox 审计状态。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatusUpdateReq {

    private Long orderId;

    private boolean success;

    private String reason;
}
