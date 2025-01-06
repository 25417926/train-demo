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
    MEMBER_MOBILE_NOT_EXIST(HttpStatus.BAD_REQUEST, "手机号未注册"),
    MEMBER_CODE_ERROR(HttpStatus.CONFLICT, "验证码错误"),
    MEMBER_CODE_NOT_ERROR(HttpStatus.BAD_REQUEST, "请先获取验证码"),
    NOT_AUTHORIZATION (HttpStatus.UNAUTHORIZED, "未登录"),
    STATION_EXIST(HttpStatus.WARN, "车站已存在"),
    CARRIAGE_EXIST(HttpStatus.WARN, "同车次厢序已存在"),
    TRAIN_NAME_EXIST(HttpStatus.WARN, "同车次站名已存在"),
    TRAIN_INDEX_EXIST(HttpStatus.WARN, "同车次站序已存在"),
    TRAIN_CODE_EXIST(HttpStatus.WARN, "车次编号已存在"),
    TICKET_NOT_ENOUGH(HttpStatus.CONFLICT, "余票不足");

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
