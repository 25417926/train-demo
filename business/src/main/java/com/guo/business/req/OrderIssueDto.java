package com.guo.business.req;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 支付成功后触发出票的 MQ 消息体。
 */
@Getter
@Setter
@ToString
public class OrderIssueDto {

    private Long orderId;
    private String logId;
}
