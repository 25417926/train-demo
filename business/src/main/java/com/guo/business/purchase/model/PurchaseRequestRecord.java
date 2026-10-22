package com.guo.business.purchase.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * purchase_request：受理请求幂等表。
 */
@Getter
@Setter
public class PurchaseRequestRecord {

    private Long id;

    private String requestId;

    private Long memberId;

    /** 请求体 SHA-256 摘要，用于审计与人工比对 */
    private String payloadHash;

    /** A 受理 / P 处理中 / S 成功 / F 失败 */
    private String status;

    private Long orderId;

    /** 首个资格 id（多乘客时仅作索引） */
    private Long qualificationId;

    private Date createTime;

    private Date updateTime;
}
