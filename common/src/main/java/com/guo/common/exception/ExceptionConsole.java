package com.guo.common.exception;

import lombok.Getter;
import lombok.Setter;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 */
@Getter
@Setter
public class MobileException extends RuntimeException{
    private ExceptionEnum exceptionEnum;

    public MobileException( ExceptionEnum exceptionEnum) {
        this.exceptionEnum = exceptionEnum;
    }
}
