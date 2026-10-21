package com.guo.business.mapper.cust;

import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/6
 */
public interface CUST_Daily_Train_TicketMapper {
    void updateCountBySell(
            @Param("date") Date date,
            @Param("trainCode") String trainCode,
            @Param("seatTypeCode") String seatTypeCode,
            @Param("minStartIndex") Integer minStartIndex,
            @Param("maxStartIndex") Integer maxStartIndex,
            @Param("minEndIndex") Integer minEndIndex,
            @Param("maxEndIndex") Integer maxEndIndex
    );

    /**
     * 退票场景：在受影响的区间范围内余票数 +1
     */
    void increaseCountBySell(
            @Param("date") Date date,
            @Param("trainCode") String trainCode,
            @Param("seatTypeCode") String seatTypeCode,
            @Param("minStartIndex") Integer minStartIndex,
            @Param("maxStartIndex") Integer maxStartIndex,
            @Param("minEndIndex") Integer minEndIndex,
            @Param("maxEndIndex") Integer maxEndIndex
    );
}
