package com.guo.auth.mapper;

import java.util.List;

public interface UserRoleRelationMapper {

    /**
     * 查询某用户拥有的全部角色名（已过滤逻辑删除的角色）。
     * 一个用户可能同时拥有多个角色（例如 admin + user），所以返回 List。
     */
    List<String> selectRoleByUserId(Long userId);
}
