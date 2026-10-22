package com.guo.business.TypeEnum;

import lombok.Getter;
import lombok.ToString;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/5
 */
@Getter
@ToString
public enum ConfirmOrderStatusEnum {

    INIT("I","初始"),
    PENDING("P","处理中"),
    SUCCESS("S","成功"),
    FAIL("F","失败"),
    EMPTY("E","无票"),
    CANCEL("C","取消"),
    PAID("Y","已支付"),
    WAITLIST_PAY("H","候补待支付"),
    REFUNDING("G","退款中"),
    REFUNDED("R","已退款"),
    CHANGED("X","已改签"),
    CLOSED("D","已关闭");

    private String code;
    private String desc;

    ConfirmOrderStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static ConfirmOrderStatusEnum fromCode(String code, String desc) {
        for (ConfirmOrderStatusEnum type : ConfirmOrderStatusEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }else if(type.getDesc().equals(desc)){
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}
