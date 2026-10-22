package com.guo.business.TypeEnum;

import lombok.Getter;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/14
 */
@Getter
public enum RocketMqEnum {
    ORDER_CREATE_TOPIC("order_create_topic","订单创建"),
    ORDER_PAY_TOPIC("order_pay_topic","订单支付"),
    ORDER_CANCEL_TOPIC("order_cancel_topic","订单取消"),
    ORDER_REFUND_TOPIC("order_refund_topic","订单退款"),
    ORDER_COMPLETE_TOPIC("order_complete_topic","订单完成"),
    ORDER_CLOSE_TOPIC("order_close_topic","订单关闭"),
    //确认订单排队
    ORDER_CONFIRM("order_confirm","订单确认进入排队"),
    //支付成功后出票
    ORDER_ISSUE("order_issue","支付成功后出票"),
    //订单支付队列
    ORDER_PAY_QUEUE("order_pay_queue","订单支付队列"),
    //订单取消队列
    ORDER_CANCEL_QUEUE("order_cancel_queue","订单取消队列"),
    //订单退款队列
    ORDER_REFUND_QUEUE("order_refund_queue","订单退款队列"),
    //订单完成队列
    ORDER_COMPLETE_QUEUE("order_complete_queue","订单完成队列");

    private String code;
    private String desc;

    RocketMqEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;

    }
    public static TrainTypeEnum fromCode(String code, String desc) {
        for (TrainTypeEnum type : TrainTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }else if(type.getDesc().equals(desc)){
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}
