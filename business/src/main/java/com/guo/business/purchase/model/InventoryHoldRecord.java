package com.guo.business.purchase.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * inventory_hold 预扣账本行（I09）。
 */
@Getter
@Setter
public class InventoryHoldRecord {

    private Long id;

    private String holdToken;

    private String requestId;

    private Long orderId;

    private Long memberId;

    private Long passengerId;

    private Date date;

    private String trainCode;

    private Long seatId;

    private String seatType;

    private Integer startIndex;

    private Integer endIndex;

    /** H 持有 / R 已释放 */
    private String status;

    private Date createTime;
}
