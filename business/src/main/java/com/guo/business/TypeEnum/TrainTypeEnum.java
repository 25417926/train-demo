package com.guo.business.TypeEnum;


import lombok.Getter;

import java.math.BigDecimal;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/21
 */

@Getter
public enum TrainTypeEnum {
    G("G","高铁", new BigDecimal("1.2")),
    D("D","动车", new BigDecimal("1")),
    K("K","快速", new BigDecimal("0.8"));
        private  String code;
        private  String desc;
    /**
     * 票价比例 例如 1.2 则票价为 1.2 * 每公里票价 * 公里数
     */
    private  BigDecimal price;


    TrainTypeEnum(String code, String desc, BigDecimal price) {
        this.code = code;
        this.desc = desc;
        this.price = price;
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
