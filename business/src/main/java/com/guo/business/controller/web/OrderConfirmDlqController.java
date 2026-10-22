package com.guo.business.controller.web;

import com.guo.business.mq.OrderConfirmDlqService;
import com.guo.business.req.DlqReplayReq;
import com.guo.business.resp.DlqMessageResp;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * I08 死信运维接口：只读查询 + 人工重放。
 */
@Tag(name = "订单确认死信(运维)", description = "ORDER_CONFIRM 死信查询与人工重放")
@RestController
@RequestMapping("web/order-confirm/dlq")
public class OrderConfirmDlqController {

    private final OrderConfirmDlqService dlqService;

    public OrderConfirmDlqController(OrderConfirmDlqService dlqService) {
        this.dlqService = dlqService;
    }

    @Operation(summary = "查询死信消息")
    @GetMapping
    public CommonResp<List<DlqMessageResp>> query(@RequestParam(defaultValue = "20") int maxNum) {
        return new CommonResp<>(true, "成功", dlqService.query(maxNum), HttpStatus.SUCCESS);
    }

    @Operation(summary = "人工重放死信消息")
    @PostMapping("/replay")
    public CommonResp<String> replay(@Valid @RequestBody DlqReplayReq req) {
        dlqService.replay(req.getMsgId());
        return new CommonResp<>(true, "重放成功", req.getMsgId(), HttpStatus.SUCCESS);
    }
}
