package com.guo.business.controller.feign;

import com.guo.business.service.OrderTicketStatusService;
import com.guo.common.req.TicketStatusUpdateReq;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * I10 内部接口：供 member 消费端查询/回写订单出票状态。
 */
@RestController
@RequestMapping("/internal/feign/order")
public class InternalOrderTicketController {

    @Resource
    private OrderTicketStatusService orderTicketStatusService;

    @GetMapping("/{orderId}/ticket-status")
    public CommonResp<String> ticketStatus(@PathVariable Long orderId) {
        String status = orderTicketStatusService.selectTicketStatus(orderId);
        if (status == null) {
            return new CommonResp<>(false, "订单不存在", HttpStatus.ERROR);
        }
        return new CommonResp<>(true, "成功", status, HttpStatus.SUCCESS);
    }

    @PostMapping("/ticket-status")
    public CommonResp<Void> updateTicketStatus(@RequestBody TicketStatusUpdateReq req) {
        if (req == null || req.getOrderId() == null) {
            return new CommonResp<>(false, "缺少订单id", HttpStatus.ERROR);
        }
        if (req.isSuccess()) {
            orderTicketStatusService.markIssued(req.getOrderId());
        } else {
            orderTicketStatusService.markFailed(req.getOrderId());
        }
        return new CommonResp<>(true, "成功", HttpStatus.SUCCESS);
    }
}
