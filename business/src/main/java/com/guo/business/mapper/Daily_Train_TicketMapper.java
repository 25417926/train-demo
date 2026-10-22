package com.guo.business.mapper;

import com.guo.business.domain.Daily_Train_Ticket;
import com.guo.business.domain.Daily_Train_TicketExample;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface Daily_Train_TicketMapper {
    long countByExample(Daily_Train_TicketExample example);

    int deleteByExample(Daily_Train_TicketExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Daily_Train_Ticket record);

    int insertSelective(Daily_Train_Ticket record);

    List<Daily_Train_Ticket> selectByExample(Daily_Train_TicketExample example);

    Daily_Train_Ticket selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Daily_Train_Ticket record, @Param("example") Daily_Train_TicketExample example);

    int updateByExample(@Param("record") Daily_Train_Ticket record, @Param("example") Daily_Train_TicketExample example);

    int updateByPrimaryKeySelective(Daily_Train_Ticket record);

    int updateByPrimaryKey(Daily_Train_Ticket record);

    @Select("select distinct train_code from daily_train_ticket where `date` = #{date} order by train_code")
    List<String> selectTrainCodesByDate(@Param("date") Date date);
}