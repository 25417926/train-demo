package com.guo.business.mapper;

import com.guo.business.domain.Daily_Train;
import com.guo.business.domain.Daily_TrainExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface Daily_TrainMapper {
    long countByExample(Daily_TrainExample example);

    int deleteByExample(Daily_TrainExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Daily_Train record);

    int insertSelective(Daily_Train record);

    List<Daily_Train> selectByExample(Daily_TrainExample example);

    Daily_Train selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Daily_Train record, @Param("example") Daily_TrainExample example);

    int updateByExample(@Param("record") Daily_Train record, @Param("example") Daily_TrainExample example);

    int updateByPrimaryKeySelective(Daily_Train record);

    int updateByPrimaryKey(Daily_Train record);
}