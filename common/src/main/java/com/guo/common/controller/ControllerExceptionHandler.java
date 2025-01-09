package com.guo.common.controller;

import cn.hutool.core.util.StrUtil;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import io.seata.core.context.RootContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 * 统一异常处理、数据预处理
 */
@ControllerAdvice
public class ControllerExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ControllerExceptionHandler.class);

    /**
     * 所有异常统一处理
     * @param e
     * @return
     */
    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public CommonResp exceptionHandler(Exception e) throws Exception {
         LOG.info("seata全局事务ID: {}", RootContext.getXID());
         // 如果是在一次全局事务里出异常了，就不要包装返回值，将异常抛给调用方，让调用方回滚事务
         if (StrUtil.isNotBlank(RootContext.getXID())) {
             throw e;
         }
        LOG.error("系统异常：{}", e);
        CommonResp commonResp1 = new CommonResp(false,"系统出现异常，请联系管理员", HttpStatus.ERROR);
        return commonResp1;
    }

    /**
     * 业务异常统一处理
     * @param e
     * @return
     */
    @ExceptionHandler(value = ExceptionConsole.class)
    @ResponseBody
    public CommonResp exceptionHandler(ExceptionConsole e)  {
        // LOG.info("seata全局事务ID: {}", RootContext.getXID());
        // // 如果是在一次全局事务里出异常了，就不要包装返回值，将异常抛给调用方，让调用方回滚事务
        // if (StrUtil.isNotBlank(RootContext.getXID())) {
        //     throw e;
        // }
        LOG.error("业务异常：{}",e.getExceptionEnum().getMessage(),e);
        CommonResp commonResp1 = new CommonResp(false,e.getExceptionEnum().getMessage(), e.getExceptionEnum().getCode());
        return commonResp1;
    }
    /**
     * 校验异常统一处理
     * @param e
     * @return
     */
    @ExceptionHandler(value = BindException.class)
    @ResponseBody
    public CommonResp exceptionHandler(BindException e)  {
        // LOG.info("seata全局事务ID: {}", RootContext.getXID());
        // // 如果是在一次全局事务里出异常了，就不要包装返回值，将异常抛给调用方，让调用方回滚事务
        // if (StrUtil.isNotBlank(RootContext.getXID())) {
        //     throw e;
        // }
        String message = e.getAllErrors().get(0).getDefaultMessage();
        LOG.error("校验异常：{}",message);
        CommonResp commonResp1 = new CommonResp(false,message   , HttpStatus.BAD_REQUEST);
        return commonResp1;
    }
}
