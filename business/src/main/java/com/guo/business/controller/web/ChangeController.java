package com.guo.business.controller.web;

import com.guo.business.req.ChangeReq;
import com.guo.business.resp.ChangeRecordResp;
import com.guo.business.service.ChangeService;
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
 * 退改签 Controller（C 端 + 后台）
 *
 * @Description
 * @Author: 派
 * @Date: 2026/06/04
 */
@Tag(name = "退改签", description = "改签 = 退原票（厚实现）+ 重新下单（走令牌+MQ 流程）")
@RestController
@RequestMapping("web/change")
public class ChangeController {

    @Resource
    private ChangeService changeService;

    @Operation(summary = "申请改签")
    @PostMapping("/apply")
    public CommonResp<ChangeRecordResp> apply(@Valid @RequestBody ChangeReq req) {
        Long memberId = LoginMemberContext.getId();
        ChangeRecordResp resp = changeService.change(req, memberId);
        return new CommonResp<>(true, "改签成功", resp, HttpStatus.SUCCESS);
    }

    @Operation(summary = "退改签记录分页查询(后台)")
    @GetMapping("/Query-List")
    public CommonResp<PageResp<ChangeRecordResp>> list(@Valid PageReq req) {
        PageResp<ChangeRecordResp> list = changeService.QueryList(req);
        return new CommonResp(true, "成功", list, HttpStatus.SUCCESS);
    }
}
