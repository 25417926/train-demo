package com.guo.business.mapper;

import com.guo.business.domain.Daily_Train_Seat;
import com.guo.business.domain.Daily_Train_SeatExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface Daily_Train_SeatMapper {
    long countByExample(Daily_Train_SeatExample example);

    int deleteByExample(Daily_Train_SeatExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Daily_Train_Seat record);

    int insertSelective(Daily_Train_Seat record);

    List<Daily_Train_Seat> selectByExample(Daily_Train_SeatExample example);

    Daily_Train_Seat selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Daily_Train_Seat record, @Param("example") Daily_Train_SeatExample example);

    int updateByExample(@Param("record") Daily_Train_Seat record, @Param("example") Daily_Train_SeatExample example);

    int updateByPrimaryKeySelective(Daily_Train_Seat record);

    int updateByPrimaryKey(Daily_Train_Seat record);
}