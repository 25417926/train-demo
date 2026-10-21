package com.guo.business.TypeEnum;

import lombok.Getter;
import lombok.ToString;

/**
 * @Description 支付状态枚举
 * @Author: 派
 * @Date: 2026/06/04
 */
@Getter
@ToString
public enum PaymentStatusEnum {

    INIT("I", "待支付"),
    SUCCESS("S", "已支付"),
    FAIL("F", "支付失败"),
    REFUNDED("R", "已退款");

    private String code;
    private String desc;

    PaymentStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PaymentStatusEnum fromCode(String code, String desc) {
        for (PaymentStatusEnum type : PaymentStatusEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            } else if (type.getDesc().equals(desc)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}
