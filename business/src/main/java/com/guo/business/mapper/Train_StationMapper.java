package com.guo.business.mapper;

import com.guo.business.domain.Train_Station;
import com.guo.business.domain.Train_StationExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface Train_StationMapper {
    long countByExample(Train_StationExample example);

    int deleteByExample(Train_StationExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Train_Station record);

    int insertSelective(Train_Station record);

    List<Train_Station> selectByExample(Train_StationExample example);

    Train_Station selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Train_Station record, @Param("example") Train_StationExample example);

    int updateByExample(@Param("record") Train_Station record, @Param("example") Train_StationExample example);

    int updateByPrimaryKeySelective(Train_Station record);

    int updateByPrimaryKey(Train_Station record);
}