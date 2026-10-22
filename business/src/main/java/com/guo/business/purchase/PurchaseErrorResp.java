package com.guo.business.purchase;

import lombok.Getter;
import lombok.Setter;

/**
 * 受理链路的错误响应体：{ success, code, message, content }。
 */
@Getter
@Setter
public class PurchaseErrorResp {

    private boolean success = false;

    private String code;

    private String message;

    /** 重复购票时携带已有订单，便于前端直接跳转 */
    private PurchaseApplyResp content;

    public PurchaseErrorResp(PurchaseErrorCode errorCode, String message, PurchaseApplyResp content) {
        this.code = errorCode.name();
        this.message = message;
        this.content = content;
    }
}
