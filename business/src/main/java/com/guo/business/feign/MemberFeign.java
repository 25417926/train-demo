package com.guo.business.feign;

import com.guo.common.req.MemberTicketReq;
import com.guo.common.req.ValidReq;
import com.guo.common.resp.CommonResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/7
 */
@FeignClient(value = "member", url = "http://127.0.0.1:8080")
public interface MemberFeign {
    @PostMapping("member/feign/save")
    CommonResp save(@Valid @RequestBody MemberTicketReq req);
    @PostMapping("member/feign/valid")
    ValidReq vaild(@RequestBody ValidReq req);
    @PostMapping("member/feign/isCodeExpired")
    ValidReq isCodeExpired(@RequestBody ValidReq req);
}
