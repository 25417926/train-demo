package com.guo.auth.resp;

import cn.dev33.satoken.stp.SaTokenInfo;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class UserResp {

    private String id;
    private String getSaToken;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSaToken() {
        return getSaToken;
    }

    public void setSaToken(String getSaToken) {
        this.getSaToken = getSaToken;
    }

    @Override
    public String toString() {
        return "UserResp{" +
                "id=" + id +
                ", saTokenInfo=" + getSaToken +
                '}';
    }
}
