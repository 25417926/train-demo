package com.guo.business.resp;

import cn.hutool.core.bean.BeanUtil;
import com.guo.business.TypeEnum.WaitlistStatusEnum;
import com.guo.business.domain.Waitlist_Application;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 我的候补列表项（I15）。
 */
@Getter
@Setter
public class WaitlistResp {

    private Long id;

    private Long passengerId;

    private String passengerName;

    private Date date;

    private String trainCode;

    private String start;

    private String end;

    private String seatType;

    private Long seqNo;

    private String status;

    private String statusDesc;

    private Long matchedOrderId;

    private Date createTime;

    public static WaitlistResp of(Waitlist_Application application) {
        WaitlistResp resp = BeanUtil.copyProperties(application, WaitlistResp.class);
        resp.setStatusDesc(WaitlistStatusEnum.descOf(application.getStatus()));
        return resp;
    }
}
