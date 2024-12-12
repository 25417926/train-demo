package com.guo.common.controller;

import com.guo.common.exception.ExceptionConsole;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
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
    public CommonResp exceptionHandler(Exception e) {
        // LOG.info("seata全局事务ID: {}", RootContext.getXID());
        // // 如果是在一次全局事务里出异常了，就不要包装返回值，将异常抛给调用方，让调用方回滚事务
        // if (StrUtil.isNotBlank(RootContext.getXID())) {
        //     throw e;
        // }
        LOG.error(()->"系统异常：", e);
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
        LOG.error(()->"业务异常："+e.getExceptionEnum().getMessage(),e);
        CommonResp commonResp1 = new CommonResp(false,e.getExceptionEnum().getMessage(), e.getExceptionEnum().getCode());
        return commonResp1;
    }
}
