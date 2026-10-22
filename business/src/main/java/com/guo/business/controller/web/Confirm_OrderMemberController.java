package com.guo.business.controller.web;

import com.guo.business.feign.MemberFeign;
import com.guo.business.req.Confirm_OrderDoReq;
import com.guo.business.req.Confirm_OrderQueryReq;
import com.guo.business.resp.Confirm_OrderQueryResp;
import com.guo.business.resp.UnpaidOrderResp;
import com.guo.business.service.Before_Confirm_OrderService;
import com.guo.business.service.Confirm_OrderService;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.req.ValidReq;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    // 注入BeforeConfirm_OrderService接口的实现
    @Resource
    private Before_Confirm_OrderService beforeConfirmOrderService;
    @Resource
    private Confirm_OrderService confirmOrderService;
    @Resource
    private MemberFeign memberFeign;
    /**
     * 处理乘客信息保存请求
     * 该方法使用HTTP POST请求来接收乘客信息，并将其保存到系统中
     * 使用了参数校验（@Valid注解）以确保请求数据的完整性
     *
     * @param req 一个经过校验的Confirm_OrderSaveReq对象，包含要保存的乘客信息
     *            该对象通过请求体（@RequestBody注解）传递
     * @return 返回一个CommonResp对象，表示保存操作的结果
     * 包含操作是否成功、提示信息以及HTTP状态码
     */
    @Operation(summary = "提交订单(下单)")
    @PostMapping("/do-storage")
    public CommonResp save(@Valid @RequestBody Confirm_OrderDoReq req) {
        // 调用Confirm_OrderService的save方法来保存乘客信息

        ValidReq valid = new ValidReq();
        valid.setCode(req.getCode());
        valid.setToken(req.getCodeToken());

//        ValidReq isCodeExpired =memberFeign.isCodeExpired(valid);
//        if(!isCodeExpired.isSuccess()){
//            CommonResp commonResp = new CommonResp(false, "验证码过期", HttpStatus.BAD_REQUEST);
//            return commonResp;
//        }
//        ValidReq isValid=memberFeign.vaild(valid);
//        if (!isValid.isSuccess()) {
//            CommonResp commonResp = new CommonResp(false, "验证码错误", HttpStatus.BAD_REQUEST);
//            return commonResp;
//        }
        Long l = beforeConfirmOrderService.BeforeDoConfirm(req);

        // 创建一个CommonResp对象，表示成功的响应
        // 参数分别为：操作成功（true）、提示信息（"成功"）和HTTP状态码（HttpStatus.SUCCESS）
        CommonResp commonResp = new CommonResp(true, "成功",String.valueOf(l), HttpStatus.SUCCESS);
        // 返回创建的CommonResp对象
        return commonResp;
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
