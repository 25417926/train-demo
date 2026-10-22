package com.guo.business.controller.web;

import com.guo.business.req.WaitlistApplyReq;
import com.guo.business.resp.WaitlistApplyResp;
import com.guo.business.resp.WaitlistResp;
import com.guo.business.service.WaitlistService;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.req.PageReq;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * 候补（I15）：会员端申请、取消与我的候补。
 */
@Tag(name = "候补", description = "FIFO 候补申请、取消与查询")
@RestController
@RequestMapping("web/waitlist")
public class WaitlistController {

    @Resource
    private WaitlistService waitlistService;

    @Operation(summary = "申请候补")
    @PostMapping("/apply")
    public CommonResp<WaitlistApplyResp> apply(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody WaitlistApplyReq req) {
        WaitlistApplyResp resp = waitlistService.apply(req, LoginMemberContext.getId(), idempotencyKey);
        return new CommonResp<>(true, resp.isCreated() ? "候补申请成功" : "已存在候补申请",
                resp, HttpStatus.SUCCESS);
    }

    @Operation(summary = "取消候补")
    @PostMapping("/{applicationId}/cancel")
    public CommonResp<WaitlistApplyResp> cancel(@PathVariable("applicationId") Long applicationId) {
        WaitlistApplyResp resp = waitlistService.cancel(applicationId, LoginMemberContext.getId());
        return new CommonResp<>(true, "候补已取消", resp, HttpStatus.SUCCESS);
    }

    @Operation(summary = "我的候补")
    @GetMapping("/mine")
    public CommonResp<PageResp<WaitlistResp>> mine(@Valid PageReq req,
                                                   @RequestParam(value = "status", required = false) String status) {
        PageResp<WaitlistResp> list = waitlistService.queryMine(req, LoginMemberContext.getId(), status);
        return new CommonResp<>(true, "成功", list, HttpStatus.SUCCESS);
    }
}
