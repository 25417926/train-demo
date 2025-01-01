package com.guo.business.TypeEnum;


import lombok.Getter;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/21
 */

@Getter
public enum TarinSeatTypeEnum {
    YDZ_A("A", "A", "1"),
    YDZ_C("C", "C", "1"),
    YDZ_D("D", "D", "1"),
    YDZ_F("F", "F", "1"),
    EDZ_A("A", "A", "2"),
    EDZ_B("B", "B", "2"),
    EDZ_C("C", "C", "2"),
    EDZ_D("D", "D", "2"),
    EDZ_F("F", "F", "2");

    private String code;

    private String desc;

    /**
     * 对应SeatTypeEnum.code
     */
    private String type;

    TarinSeatTypeEnum(String code, String desc, String type) {
        this.code = code;
        this.desc = desc;
        this.type = type;
    }

    public static TarinSeatTypeEnum fromCode(String code, String desc) {
        for (TarinSeatTypeEnum type : TarinSeatTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }else if(type.getDesc().equals(desc)){
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}
