package com.guo.member.feign;

import com.guo.common.req.TicketStatusUpdateReq;
import com.guo.common.resp.CommonResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * I10 调用 business 内部接口：查询并回写订单出票状态。
 *
 * <p>business/member 各自独立库，member 不直连 business 数据库，跨服务只走 Feign。</p>
 */
@FeignClient(value = "business")
public interface BusinessOrderFeign {

    @GetMapping("business/internal/feign/order/{orderId}/ticket-status")
    CommonResp<String> ticketStatus(@PathVariable("orderId") Long orderId);

    @PostMapping("business/internal/feign/order/ticket-status")
    CommonResp<Void> updateTicketStatus(@RequestBody TicketStatusUpdateReq req);
}
