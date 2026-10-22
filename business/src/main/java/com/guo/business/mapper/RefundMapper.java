package com.guo.business.mapper;

import com.guo.business.domain.Refund;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

public interface RefundMapper {

    int insert(Refund record);

    int updateByPrimaryKeySelective(Refund record);

    Refund selectByPrimaryKey(Long id);

    /**
     * I13 按幂等键查询退款流水（REFUND:{orderId}）。
     */
    Refund selectByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    /**
     * I13 REFUNDING 预留：同幂等键重复申请时复位为处理中，允许失败后重试。
     */
    int insertOrResetRefunding(Refund record);

    @Update("update refund set refund_status = 'S', refund_time = #{refundTime}, update_time = #{updateTime}, "
            + "fail_reason = null where idempotent_key = #{idempotentKey} and refund_status = 'I'")
    int markSuccess(@Param("idempotentKey") String idempotentKey,
                    @Param("refundTime") Date refundTime,
                    @Param("updateTime") Date updateTime);

    @Update("update refund set refund_status = 'F', fail_reason = #{failReason}, update_time = #{updateTime} "
            + "where idempotent_key = #{idempotentKey} and refund_status = 'I'")
    int markFailed(@Param("idempotentKey") String idempotentKey,
                   @Param("failReason") String failReason,
                   @Param("updateTime") Date updateTime);

    List<Refund> selectByOrderId(@Param("orderId") Long orderId);

    List<Refund> selectByMember(@Param("memberId") Long memberId);

    List<Refund> selectAll();

    long countAll();
}
