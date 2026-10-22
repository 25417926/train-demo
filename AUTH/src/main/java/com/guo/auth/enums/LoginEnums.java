package com.guo.auth.enums;

import java.util.Objects;

public enum LoginEnums {
    PASSWORD_LOGIN(1),
    SENDCODE_LOGIN(2);

    private final Integer type;

    LoginEnums(Integer type) {
        this.type = type;
    }

    public Integer getType() {
        return type;
    }


    public static LoginEnums valueOf(Integer code) {
        for (LoginEnums loginTypeEnum : LoginEnums.values()) {
            if (Objects.equals(code, loginTypeEnum.getType())) {
                return loginTypeEnum;
            }
        }
        return null;
    }
}
