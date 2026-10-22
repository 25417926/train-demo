package com.guo.auth.req;

import javax.validation.constraints.NotBlank;

public class UserReq {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    private String password;

    private String code;

    /**
     * 登录入口标识：
     *   member（默认）= 会员端，用户不存在时验证码登录会自动注册，分配 user 角色
     *   admin           = 管理后台，禁止自动注册，且要求账户具备 admin 角色
     */
    private String source;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return "UserReq{" +
                "email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", code='" + code + '\'' +
                ", source='" + source + '\'' +
                '}';
    }
}
