package com.guo.business.mapper.cust;

import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/6
 */
public interface CUST_Sk_TokenMapper {
    int decrease(
            @Param("date") Date date,
            @Param("trainCode") String trainCode,
            @Param("decreaseCount") int decreaseCount
    );
}
