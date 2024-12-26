package com.guo.common.req;

import lombok.Getter;
import lombok.Setter;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/20
 */
@Setter
@Getter
public class UserDetailsReq {
    private Long id;
    private String mobile;
    // 可以添加更多用户相关的属性，如密码、角色等，根据需求来定
    private String password;
    private String token;

}

