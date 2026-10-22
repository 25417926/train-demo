package com.guo.business.feign;

import com.guo.common.req.MemberTicketReq;
import com.guo.common.req.TicketIssueReq;
import com.guo.common.req.ValidReq;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PassengerBriefResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/7
 */
@FeignClient(value = "member")
public interface MemberFeign {
    @PostMapping("member/internal/feign/save")
    CommonResp save(@Valid @RequestBody MemberTicketReq req);
    @PostMapping("member/internal/feign/valid")
    ValidReq vaild(@RequestBody ValidReq req);
    @PostMapping("member/internal/feign/isCodeExpired")
    ValidReq isCodeExpired(@RequestBody ValidReq req);

    /**
     * 按订单id查询车票（用于退票/退改签精确反查座位）
     */
    @GetMapping("member/internal/feign/ticket/list-by-order")
    CommonResp<List<Map<String, Object>>> listByOrder(@RequestParam("orderId") Long orderId);

    /**
     * 删除车票
     */
    @DeleteMapping("member/internal/feign/ticket/del/{id}")
    CommonResp deleteTicket(@PathVariable("id") Long id);

    /**
     * I10 按订单批量出票（member 侧按 (order_id, passenger_id) 幂等）
     */
    @PostMapping("member/internal/feign/ticket/issue")
    CommonResp<Integer> issueTickets(@RequestBody TicketIssueReq req);

    /**
     * I10 按订单精确删除本单车票
     */
    @DeleteMapping("member/internal/feign/ticket/by-order/{orderId}")
    CommonResp<Integer> deleteTicketsByOrder(@PathVariable("orderId") Long orderId);

    /**
     * I15 按会员查询乘客简要信息（校验候补申请归属）
     */
    @GetMapping("member/internal/feign/passenger/by-member")
    CommonResp<List<PassengerBriefResp>> listPassengersByMember(@RequestParam("memberId") Long memberId);
}
