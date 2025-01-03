package com.guo.business.mapper;

import com.guo.business.domain.Daily_Train_Carriage;
import com.guo.business.domain.Daily_Train_CarriageExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface Daily_Train_CarriageMapper {
    long countByExample(Daily_Train_CarriageExample example);

    int deleteByExample(Daily_Train_CarriageExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Daily_Train_Carriage record);

    int insertSelective(Daily_Train_Carriage record);

    List<Daily_Train_Carriage> selectByExample(Daily_Train_CarriageExample example);

    Daily_Train_Carriage selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Daily_Train_Carriage record, @Param("example") Daily_Train_CarriageExample example);

    int updateByExample(@Param("record") Daily_Train_Carriage record, @Param("example") Daily_Train_CarriageExample example);

    int updateByPrimaryKeySelective(Daily_Train_Carriage record);

    int updateByPrimaryKey(Daily_Train_Carriage record);
}