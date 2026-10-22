package com.guo.auth.mapper;

import com.guo.auth.domain.RolePermissionRel;
import com.guo.auth.domain.RolePermissionRelExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RolePermissionRelMapper {
    long countByExample(RolePermissionRelExample example);

    int deleteByExample(RolePermissionRelExample example);

    int deleteByPrimaryKey(Long id);

    int insert(RolePermissionRel record);

    int insertSelective(RolePermissionRel record);

    List<RolePermissionRel> selectByExample(RolePermissionRelExample example);

    RolePermissionRel selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") RolePermissionRel record, @Param("example") RolePermissionRelExample example);

    int updateByExample(@Param("record") RolePermissionRel record, @Param("example") RolePermissionRelExample example);

    int updateByPrimaryKeySelective(RolePermissionRel record);

    int updateByPrimaryKey(RolePermissionRel record);
}