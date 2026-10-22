package com.guo.business.purchase;

/**
 * 受理链路错误码（I06）。
 *
 * <p>与 {@link com.guo.common.exception.ExceptionEnum} 的数值码不同，这里返回可读的字符串码，
 * 便于前端按码分支处理（见项目计划 6.4）。</p>
 */
public enum PurchaseErrorCode {

    DUPLICATE_PURCHASE(409, "同乘客同车次已有有效购票/候补"),
    WAITLIST_PRIORITY(409, "当前票档有候补等待，请先提交候补"),
    PURCHASE_REQUEST_EXPIRED(409, "受理已超时，请重新提交"),
    ORDER_ALREADY_CLOSED(409, "订单已关闭，请刷新状态"),
    INVENTORY_NOT_READY(409, "票档库存未就绪，请稍后重试"),
    TICKET_NOT_ENOUGH(409, "余票不足"),
    TICKET_NOT_EXIST(404, "车次余票不存在"),
    IDEMPOTENCY_KEY_REQUIRED(400, "缺少 Idempotency-Key 请求头"),
    IDEMPOTENCY_KEY_INVALID(400, "Idempotency-Key 格式非法"),
    PARAM_INVALID(400, "参数校验异常"),
    ACCEPT_FAILED(500, "受理失败，请稍后重试");

    private final int httpStatus;
    private final String message;

    PurchaseErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
