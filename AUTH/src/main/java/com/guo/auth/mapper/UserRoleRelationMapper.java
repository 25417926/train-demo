package com.guo.auth.mapper;

import com.guo.auth.domain.Role;

public interface UserRoleRelationMapper {

    //拿取用户表的id，查询角色用户关联表中的对应的角色id，再查询角色id拿到角色的name,还需要判断是否逻辑删除
    String selectRoleByUserId(Long userId);
}
