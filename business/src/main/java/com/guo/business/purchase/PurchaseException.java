package com.guo.business.purchase;

/**
 * 受理业务异常，携带字符串错误码与可选的已有受理结果。
 *
 * <p>命中重复购票时把已有订单放进 {@link #getExisting()}，前端可直接跳转订单详情。</p>
 */
public class PurchaseException extends RuntimeException {

    private final PurchaseErrorCode errorCode;

    private final PurchaseApplyResp existing;

    public PurchaseException(PurchaseErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public PurchaseException(PurchaseErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public PurchaseException(PurchaseErrorCode errorCode, String message, PurchaseApplyResp existing) {
        super(message);
        this.errorCode = errorCode;
        this.existing = existing;
    }

    public PurchaseErrorCode getErrorCode() {
        return errorCode;
    }

    public PurchaseApplyResp getExisting() {
        return existing;
    }
}
