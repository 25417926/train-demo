package com.guo.business.TypeEnum;


import lombok.Getter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/21
 */

@Getter
public enum TrainSeatTypeEnum {
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

    TrainSeatTypeEnum(String code, String desc, String type) {
        this.code = code;
        this.desc = desc;
        this.type = type;
    }

    public static TrainSeatTypeEnum fromCode(String code, String desc) {
        for (TrainSeatTypeEnum type : TrainSeatTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }else if(type.getDesc().equals(desc)){
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
    /**
     * 根据车箱的座位类型，筛选出所有的列，比如车箱类型是一等座，则筛选出columnList={ACDF}
     */
    public static List<TrainSeatTypeEnum> getColsByType(String seatType) {
        List<TrainSeatTypeEnum> colList = new ArrayList<>();
        EnumSet<TrainSeatTypeEnum> seatColEnums = EnumSet.allOf(TrainSeatTypeEnum.class);
        for (TrainSeatTypeEnum anEnum : seatColEnums) {
            if (seatType.equals(anEnum.getType())) {
                colList.add(anEnum);
            }
        }
        return colList;
    }

}
