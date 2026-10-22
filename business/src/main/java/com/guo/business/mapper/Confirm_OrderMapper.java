package com.guo.business.mapper;

import com.guo.business.domain.Confirm_Order;
import com.guo.business.domain.Confirm_OrderExample;
import com.guo.business.resp.UnpaidOrderResp;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Update("update confirm_order set status = #{status}, ticket_status = #{ticketStatus}, update_time = #{updateTime} where id = #{id}")
    int updateStatusAndTicketStatus(@Param("id") Long id,
                                    @Param("status") String status,
                                    @Param("ticketStatus") String ticketStatus,
                                    @Param("updateTime") Date updateTime);

    /**
     * I07 状态机 CAS：只有当前状态仍为 fromStatus 时才更新，返回影响行数用于判断并发冲突。
     */
    @Update("update confirm_order set status = #{toStatus}, update_time = #{updateTime} " +
            "where id = #{id} and status = #{fromStatus}")
    int updateStatusCas(@Param("id") Long id,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("updateTime") Date updateTime);

    @Select("select ticket_status from confirm_order where id = #{id}")
    String selectTicketStatus(@Param("id") Long id);

    @Select("select id, `date`, train_code, `start`, `end`, expire_at, create_time from confirm_order " +
            "where member_id = #{memberId} and status = 'S' and ticket_status = 'N' " +
            "and expire_at is not null and expire_at > now() order by expire_at asc")
    @Results(id = "unpaidOrderResult", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "date", property = "date"),
            @Result(column = "train_code", property = "trainCode"),
            @Result(column = "start", property = "start"),
            @Result(column = "end", property = "end"),
            @Result(column = "expire_at", property = "expireAt"),
            @Result(column = "create_time", property = "createTime")
    })
    List<UnpaidOrderResp> selectUnpaidByMember(@Param("memberId") Long memberId);

    @Update("update confirm_order set status = #{status}, ticket_status = #{ticketStatus}, " +
            "expire_at = #{expireAt}, update_time = #{updateTime} where id = #{id}")
    int updateLockResult(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("ticketStatus") String ticketStatus,
                         @Param("expireAt") Date expireAt,
                         @Param("updateTime") Date updateTime);

    int updateByPrimaryKeyWithBLOBs(Confirm_Order record);

    int updateByPrimaryKey(Confirm_Order record);
}