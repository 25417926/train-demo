package com.guo.business.purchase;

import com.guo.business.controller.web.PurchaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 受理接口专属错误响应：返回字符串错误码（DUPLICATE_PURCHASE 等）。
 *
 * <p>只作用于 {@link PurchaseController}，避免影响既有 CommonResp 数值码契约。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PurchaseController.class)
public class PurchaseErrorAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(PurchaseErrorAdvice.class);

    @ExceptionHandler(PurchaseException.class)
    public ResponseEntity<PurchaseErrorResp> handle(PurchaseException e) {
        LOG.warn("受理失败 code={} message={}", e.getErrorCode().name(), e.getMessage());
        PurchaseErrorResp body = new PurchaseErrorResp(e.getErrorCode(), e.getMessage(), e.getExisting());
        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(body);
    }
}
