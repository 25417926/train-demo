package com.guo.business.resp;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.guo.business.TypeEnum.WaitlistStatusEnum;
import com.guo.business.domain.Waitlist_Application;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 我的候补列表项（I15），I17 起同时用于后台候补列表。
 *
 * <p>雪花 ID 统一序列化为字符串，避免前端 Number 精度丢失。</p>
 */
@Getter
@Setter
public class WaitlistResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 会员 id（后台候补列表展示，会员端忽略） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long memberId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long passengerId;

    private String passengerName;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date date;

    private String trainCode;

    private String start;

    private String end;

    private String seatType;

    private Long seqNo;

    private String status;

    private String statusDesc;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long matchedOrderId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public static WaitlistResp of(Waitlist_Application application) {
        WaitlistResp resp = BeanUtil.copyProperties(application, WaitlistResp.class);
        resp.setStatusDesc(WaitlistStatusEnum.descOf(application.getStatus()));
        return resp;
    }
}
