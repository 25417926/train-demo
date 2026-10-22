package com.guo.business.inventory;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * 预扣入参（I05）。
 */
@Getter
@Setter
public class ReserveCommand {

    /** 受理幂等键，同一 requestId 只会成功一次 */
    private String requestId;

    /** 订单号，同时作为资格与座位占用的凭据 */
    private String orderId;

    private Date date;

    private String trainCode;

    /** 席别编码 1/2/3/4 */
    private String seatType;

    /** 上车站序，1-based */
    private int startIndex;

    /** 下车站序，1-based，区间为 [startIndex, endIndex) */
    private int endIndex;

    private List<Long> passengerIds;

    /** 资格 TTL，默认 20 分钟 */
    private long qualTtlMs;

    /** hold 凭据 TTL，默认 5 分钟 */
    private long holdTtlMs;
}
