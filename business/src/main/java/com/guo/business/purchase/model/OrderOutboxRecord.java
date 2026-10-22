package com.guo.business.purchase.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * order_outbox：与受理同事务写入的事件审计记录（主投递走 RocketMQ 事务消息）。
 */
@Getter
@Setter
public class OrderOutboxRecord {

    private Long id;

    private String eventId;

    /** ORDER / QUALIFICATION / WAITLIST */
    private String aggregateType;

    private Long aggregateId;

    /** ORDER_CONFIRM / TICKET_CREATE / TICKET_DELETE / WAITLIST_MATCH */
    private String eventType;

    private String payload;

    /** N 新建 / S 已投递 / F 失败 / D 死信 */
    private String status;

    private Integer retryCount;

    private Date nextRetryAt;

    private Date createTime;

    private Date updateTime;
}
