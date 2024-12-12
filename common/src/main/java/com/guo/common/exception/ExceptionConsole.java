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
public class ExceptionConsole extends RuntimeException{
    private ExceptionEnum exceptionEnum;

    public ExceptionConsole(ExceptionEnum exceptionEnum) {
        this.exceptionEnum = exceptionEnum;
    }

    /**
     * 重写异常打印
     * 不写入堆栈信息，提高系统的性能
     */
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
