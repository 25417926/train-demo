package com.guo.business.TypeEnum;


import lombok.Getter;

import java.math.BigDecimal;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/21
 */

@Getter
public enum TrainCarriageTypeEnum {
    YDZ("1","一等座", new BigDecimal("0.4")),
    EDZ("2","二等座", new BigDecimal("0.3")),
    RW("3","软卧", new BigDecimal("0.6")),
    YW("4","硬卧", new BigDecimal("0.5"));
        private  String code;
        private  String desc;
    /**
     * 基础票价 N元/km 即0.4为0.4元/km
     */
    private  BigDecimal price;


    TrainCarriageTypeEnum(String code, String desc, BigDecimal price) {
        this.code = code;
        this.desc = desc;
    }
    public static TrainCarriageTypeEnum fromCode(String code, String desc) {
        for (TrainCarriageTypeEnum type : TrainCarriageTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }else if(type.getDesc().equals(desc)){
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}
