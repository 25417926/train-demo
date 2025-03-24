package com.guo.auth.mapper;

import com.guo.auth.domain.UserRoleRel;
import com.guo.auth.domain.UserRoleRelExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserRoleRelMapper {
    long countByExample(UserRoleRelExample example);

    int deleteByExample(UserRoleRelExample example);

    int deleteByPrimaryKey(Long id);

    int insert(UserRoleRel record);

    int insertSelective(UserRoleRel record);

    List<UserRoleRel> selectByExample(UserRoleRelExample example);

    UserRoleRel selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") UserRoleRel record, @Param("example") UserRoleRelExample example);

    int updateByExample(@Param("record") UserRoleRel record, @Param("example") UserRoleRelExample example);

    int updateByPrimaryKeySelective(UserRoleRel record);

    int updateByPrimaryKey(UserRoleRel record);
}