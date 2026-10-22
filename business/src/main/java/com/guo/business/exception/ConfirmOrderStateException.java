package com.guo.business.exception;

/**
 * I07 订单状态非法流转异常：状态机拒绝或 CAS 并发冲突时抛出。
 */
public class ConfirmOrderStateException extends RuntimeException {

    public static final String CODE = "ORDER_STATUS_ILLEGAL_TRANSITION";

    private final Long orderId;
    private final String fromStatus;
    private final String toStatus;

    public ConfirmOrderStateException(Long orderId, String fromStatus, String toStatus) {
        super("订单状态不允许流转：" + fromStatus + " -> " + toStatus + "，orderId=" + orderId);
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public String getCode() {
        return CODE;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }
}
