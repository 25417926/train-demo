package com.guo.member.req;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/14
 */
@Getter
@Setter
@ToString
public class MemberLoginReq {
    private Long id;
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3|4|5|6|7|8|9][0-9]{9}$", message = "手机号格式错误")
    private String mobile;
    @NotBlank(message = "验证码不能为空")
    private String code;
    @NotBlank(message = "密码不能为空")
    private String password;
    private String token;
    //验证码token
    private String tokenCode;
}
