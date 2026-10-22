package com.guo.business.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 候补申请（事实源，Redis ZSet 仅作顺序索引）。
 */
@Getter
@Setter
public class Waitlist_Application {

    private Long id;

    /** 申请幂等键 */
    private String requestId;

    private Long memberId;

    private Long passengerId;

    private String passengerName;

    private Date date;

    private String trainCode;

    private String start;

    private String end;

    private String seatType;

    /** 同票档排队序号，一次分配不再改变 */
    private Long seqNo;

    /** W等待 M匹配中 D已匹配 E过期 C取消 F失败 */
    private String status;

    /** 有效申请唯一键；结束后置 NULL */
    private String activeKey;

    private Long matchedOrderId;

    private Date expireAt;

    private Date createTime;

    private Date updateTime;
}
