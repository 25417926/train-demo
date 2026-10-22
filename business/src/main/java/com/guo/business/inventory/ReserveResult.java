package com.guo.business.inventory;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 预扣结果（I05）。
 */
@Getter
@Setter
public class ReserveResult {

    private InventoryResultCode code;

    /** 成功时为当前订单；DUPLICATE 时为已存在的订单 */
    private String orderId;

    /** 成功时为新生成的 holdToken；DUPLICATE 时为原 holdToken */
    private String holdToken;

    /** 实际占用的 seatId（daily_train_seat.id） */
    private List<String> seatIds;

    private String message;

    public boolean isSuccess() {
        return code == InventoryResultCode.OK;
    }

    public static ReserveResult of(InventoryResultCode code, String orderId, String holdToken,
                                   List<String> seatIds, String message) {
        ReserveResult result = new ReserveResult();
        result.setCode(code);
        result.setOrderId(orderId);
        result.setHoldToken(holdToken);
        result.setSeatIds(seatIds);
        result.setMessage(message);
        return result;
    }
}
