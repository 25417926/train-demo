package com.guo.auth.enums;

public enum RoleEnums {
    /**
     * 角色枚举类
     */

    admin(1L,"admin"),
    user(2L,"user"),
    ;
    private Long code;
    private String name;

    RoleEnums(Long code, String name) {
        this.code = code;
        this.name = name;
    }
    public Long getCode() {
        return code;
    }
    public String getName() {
        return name;
    }
    public static RoleEnums getRoleEnumsByCode(Long code){
        for (RoleEnums roleEnums : RoleEnums.values()) {
            if(roleEnums.getCode().equals(code)){
                return roleEnums;
            }
        }
        return null;
    }
}
