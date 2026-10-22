package com.guo.business.controller.web;

import com.guo.business.feign.MemberFeign;
import com.guo.business.req.Confirm_OrderDoReq;
import com.guo.business.req.Confirm_OrderQueryReq;
import com.guo.business.resp.Confirm_OrderQueryResp;
import com.guo.business.resp.UnpaidOrderResp;
import com.guo.business.service.Confirm_OrderService;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 */

// 定义一个控制器类，处理与确认订单相关的HTTP请求
@Tag(name = "下单(C 端)", description = "用户下单提交：令牌校验 → 落库 → 发送 MQ 异步购票")
@RestController
@RequestMapping("web/confirm-order")
public class Confirm_OrderMemberController {
    Logger LOG = LoggerFactory.getLogger(Confirm_OrderMemberController.class);
    @Resource
    private Confirm_OrderService confirmOrderService;
    @Resource
    private MemberFeign memberFeign;
    /**
     * 旧受理入口已下线（I06）：统一返回 410 Gone，客户端需切换到 /web/purchase/apply。
     */
    @Operation(summary = "提交订单(已下线，返回 410)")
    @PostMapping("/do-storage")
    public ResponseEntity<CommonResp<String>> save(@RequestBody(required = false) Confirm_OrderDoReq req) {
        LOG.warn("旧受理接口 /web/confirm-order/do-storage 已被调用，返回 410");
        CommonResp<String> body = new CommonResp<>(false,
                "该接口已下线，请使用 POST /business/web/purchase/apply", "410", HttpStatus.GONE);
        return ResponseEntity.status(HttpStatus.GONE).body(body);
    }

    @Operation(summary = "未支付订单列表（锁座成功、待支付）")
    @GetMapping("/unpaid-list")
    public CommonResp<List<UnpaidOrderResp>> unpaidList() {
        return new CommonResp<>(true, "成功",
                confirmOrderService.queryUnpaid(LoginMemberContext.getId()), HttpStatus.SUCCESS);
    }

    @Operation(summary = "我的订单分页查询")
    @GetMapping("/Query-List")
    public CommonResp<PageResp<Confirm_OrderQueryResp>> myList(@Valid Confirm_OrderQueryReq req) {
        // 强制按当前登录用户过滤，避免越权
        req.setMemberId(LoginMemberContext.getId());
        PageResp<Confirm_OrderQueryResp> list = confirmOrderService.QueryList(req);
        return new CommonResp<>(true, "成功", list, HttpStatus.SUCCESS);
    }

}
