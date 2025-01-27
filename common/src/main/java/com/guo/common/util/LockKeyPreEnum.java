package com.guo.common.util;

import lombok.Getter;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/12
 */
@Getter
public enum LockKeyPreEnum {

    //订单锁
     ORDER_LOCK("order_lock_", "订单锁"),
    //令牌锁
    SK_TOKEN_LOCK("token_lock_","令牌锁"),
     //令牌数
     SK_TOKEN_COUNT("token_count_","令牌数"),;



    private  String code;
    private  String desc;

    LockKeyPreEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static LockKeyPreEnum fromCode(String code,String desc) {
        for (LockKeyPreEnum type : LockKeyPreEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }else if(type.getDesc().equals(desc)){
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}
