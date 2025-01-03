package com.guo.business.mapper;

import com.guo.business.domain.Daily_Train_Station;
import com.guo.business.domain.Daily_Train_StationExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface Daily_Train_StationMapper {
    long countByExample(Daily_Train_StationExample example);

    int deleteByExample(Daily_Train_StationExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Daily_Train_Station record);

    int insertSelective(Daily_Train_Station record);

    List<Daily_Train_Station> selectByExample(Daily_Train_StationExample example);

    Daily_Train_Station selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Daily_Train_Station record, @Param("example") Daily_Train_StationExample example);

    int updateByExample(@Param("record") Daily_Train_Station record, @Param("example") Daily_Train_StationExample example);

    int updateByPrimaryKeySelective(Daily_Train_Station record);

    int updateByPrimaryKey(Daily_Train_Station record);
}