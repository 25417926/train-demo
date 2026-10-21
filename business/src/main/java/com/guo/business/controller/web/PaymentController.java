package com.guo.business.controller.web;

import com.guo.business.req.PaymentReq;
import com.guo.business.resp.PaymentResp;
import com.guo.business.service.PaymentService;
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
 * 支付 Controller（C 端 + 后台）
 *
 * @Description
 * @Author: 派
 * @Date: 2026/06/04
 */
@Tag(name = "支付", description = "订单支付、查询、列表")
@RestController
@RequestMapping("web/payment")
public class PaymentController {

    @Resource
    private PaymentService paymentService;

    @Operation(summary = "发起支付")
    @PostMapping("/pay")
    public CommonResp<PaymentResp> pay(@Valid @RequestBody PaymentReq req) {
        Long memberId = LoginMemberContext.getId();
        PaymentResp resp = paymentService.pay(req, memberId);
        return new CommonResp<>(true, "支付成功", resp, HttpStatus.SUCCESS);
    }

    @Operation(summary = "按订单号查询支付详情")
    @GetMapping("/query/{orderId}")
    public CommonResp<PaymentResp> queryByOrderId(@PathVariable Long orderId) {
        PaymentResp resp = paymentService.queryByOrderId(orderId);
        return new CommonResp<>(true, "成功", resp, HttpStatus.SUCCESS);
    }

    @Operation(summary = "支付记录分页查询(后台)")
    @GetMapping("/Query-List")
    public CommonResp<PageResp<PaymentResp>> list(@Valid PageReq req) {
        PageResp<PaymentResp> list = paymentService.QueryList(req);
        return new CommonResp(true, "成功", list, HttpStatus.SUCCESS);
    }
}
