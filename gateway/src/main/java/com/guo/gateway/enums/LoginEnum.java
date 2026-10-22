package com.guo.gateway.enums;


import com.guo.gateway.util.HttpStatus;

public enum LoginEnum {
    //登录所需enum
    LOGIN_NOT_SUCCESS(HttpStatus.BAD_REQUEST, "请先登录"),
    LOGIN_SUCCESS(HttpStatus.SUCCESS, "登录成功"),
    LOGIN_FAIL(HttpStatus.BAD_REQUEST, "登录失败"),
    LOGIN_EXPIRED(HttpStatus.UNAUTHORIZED, "登录过期"),
    LOGIN_ERROR(HttpStatus.BAD_REQUEST, "登录错误"),
    LOGIN_NOT_EXIST(HttpStatus.FORBIDDEN, "用户不存在"),
    LOGIN_PASSWORD_ERROR(HttpStatus.BAD_REQUEST, "密码错误"),
    LOGIN_CODE_ERROR(HttpStatus.BAD_REQUEST, "验证码错误"),
    LOGIN_CODE_NOT_ERROR(HttpStatus.BAD_REQUEST, "请先获取验证码"),
    LOGIN_EXCEPTION(HttpStatus.FORBIDDEN, "登录异常"),
    LOGIN_PERMISSION_ERROR(HttpStatus.FORBIDDEN, "权限不足"),
    LOGIN_SYSTEM_BUSY(HttpStatus.ERROR, "系统繁忙，请稍后再试"),
    ;
    private String msg;
    private Integer code;

    LoginEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    public static LoginEnum fromCode(Integer code, String msg) {
        for (LoginEnum loginEnum : LoginEnum.values()) {
            if (loginEnum.getCode() == code) {
                return loginEnum;
            }
        }
        return null;
    }
    public Integer getCode() {
        return code;
    }
    public String getMsg() {
        return msg;
    }
}
