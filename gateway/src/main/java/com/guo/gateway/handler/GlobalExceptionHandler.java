package com.guo.gateway.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guo.gateway.enums.LoginEnum;
import com.guo.gateway.util.CommonResp;
import com.guo.gateway.util.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;

@Component

public class GlobalExceptionHandler implements ErrorWebExceptionHandler {
    Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 获取响应对象
        ServerHttpResponse response = exchange.getResponse();

        log.error("==> 全局异常捕获: ", ex);

        // 响参
        CommonResp<?> result;
        // 根据捕获的异常类型，设置不同的响应状态码和响应消息
        // Sa-Token 异常
        if (ex instanceof NotLoginException) {
            response.setStatusCode(org.springframework.http.HttpStatus.valueOf(HttpStatus.UNAUTHORIZED));
            result = new CommonResp(false, LoginEnum.LOGIN_NOT_SUCCESS.getMsg(), LoginEnum.LOGIN_NOT_SUCCESS.getCode());
        } else if (ex instanceof NotPermissionException) {
            // 权限认证失败时，设置 401 状态码
            response.setStatusCode(org.springframework.http.HttpStatus.valueOf(HttpStatus.UNAUTHORIZED));
            // 构建响应结果
            result = new CommonResp(false, LoginEnum.LOGIN_PERMISSION_ERROR.getMsg(), LoginEnum.LOGIN_PERMISSION_ERROR.getCode());
        } else { // 其他异常，则统一提示 “系统繁忙” 错误
            result = new CommonResp(false, LoginEnum.LOGIN_SYSTEM_BUSY.getMsg(), LoginEnum.LOGIN_SYSTEM_BUSY.getCode());
        }

        // 设置响应头的内容类型为 application/json;charset=UTF-8，表示响应体为 JSON 格式
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON_UTF8);
        // 设置 body 响应体
        return response.writeWith(Mono.fromSupplier(() -> { // 使用 Mono.fromSupplier 创建响应体
            DataBufferFactory bufferFactory = response.bufferFactory();
            try {
                // 使用 ObjectMapper 将 result 对象转换为 JSON 字节数组
                return bufferFactory.wrap(objectMapper.writeValueAsBytes(result));
            } catch (Exception e) {
                // 如果转换过程中出现异常，则返回空字节数组
                return bufferFactory.wrap(new byte[0]);
            }
        }));
    }
}
