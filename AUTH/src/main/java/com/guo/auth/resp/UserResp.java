package com.guo.auth.resp;

import cn.dev33.satoken.stp.SaTokenInfo;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class UserResp {

    private Long id;
    private SaTokenInfo saTokenInfo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SaTokenInfo getSaTokenInfo() {
        return saTokenInfo;
    }

    public void setSaTokenInfo(SaTokenInfo saTokenInfo) {
        this.saTokenInfo = saTokenInfo;
    }

    @Override
    public String toString() {
        return "UserResp{" +
                "id=" + id +
                ", saTokenInfo=" + saTokenInfo +
                '}';
    }
}
