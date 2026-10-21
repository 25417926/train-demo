package com.guo.member.controller.feign;

import cn.hutool.core.bean.BeanUtil;
import com.guo.common.req.MemberTicketReq;
import com.guo.common.req.ValidReq;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import com.guo.member.domain.Ticket;
import com.guo.member.resp.TicketQueryResp;
import com.guo.member.service.TicketService;
import com.guo.member.service.ValidCodeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/7
 */
@RestController
@RequestMapping("/internal/feign")
public class FeignTicketController {
    @Resource
    private TicketService ticketService;
    @Resource
    private ValidCodeService validCodeService;

    /**
     * 处理乘客信息保存请求
     * 该方法使用HTTP POST请求来接收乘客信息，并将其保存到系统中
     * 使用了参数校验（@Valid注解）以确保请求数据的完整性
     *
     * @param req 一个经过校验的TicketSaveReq对象，包含要保存的乘客信息
     *            该对象通过请求体（@RequestBody注解）传递
     * @return 返回一个CommonResp对象，表示保存操作的结果
     * 包含操作是否成功、提示信息以及HTTP状态码
     */
    @PostMapping("/save")
    public CommonResp save(@Valid @RequestBody MemberTicketReq req) {
        // 调用TicketService的save方法来保存乘客信息
        ticketService.save(req);

        // 创建一个CommonResp对象，表示成功的响应
        // 参数分别为：操作成功（true）、提示信息（"成功"）和HTTP状态码（HttpStatus.SUCCESS）
        CommonResp commonResp = new CommonResp(true, "成功", HttpStatus.SUCCESS);

        // 返回创建的CommonResp对象
        return commonResp;
    }
    @PostMapping("/valid")
    public ValidReq vaild(@RequestBody ValidReq req) {
        // 调用TicketService的save方法来保存乘客信息
        boolean isFlag = validCodeService.ValidForCode(req.getToken(),req.getCode());
        ValidReq isValid =new ValidReq();
        isValid.setSuccess(isFlag);
        return isValid;
    }
    @PostMapping("/isCodeExpired")
    public ValidReq isCodeExpired(@RequestBody ValidReq req) {
        ValidReq isValid =new ValidReq();
        isValid.setSuccess(validCodeService.isCodeExpired(req.getToken()));
        return isValid;
    }

    /**
     * 按订单 key 查询车票（供 business 退票/退改签反查座位）
     */
    @GetMapping("/ticket/list-by-order")
    public CommonResp<List<TicketQueryResp>> listByOrder(
            @RequestParam("memberId") Long memberId,
            @RequestParam("trainDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date trainDate,
            @RequestParam("trainCode") String trainCode,
            @RequestParam(value = "startStation", required = false) String startStation,
            @RequestParam(value = "endStation", required = false) String endStation) {
        List<Ticket> list = ticketService.selectByOrderKey(memberId, trainDate, trainCode, startStation, endStation);
        List<TicketQueryResp> respList = list.stream()
                .map(t -> BeanUtil.copyProperties(t, TicketQueryResp.class))
                .collect(Collectors.toList());
        return new CommonResp(true, "成功", respList, HttpStatus.SUCCESS);
    }

    /**
     * 删除车票（供 business 退票流程使用）
     */
    @DeleteMapping("/ticket/del/{id}")
    public CommonResp deleteTicket(@PathVariable Long id) {
        ticketService.delete(id);
        return new CommonResp(true, "成功", HttpStatus.SUCCESS);
    }
}
