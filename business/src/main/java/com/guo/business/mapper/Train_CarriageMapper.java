package com.guo.business.mapper;

import com.guo.business.domain.Train_Carriage;
import com.guo.business.domain.Train_CarriageExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface Train_CarriageMapper {
    long countByExample(Train_CarriageExample example);

    int deleteByExample(Train_CarriageExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Train_Carriage record);

    int insertSelective(Train_Carriage record);

    List<Train_Carriage> selectByExample(Train_CarriageExample example);

    Train_Carriage selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Train_Carriage record, @Param("example") Train_CarriageExample example);

    int updateByExample(@Param("record") Train_Carriage record, @Param("example") Train_CarriageExample example);

    int updateByPrimaryKeySelective(Train_Carriage record);

    int updateByPrimaryKey(Train_Carriage record);
}