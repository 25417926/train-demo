package com.guo.auth.mapper;

import com.guo.auth.domain.RolePermissionRel;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RolePermissionRelDoMapper {
     /**
     * 根据角色 ID 集合批量查询
     *
     * @param roleIds
     * @return
     */
    List<RolePermissionRel> selectByRoleIds(@Param("roleIds") List<Long> roleIds);
}
