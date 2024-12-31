package com.guo.business.mapper;

import com.guo.business.domain.train;
import com.guo.business.domain.trainExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface trainMapper {
    long countByExample(trainExample example);

    int deleteByExample(trainExample example);

    int deleteByPrimaryKey(Long id);

    int insert(train record);

    int insertSelective(train record);

    List<train> selectByExample(trainExample example);

    train selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") train record, @Param("example") trainExample example);

    int updateByExample(@Param("record") train record, @Param("example") trainExample example);

    int updateByPrimaryKeySelective(train record);

    int updateByPrimaryKey(train record);
}