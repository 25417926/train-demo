package com.guo.member.mapper;

import com.guo.member.domain.Ticket;
import com.guo.member.domain.TicketExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TicketMapper {
    long countByExample(TicketExample example);

    int deleteByExample(TicketExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Ticket record);

    int insertSelective(Ticket record);

    /**
     * I10 幂等写票：命中唯一键 (order_id, passenger_id) 时忽略，返回 0
     */
    int insertIgnore(Ticket record);

    /**
     * I10 按订单精确删除本单车票
     */
    int deleteByOrderId(@Param("orderId") Long orderId);

    List<Ticket> selectByExample(TicketExample example);

    List<Ticket> selectByOrderId(@Param("orderId") Long orderId);

    Ticket selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Ticket record, @Param("example") TicketExample example);

    int updateByExample(@Param("record") Ticket record, @Param("example") TicketExample example);

    int updateByPrimaryKeySelective(Ticket record);

    int updateByPrimaryKey(Ticket record);
}