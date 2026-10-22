package com.guo.business.purchase;

import com.guo.business.TypeEnum.ConfirmOrderStatusEnum;
import lombok.Getter;
import lombok.Setter;

/**
 * 受理结果：同步返回幂等键、订单号与订单状态。
 */
@Getter
@Setter
public class PurchaseApplyResp {

    private String requestId;

    private Long orderId;

    /** 订单状态码，受理成功固定为 I（INIT） */
    private String status;

    private String statusDesc;

    public static PurchaseApplyResp accepted(String requestId, Long orderId) {
        PurchaseApplyResp resp = new PurchaseApplyResp();
        resp.setRequestId(requestId);
        resp.setOrderId(orderId);
        resp.setStatus(ConfirmOrderStatusEnum.INIT.getCode());
        resp.setStatusDesc("已受理，出票中");
        return resp;
    }
}
