package com.guo.business.controller.web;

import com.guo.business.req.RefundReq;
import com.guo.business.resp.RefundResp;
import com.guo.business.service.RefundService;
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
 * 退款/退票 Controller（C 端 + 后台）
 *
 * @Description
 * @Author: 派
 * @Date: 2026/06/04
 */
@Tag(name = "退款/退票", description = "退票（自动回滚 sell/余票/会员车票）并退款")
@RestController
@RequestMapping("web/refund")
public class RefundController {

    @Resource
    private RefundService refundService;

    @Operation(summary = "申请退票退款")
    @PostMapping("/apply")
    public CommonResp<RefundResp> apply(@Valid @RequestBody RefundReq req) {
        Long memberId = LoginMemberContext.getId();
        RefundResp resp = refundService.refund(req, memberId);
        return new CommonResp<>(true, "退款成功", resp, HttpStatus.SUCCESS);
    }

    @Operation(summary = "退款记录分页查询(后台)")
    @GetMapping("/Query-List")
    public CommonResp<PageResp<RefundResp>> list(@Valid PageReq req) {
        PageResp<RefundResp> list = refundService.QueryList(req);
        return new CommonResp(true, "成功", list, HttpStatus.SUCCESS);
    }
}
