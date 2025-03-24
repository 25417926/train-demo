package com.guo.auth.req;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class UserReq {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    private String password;

    private String code;



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




    @Override
    public String toString() {
        return "UserReq{" +
                "email='" + email + '\'' +
                ", password='" + password + '\'' +
                ", code='" + code + '\'' +

                '}';
    }
}
