package com.guo.business.service;

import com.guo.business.TypeEnum.OrderCloseReasonEnum;
import lombok.Getter;
import lombok.Setter;

/**
 * I12 关单结果。alreadyClosed 为 true 表示本次调用没有产生任何副作用。
 */
@Getter
@Setter
public class OrderCloseResult {

    private Long orderId;

    private OrderCloseReasonEnum reason;

    private boolean alreadyClosed;

    /** 本次实际归还的 Redis 座位数 */
    private int releasedSeats;

    /** Redis 释放是否成功；失败由对账任务兜底 */
    private boolean redisReleased;

    public static OrderCloseResult closed(Long orderId, OrderCloseReasonEnum reason,
                                          int releasedSeats, boolean redisReleased) {
        OrderCloseResult result = new OrderCloseResult();
        result.setOrderId(orderId);
        result.setReason(reason);
        result.setAlreadyClosed(false);
        result.setReleasedSeats(releasedSeats);
        result.setRedisReleased(redisReleased);
        return result;
    }

    public static OrderCloseResult alreadyClosed(Long orderId, OrderCloseReasonEnum reason) {
        OrderCloseResult result = new OrderCloseResult();
        result.setOrderId(orderId);
        result.setReason(reason);
        result.setAlreadyClosed(true);
        result.setReleasedSeats(0);
        result.setRedisReleased(true);
        return result;
    }
}
