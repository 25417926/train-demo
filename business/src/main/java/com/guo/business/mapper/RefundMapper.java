package com.guo.business.mapper;

import com.guo.business.domain.Refund;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RefundMapper {

    int insert(Refund record);

    int updateByPrimaryKeySelective(Refund record);

    Refund selectByPrimaryKey(Long id);

    List<Refund> selectByOrderId(@Param("orderId") Long orderId);

    List<Refund> selectByMember(@Param("memberId") Long memberId);

    List<Refund> selectAll();

    long countAll();
}
