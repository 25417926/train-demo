package com.guo.common.exception;

import com.guo.common.util.HttpStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 */

public enum ExceptionEnum {
    PARAM_NOT_VALID(HttpStatus.BAD_REQUEST, "参数校验异常"),
    MEMBER_NOT_EXIST(HttpStatus.NOT_FOUND, "用户不存在"),
    MEMBER_EXIST(HttpStatus.WARN, "用户已存在"),
    MEMBER_PASSWORD_ERROR(HttpStatus.UNAUTHORIZED, "密码错误"),
    MEMBER_LOGIN_ERROR(HttpStatus.UNAUTHORIZED, "用户未登录"),
    MEMBER_LOGIN_EXPIRED(HttpStatus.UNAUTHORIZED, "用户登录已过期"),
    MEMBER_MOBILE_EXIST(HttpStatus.CONFLICT, "手机号已注册"),
    ;

    @Getter
    @Setter
    private Integer code;

    @Getter
    @Setter
    private String message;
    ExceptionEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }


}
