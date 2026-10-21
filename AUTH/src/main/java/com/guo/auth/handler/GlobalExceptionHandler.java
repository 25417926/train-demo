package com.guo.auth.handler;

import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * AUTH 模块全局异常处理：把 ExceptionConsole 解包成 code/message 返回，避免日志里只看到 null
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExceptionConsole.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(ExceptionConsole ex) {
        ExceptionEnum e = ex.getExceptionEnum();
        Integer code = e == null ? 500 : e.getCode();
        String message = e == null ? "未知业务异常" : e.getMessage();

        log.warn("业务异常: code={}, message={}", code, message);

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("系统异常", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("code", 500);
        body.put("message", "系统异常: " + ex.getMessage());
        return ResponseEntity.status(500).body(body);
    }
}
