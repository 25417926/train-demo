package com.guo.business.TypeEnum;

import lombok.Getter;

/**
 * 候补申请状态（waitlist_application.status）。
 */
@Getter
public enum WaitlistStatusEnum {

    WAITING("W", "排队中"),
    MATCHING("M", "匹配中"),
    MATCHED("D", "已匹配待支付"),
    EXPIRED("E", "已过期"),
    CANCELLED("C", "已取消"),
    FAILED("F", "匹配失败");

    private final String code;
    private final String desc;

    WaitlistStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static WaitlistStatusEnum fromCode(String code) {
        for (WaitlistStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    public static String descOf(String code) {
        WaitlistStatusEnum status = fromCode(code);
        return status == null ? null : status.desc;
    }
}
