package com.guo.business.controller.web;

import com.guo.business.purchase.PurchaseAcceptService;
import com.guo.business.purchase.PurchaseApplyReq;
import com.guo.business.purchase.PurchaseApplyResp;
import com.guo.business.purchase.PurchaseErrorCode;
import com.guo.business.purchase.PurchaseException;
import com.guo.business.service.Sk_TokenService;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.exception.ExceptionConsole;
import com.guo.common.exception.ExceptionEnum;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * 新受理入口（I06）：替换旧 /web/confirm-order/do-storage。
 */
@Tag(name = "下单(C 端)", description = "受理购票申请：幂等键 + Lua 预扣 + 订单 INIT + 事务消息")
@RestController
@RequestMapping("web/purchase")
public class PurchaseController {

    @Resource
    private PurchaseAcceptService purchaseAcceptService;
    @Resource
    private Sk_TokenService skTokenService;

    @Operation(summary = "提交购票申请")
    @PostMapping("/apply")
    public CommonResp<PurchaseApplyResp> apply(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseApplyReq req) {
        Long memberId = LoginMemberContext.getId();
        if (memberId == null) {
            throw new PurchaseException(PurchaseErrorCode.PARAM_INVALID, "未获取到登录用户");
        }
        // 令牌大闸继续作为入口削峰，不作为正确性依据
        if (!skTokenService.validToken(req.getDate(), req.getTrainCode(), memberId)) {
            throw new ExceptionConsole(ExceptionEnum.CONFIRM_ORDER_SK_TOKEN_FAIL);
        }
        PurchaseApplyResp resp = purchaseAcceptService.accept(req, memberId, idempotencyKey);
        return new CommonResp<>(true, "已受理", resp, HttpStatus.SUCCESS);
    }
}
