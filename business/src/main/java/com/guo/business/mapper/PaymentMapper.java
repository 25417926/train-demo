package com.guo.business.mapper;

import com.guo.business.domain.Payment;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface PaymentMapper {

    int insert(Payment record);

    int updateByPrimaryKeySelective(Payment record);

    Payment selectByPrimaryKey(Long id);

    Payment selectByOrderId(@Param("orderId") Long orderId);

    List<Payment> selectByMember(@Param("memberId") Long memberId,
                                 @Param("payStatus") String payStatus,
                                 @Param("startTime") Date startTime,
                                 @Param("endTime") Date endTime);

    List<Payment> selectAll();

    long countAll();
}
