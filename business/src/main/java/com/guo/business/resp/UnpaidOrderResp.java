package com.guo.business.resp;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 未支付订单（锁座成功、尚未出票、支付未超时）。
 */
@Getter
@Setter
public class UnpaidOrderResp {

    private Long id;

    private Date date;

    private String trainCode;

    private String start;

    private String end;

    /** 支付截止时间，前端用它做倒计时 */
    private Date expireAt;

    /** 剩余秒数，<=0 表示已到期（等待 I04 定时任务取消） */
    private Long remainingSeconds;

    private Date createTime;
}
