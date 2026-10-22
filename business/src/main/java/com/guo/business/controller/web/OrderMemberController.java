package com.guo.business.controller.web;

import com.guo.business.resp.OrderCancelResp;
import com.guo.business.service.OrderCloseResult;
import com.guo.business.service.OrderCloseService;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * I12 订单取消入口：/business/web/order/{orderId}/cancel。
 */
@Tag(name = "订单(C 端)", description = "订单取消：校验归属后关单并归还库存")
@RestController
@RequestMapping("web/order")
public class OrderMemberController {

    @Resource
    private OrderCloseService orderCloseService;

    @Operation(summary = "取消订单（重复取消幂等）")
    @PostMapping("/{orderId}/cancel")
    public CommonResp<OrderCancelResp> cancel(@PathVariable("orderId") Long orderId) {
        OrderCloseResult result = orderCloseService.cancel(orderId, LoginMemberContext.getId());
        OrderCancelResp resp = new OrderCancelResp();
        resp.setOrderId(orderId);
        resp.setAlreadyClosed(result.isAlreadyClosed());
        return new CommonResp<>(true, "成功", resp, HttpStatus.SUCCESS);
    }
}
