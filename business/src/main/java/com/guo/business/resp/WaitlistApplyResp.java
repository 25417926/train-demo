package com.guo.business.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.guo.business.TypeEnum.WaitlistStatusEnum;
import com.guo.business.domain.Waitlist_Application;
import lombok.Getter;
import lombok.Setter;

/**
 * 候补申请结果（I15）。
 */
@Getter
@Setter
public class WaitlistApplyResp {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicationId;

    private Long seqNo;

    private String status;

    private String statusDesc;

    /** true=本次新建；false=命中已有申请 */
    private boolean created;

    public static WaitlistApplyResp of(Waitlist_Application application, boolean created) {
        WaitlistApplyResp resp = new WaitlistApplyResp();
        resp.setApplicationId(application.getId());
        resp.setSeqNo(application.getSeqNo());
        resp.setStatus(application.getStatus());
        resp.setStatusDesc(WaitlistStatusEnum.descOf(application.getStatus()));
        resp.setCreated(created);
        return resp;
    }
}
