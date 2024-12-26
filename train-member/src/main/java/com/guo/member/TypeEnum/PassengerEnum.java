package com.guo.member.TypeEnum;


import lombok.Getter;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/21
 */

@Getter
public enum PassengerEnum {
        ADULT("1", "成人"),
        STUDENT("2", "学生"),
        CHILDREN("3", "儿童"),
        INFANT("4", "残疾军人");
        private  String code;
        private  String desc;

    PassengerEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static PassengerEnum fromCode(String code) {
        for (PassengerEnum type : PassengerEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}
