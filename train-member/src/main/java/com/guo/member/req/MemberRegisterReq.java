package com.guo.member.req;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotBlank;


/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 */
@Getter
@Setter
@ToString
public class MemberRegisterReq {
    @NotBlank(message = "手机号不能为空")
    private String mobile;

}
