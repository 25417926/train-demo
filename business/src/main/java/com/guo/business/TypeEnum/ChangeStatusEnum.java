package com.guo.business.TypeEnum;

import lombok.Getter;
import lombok.ToString;

/**
 * @Description 退改签状态枚举
 * @Author: 派
 * @Date: 2026/06/04
 */
@Getter
@ToString
public enum ChangeStatusEnum {

    INIT("I", "处理中"),
    SUCCESS("S", "成功"),
    FAIL("F", "失败");

    private String code;
    private String desc;

    ChangeStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ChangeStatusEnum fromCode(String code, String desc) {
        for (ChangeStatusEnum type : ChangeStatusEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            } else if (type.getDesc().equals(desc)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}
