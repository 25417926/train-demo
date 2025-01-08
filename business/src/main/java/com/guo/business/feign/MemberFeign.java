package com.guo.business.feign;

import com.guo.common.req.MemberTicketReq;
import com.guo.common.resp.CommonResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/7
 */
@FeignClient(value = "train-member")
public interface MemberFeign {
    @PostMapping("member/feign/save")
    public CommonResp save(@Valid @RequestBody MemberTicketReq req);
}
