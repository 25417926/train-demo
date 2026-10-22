package com.guo.business.purchase.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 座位落库所需的乐观锁视图（I09）。
 */
@Getter
@Setter
public class SeatRecord {

    private Long id;

    private String sell;

    private Integer version;
}
