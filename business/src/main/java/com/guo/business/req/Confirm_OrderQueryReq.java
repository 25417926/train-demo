package com.guo.business.req;

import com.guo.common.req.PageReq;

public class Confirm_OrderQueryReq extends PageReq {

    /**
     * 会员 ID。
     * - 会员端 /web/confirm-order/Query-List 使用：从登录上下文回填，过滤"我的订单"
     * - 后台 /confirm-order/Query-List 不传则查全部
     */
    private Long memberId;

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    @Override
    public String toString() {
        return "Confirm_OrderQueryReq{" +
                "memberId=" + memberId +
                "} " + super.toString();
    }
}
