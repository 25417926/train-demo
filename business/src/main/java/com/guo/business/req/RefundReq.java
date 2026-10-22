package com.guo.business.req;

import javax.validation.constraints.NotNull;

public class RefundReq {

    /**
     * 订单id
     */
    @NotNull(message = "【订单id】不能为空")
    private Long orderId;

    /**
     * 退款原因
     */
    private String reason;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("orderId=").append(orderId);
        sb.append(", reason=").append(reason);
        sb.append("]");
        return sb.toString();
    }
}
