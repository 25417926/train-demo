package com.guo.auth.mapper;

import com.guo.auth.domain.Role;

import java.util.List;

public interface RoleDoMapper {

    /**
     * 查询所有被启用的角色
     *
     * @return
     */
    List<Role> selectEnabledList();

}
