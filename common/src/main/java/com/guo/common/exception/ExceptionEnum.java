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
    MEMBER_NOT_EXIST(HttpStatus.NOT_FOUND, "用户不存在，请使用验证码登录注册"),
    MEMBER_EXIST(HttpStatus.WARN, "用户已存在"),
    MEMBER_PASSWORD_ERROR(HttpStatus.UNAUTHORIZED, "密码错误"),
    MEMBER_LOGIN_ERROR(HttpStatus.UNAUTHORIZED, "用户未登录"),
    MEMBER_LOGIN_EXPIRED(HttpStatus.UNAUTHORIZED, "用户登录已过期"),
    MEMBER_MOBILE_EXIST(HttpStatus.CONFLICT, "手机号已注册"),
    MEMBER_MOBILE_NOT_EXIST(HttpStatus.BAD_REQUEST, "手机号未注册"),
    MEMBER_CODE_ERROR(HttpStatus.CONFLICT, "验证码错误"),
    MEMBER_CODE_EXCEOTION(HttpStatus.ERROR, "验证码生成错误"),
    MEMBER_CODE_NOT_ERROR(HttpStatus.BAD_REQUEST, "请先获取验证码"),
    NOT_AUTHORIZATION (HttpStatus.UNAUTHORIZED, "未登录"),
    STATION_EXIST(HttpStatus.WARN, "车站已存在"),
    CARRIAGE_EXIST(HttpStatus.WARN, "同车次厢序已存在"),
    TRAIN_NAME_EXIST(HttpStatus.WARN, "同车次站名已存在"),
    TRAIN_INDEX_EXIST(HttpStatus.WARN, "同车次站序已存在"),
    TRAIN_CODE_EXIST(HttpStatus.WARN, "车次编号已存在"),
    TICKET_NOT_ENOUGH(HttpStatus.CONFLICT, "余票不足"),
    FAILURE_TO_SAVE_TICKET(HttpStatus.CONFLICT, "购票出现异常，请稍后再试"),
    CONFIRM_ORDER_LOCK_FAIL(HttpStatus.CONFLICT, "当前抢票人数多，请稍后再试"),
    CONFIRM_ORDER_FAIL_FLOW_EXCEPTION(HttpStatus.CONFLICT, "当前抢票人数多，请稍后再试"),
    CONFIRM_ORDER_SK_TOKEN_FAIL(HttpStatus.CONFLICT, "当前抢票人数过多，请五秒后重试"),
    MEMBER_CODE_EXPIRED(HttpStatus.CONFLICT, "验证码已过期"),
    CONFIRM_ORDER_EXIST(HttpStatus.CONFLICT, "订单已存在"),
    TRAIN_NOT_EXIST(HttpStatus.CONFLICT, "车次不存在"),
    TICKET_NOT_EXIST(HttpStatus.CONFLICT, "余票不存在"),
    TRAIN_TICKET_EXIST(HttpStatus.CONFLICT, "请勿重复购买"),
    MEMBER_TYPE_ERROR(HttpStatus.CONFLICT, "登录类型错误"),
    ORDER_NOT_EXIST(HttpStatus.NOT_FOUND, "订单不存在"),
    ORDER_STATUS_ERROR(HttpStatus.CONFLICT, "订单状态不允许此操作"),
    PAYMENT_NOT_EXIST(HttpStatus.NOT_FOUND, "支付记录不存在"),
    PAYMENT_ALREADY_DONE(HttpStatus.CONFLICT, "订单已支付"),
    REFUND_ALREADY_DONE(HttpStatus.CONFLICT, "订单已退款"),
    CHANGE_ALREADY_DONE(HttpStatus.CONFLICT, "订单已改签"),
    ORDER_MEMBER_ERROR(HttpStatus.CONFLICT, "订单用户与登录用户不一致"),
    ADMIN_ONLY(HttpStatus.UNAUTHORIZED, "该账号无管理后台访问权限");


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
