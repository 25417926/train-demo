package com.guo.auth.mapper;

import com.guo.auth.domain.Permission;

import java.util.List;

public interface PermissionDoMapper {

    /**
     * 查询 APP 端所有被启用的权限
     *
     * @return
     */
    List<Permission> selectAppEnabledList();
}
