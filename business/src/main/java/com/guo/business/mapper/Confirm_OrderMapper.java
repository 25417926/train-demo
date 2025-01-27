package com.guo.business.mapper;

import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Confirm_OrderExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface Confirm_OrderMapper {
    long countByExample(Confirm_OrderExample example);

    int deleteByExample(Confirm_OrderExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Confirm_Order record);

    int insertSelective(Confirm_Order record);

    List<Confirm_Order> selectByExampleWithBLOBs(Confirm_OrderExample example);

    List<Confirm_Order> selectByExample(Confirm_OrderExample example);

    Confirm_Order selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Confirm_Order record, @Param("example") Confirm_OrderExample example);

    int updateByExampleWithBLOBs(@Param("record") Confirm_Order record, @Param("example") Confirm_OrderExample example);

    int updateByExample(@Param("record") Confirm_Order record, @Param("example") Confirm_OrderExample example);

    int updateByPrimaryKeySelective(Confirm_Order record);

    int updateByPrimaryKeyWithBLOBs(Confirm_Order record);

    int updateByPrimaryKey(Confirm_Order record);
}