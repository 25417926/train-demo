package com.guo.auth.running;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.alibaba.nacos.shaded.com.google.common.collect.Maps;
import com.guo.auth.constant.RedisKeyConstants;
import com.guo.auth.domain.Permission;
import com.guo.auth.domain.Role;
import com.guo.auth.domain.RolePermissionRel;
import com.guo.auth.enums.RoleEnums;
import com.guo.auth.mapper.PermissionDoMapper;
import com.guo.auth.mapper.RoleDoMapper;
import com.guo.auth.mapper.RolePermissionRelDoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class PushRolePermissions2RedisRunner implements ApplicationRunner {
    Logger log = LoggerFactory.getLogger(PushRolePermissions2RedisRunner.class);

    @Resource
    private RoleDoMapper roleDoMapper;
    @Resource
    private RolePermissionRelDoMapper rolePermissionRelDoMapper;
    @Resource
    private PermissionDoMapper permissionDoMapper;
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    // 权限同步标记 Key
    private static final String PUSH_PERMISSIONS_KEY = "push.permission.flag";

    @Override
    public void run(ApplicationArguments args) {
        log.error("==> 服务启动，开始同步角色权限数据到 Redis 中...");
        try {
            boolean flag =redisTemplate.opsForValue().setIfAbsent(PUSH_PERMISSIONS_KEY,"1",1, TimeUnit.DAYS);

            // 如果Redis中已存在同步标记，则跳过同步操作
            if(!flag){
                log.info("==> Redis中已存在同步标记，跳过同步操作...");
                return;
            }

            // 查询所有启用的角色列表，返回一个Role对象的列表
            List<Role> roles = roleDoMapper.selectEnabledList();

            // 如果角色列表不为空，则继续执行后续逻辑
            if (CollUtil.isNotEmpty(roles)) {
                // 提取所有角色的ID，形成一个Long类型的列表，用于后续查询
                List<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());

                // 根据角色ID列表，批量查询角色与权限的关系数据，返回一个RolePermissionRel对象的列表
                List<RolePermissionRel> rolePermissionRels = rolePermissionRelDoMapper.selectByRoleIds(roleIds);

                // 将角色与权限的关系按照角色ID进行分组，生成一个Map
                // Key为角色ID，Value为该角色对应的权限ID列表
                Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionRels.stream().collect(
                        Collectors.groupingBy(
                                RolePermissionRel::getRoleId, // 分组依据为角色ID
                                Collectors.mapping(
                                        RolePermissionRel::getPermissionId, // 映射操作，提取每个关系中的权限ID
                                        Collectors.toList() // 将权限ID收集到列表中
                                )
                        )
                );

                // 查询APP端所有被启用的权限列表，返回一个Permission对象的列表
                List<Permission> permissions = permissionDoMapper.selectAppEnabledList();

                // 构建一个权限ID到权限对象的映射表，方便后续快速查找权限对象
                Map<Long, Permission> permissionIdPermissionMap = permissions.stream().collect(
                        Collectors.toMap(Permission::getId, permission -> permission)
                );

                // 创建一个Map，用于存储角色ID与权限对象列表的对应关系
                Map<Long, List<String>> roleIdPermissionsMap = Maps.newHashMap();

                // 遍历所有角色，构建角色ID与权限对象列表的映射关系
                roles.forEach(role -> {
                    Long roleId = role.getId(); // 获取当前角色的ID
                    List<Long> permissionIds = roleIdPermissionIdsMap.get(roleId); // 获取该角色对应的权限ID列表

                    // 如果权限ID列表不为空，则继续处理
                    if (CollUtil.isNotEmpty(permissionIds)) {
                        List<String> permissionList = Lists.newArrayList(); // 创建一个权限key存储列表
                        for (Long permissionId : permissionIds) {
                            Permission permission = permissionIdPermissionMap.get(permissionId); // 根据权限ID获取权限对象
                            if (permission != null) {
                                permissionList.add(permission.getPermissionKey()); // 将有效的权限key对象添加到列表中
                            }
                        }
                        // 将角色ID与权限对象列表的映射关系存入Map
                        roleIdPermissionsMap.put(roleId, permissionList);
                    }
                });

                // 将角色ID与权限对象列表的映射关系同步到Redis中
                // 便于后续系统（如网关）能够快速查询角色的权限信息
                roleIdPermissionsMap.forEach((roleId, permissionList) -> {
                    String key = RedisKeyConstants.buildRolePermissionsKey(RoleEnums.getRoleEnumsByCode(roleId).getName()); // 构建Redis的Key
                    redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(permissionList)); // 将权限列表序列化后存入Redis
                });
            }

            // 记录日志：成功同步角色权限数据到Redis中
            log.info("==> 服务启动，成功同步角色权限数据到 Redis 中...");
        } catch (Exception e) {
            // 捕获异常并记录错误日志，确保程序不会因异常中断
            log.error("==> 同步角色权限数据到 Redis 中失败: ", e);
        }


    }
}
