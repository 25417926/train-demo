package com.guo.auth.constant;

public class RedisKeyConstants {
     /**
     * 角色对应的权限集合 KEY 前缀
     */
    private static final String ROLE_PERMISSIONS_KEY_PREFIX = "role:permissions:";


    /**
     * 构建角色对应的权限集合 KEY
     * @param RoleUser
     * @return
     */
    public static String buildRolePermissionsKey(String RoleUser) {
        return ROLE_PERMISSIONS_KEY_PREFIX + RoleUser;
    }
    /**
     * 账户对应的角色集合 KEY 前缀
     * @param UserId
     * @return
     */
    public static String buildUserRolesKey(Long UserId) {
        return "user:roles:" + UserId;
    }
}
