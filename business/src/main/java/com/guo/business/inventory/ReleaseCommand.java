package com.guo.business.inventory;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * 释放入参（I05）。释放以 orderId 为凭据，旧订单不会释放新占位。
 */
@Getter
@Setter
public class ReleaseCommand {

    /** 释放幂等键，建议用释放流水号 */
    private String releaseKey;

    private String orderId;

    private Date date;

    private String trainCode;

    private String seatType;

    private int startIndex;

    private int endIndex;

    private List<Long> passengerIds;

    /** 需要释放的 seatId，来源于 hold 或 inventory_hold */
    private List<String> seatIds;
}
