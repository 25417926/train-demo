package com.guo.business.req;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

public class PaymentReq {

    /**
     * 订单id
     */
    @NotNull(message = "【订单id】不能为空")
    private Long orderId;

    /**
     * 支付金额
     */
    @NotNull(message = "【支付金额】不能为空")
    private BigDecimal amount;

    /**
     * 支付渠道|A 支付宝 W 微信 B 银行卡
     */
    @NotBlank(message = "【支付渠道】不能为空")
    private String payChannel;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("orderId=").append(orderId);
        sb.append(", amount=").append(amount);
        sb.append(", payChannel=").append(payChannel);
        sb.append("]");
        return sb.toString();
    }
}
