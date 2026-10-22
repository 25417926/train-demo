package com.guo.business.TypeEnum;

import lombok.Getter;

/**
 * I12 订单关闭原因，code 与 inventory_release_record.release_type 一致。
 */
@Getter
public enum OrderCloseReasonEnum {

    /** 超时未支付 */
    TIMEOUT("T", "超时关闭"),
    /** 用户主动取消 */
    CANCEL("C", "用户取消"),
    /** 退款成功释放 */
    REFUND("R", "退款释放"),
    /** 改签释放 */
    CHANGE("G", "改签释放"),
    /** 候补补位失败 */
    MATCH_FAIL("M", "补位失败");

    private final String code;
    private final String desc;

    OrderCloseReasonEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderCloseReasonEnum fromCode(String code) {
        for (OrderCloseReasonEnum reason : values()) {
            if (reason.getCode().equals(code)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("未知关闭原因：" + code);
    }
}
