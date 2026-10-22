package com.guo.business.inventory;

import lombok.Getter;
import lombok.Setter;

/**
 * 释放结果（I05）。releasedSeats 表示本次实际归还的座位数。
 */
@Getter
@Setter
public class ReleaseResult {

    private InventoryResultCode code;

    private int releasedSeats;

    public static ReleaseResult of(InventoryResultCode code, int releasedSeats) {
        ReleaseResult result = new ReleaseResult();
        result.setCode(code);
        result.setReleasedSeats(releasedSeats);
        return result;
    }
}
