package com.guo.business.req;

import javax.validation.constraints.NotNull;

public class ChangeReq {

    /**
     * 原订单id（必填，用于先做退票）
     */
    @NotNull(message = "【原订单id】不能为空")
    private Long originalOrderId;

    /**
     * 新订单参数（改签后重新下单的车次/座位信息）
     * 注：不在此处递归 @Valid，沿用 BeforeDoConfirm 自身的校验时序
     */
    @NotNull(message = "【新订单参数】不能为空")
    private Confirm_OrderDoReq newOrder;

    public Long getOriginalOrderId() {
        return originalOrderId;
    }

    public void setOriginalOrderId(Long originalOrderId) {
        this.originalOrderId = originalOrderId;
    }

    public Confirm_OrderDoReq getNewOrder() {
        return newOrder;
    }

    public void setNewOrder(Confirm_OrderDoReq newOrder) {
        this.newOrder = newOrder;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("originalOrderId=").append(originalOrderId);
        sb.append(", newOrder=").append(newOrder);
        sb.append("]");
        return sb.toString();
    }
}
