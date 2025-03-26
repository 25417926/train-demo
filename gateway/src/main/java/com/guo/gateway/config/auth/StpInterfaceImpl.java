package com.guo.gateway.config.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.guo.gateway.constant.RedisKeyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义权限验证接口扩展
 */
@Component
public class StpInterfaceImpl implements StpInterface {
    private Logger log = LoggerFactory.getLogger(StpInterfaceImpl.class);
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 返回此 loginId 拥有的权限列表

        // todo 从 redis 获取
        String useRolesValue = getString(loginId);

        //创建一个权限列表
        List<String> permissionList = new ArrayList<>();
        permissionList.add(useRolesValue);
        if(CollUtil.isNotEmpty(permissionList)){
            // 查询这些角色对应的权限
            // 构建 角色-权限 Redis Key 集合
            List<String> KeysList = permissionList
                    .stream()
                    .map(RedisKeyConstants::buildRolePermissionsKey)
                    .collect(Collectors.toList());

            // 通过 key 集合批量查询权限，提升查询性能。
            List<Object> rolePermissionsValues = redisTemplate.opsForValue().multiGet(KeysList);
            if(CollUtil.isEmpty(rolePermissionsValues)){
                throw new RuntimeException("角色对应的权限不存在");
            }
            // 将查询到的值转换为 List<String>
            List<String> PermissionsValues =rolePermissionsValues
                    .stream()
                    .map(obj -> obj!=null?obj.toString():null)
                    .collect(Collectors.toList());
            if(CollUtil.isEmpty(PermissionsValues)){
                throw new RuntimeException("角色对应的权限不存在");
            }
            PermissionsValues.forEach(item->{
                try{
                    log.info(item);
                    // 将 JSON 字符串["app:note:publish"]转换为 List<String> 权限集合
                    List<String> permissions = JSONUtil.toList(JSONUtil.parseArray(item), String.class);
                    permissionList.addAll(permissions);
                }catch (Exception e){
                    log.error("角色对应的权限不存在");
                }
            });
        }

        return permissionList;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 返回此 loginId 拥有的角色列表

        // todo 从 redis 获取
        String useRolesValue = getString(loginId);
        List<String> roleList = new ArrayList<>();
        roleList.add(useRolesValue);
        return roleList;
    }

    private  String getString(Object loginId) {
        String key = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
        if(StrUtil.isBlank(key)){
            throw new RuntimeException("loginId为空");
        }
        String useRolesValue = (String) redisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(useRolesValue)) {
            return null;
        }
        return useRolesValue;
    }
}