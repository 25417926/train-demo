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

    /**
     * 返回此 loginId 拥有的权限列表
     * @param loginId
     * @param loginType
     * @return
     */
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

    /**
     * 返回此 loginId 拥有的角色列表
     * @param loginId
     * @param loginType
     * @return
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 从 redis 读取 user:roles:{loginId}，AUTH 写入的是 List<String> 序列化后的 JSON 数组
        String useRolesValue = getString(loginId);
        List<String> roleList = new ArrayList<>();
        if (StrUtil.isBlank(useRolesValue)) {
            return roleList;
        }
        try {
            // 优先按 JSON 数组解析
            roleList.addAll(JSONUtil.toList(JSONUtil.parseArray(useRolesValue), String.class));
        } catch (Exception e) {
            // 兼容旧数据：写的是单个角色字符串
            roleList.add(useRolesValue);
        }
        return roleList;
    }

    //从redis中拿取对应的角色数据
    private  String getString(Object loginId) {
        String key = RedisKeyConstants.buildUserRoleKey(Long.valueOf(loginId.toString()));
        if(StrUtil.isBlank(key)){
            throw new RuntimeException("loginId为空");
        }
        Object cacheValue = redisTemplate.opsForValue().get(key);
        if (cacheValue == null) {
            return null;
        }
        // value 序列化器是 Jackson，AUTH 写入的是 List<String>，反序列化回来是 ArrayList
        // 这里统一转成 JSON 字符串，让 getRoleList 用 JSONUtil 解析
        if (cacheValue instanceof String) {
            return (String) cacheValue;
        }
        return JSONUtil.toJsonStr(cacheValue);
    }
}