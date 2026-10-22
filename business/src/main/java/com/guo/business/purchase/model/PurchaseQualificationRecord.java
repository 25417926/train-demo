package com.guo.business.purchase.model;

import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

/**
 * purchase_qualification：乘客在某个车次上的购票资格。
 */
@Getter
@Setter
public class PurchaseQualificationRecord {

    private Long id;

    private String requestId;

    private Long memberId;

    private Long passengerId;

    private Date date;

    private String trainCode;

    private String start;

    private String end;

    /** 席别编码 1/2/3/4，对应 char(1) */
    private String seatType;

    private Long orderId;

    /** H 占用 / C 已消费 / R 已释放 / T 已转移 / E 过期 / X 取消 */
    private String status;

    /** 有效占位唯一键；释放后置 NULL */
    private String activeKey;

    private java.util.Date expireAt;

    private java.util.Date createTime;

    private java.util.Date updateTime;
}
