package com.guo.business.mapper;

import com.guo.business.domain.Sk_Token;
import com.guo.business.domain.Sk_TokenExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface Sk_TokenMapper {
    long countByExample(Sk_TokenExample example);

    int deleteByExample(Sk_TokenExample example);

    int deleteByPrimaryKey(Long id);

    int insert(Sk_Token record);

    int insertSelective(Sk_Token record);

    List<Sk_Token> selectByExample(Sk_TokenExample example);

    Sk_Token selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") Sk_Token record, @Param("example") Sk_TokenExample example);

    int updateByExample(@Param("record") Sk_Token record, @Param("example") Sk_TokenExample example);

    int updateByPrimaryKeySelective(Sk_Token record);

    int updateByPrimaryKey(Sk_Token record);
}