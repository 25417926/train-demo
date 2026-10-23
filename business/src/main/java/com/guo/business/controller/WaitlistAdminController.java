package com.guo.business.controller;

import com.guo.business.resp.WaitlistApplyResp;
import com.guo.business.resp.WaitlistResp;
import com.guo.business.service.WaitlistService;
import com.guo.common.req.PageReq;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Date;

/**
 * 候补（I17 后台）：候补列表按车次/票档筛选与人工取消。
 */
@Tag(name = "候补(后台)", description = "候补队列查看与人工取消")
@RestController
@RequestMapping("/waitlist")
public class WaitlistAdminController {

    @Resource
    private WaitlistService waitlistService;

    @Operation(summary = "候补列表")
    @GetMapping("/Query-List")
    public CommonResp<PageResp<WaitlistResp>> list(
            @Valid PageReq pageReq,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") Date date,
            @RequestParam(value = "trainCode", required = false) String trainCode,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end,
            @RequestParam(value = "seatType", required = false) String seatType,
            @RequestParam(value = "status", required = false) String status) {
        PageResp<WaitlistResp> list = waitlistService.queryAdmin(
                pageReq, date, trainCode, start, end, seatType, status);
        return new CommonResp<>(true, "成功", list, HttpStatus.SUCCESS);
    }

    @Operation(summary = "人工取消候补")
    @PostMapping("/{applicationId}/cancel")
    public CommonResp<WaitlistApplyResp> cancel(@PathVariable("applicationId") Long applicationId) {
        WaitlistApplyResp resp = waitlistService.cancelByAdmin(applicationId);
        return new CommonResp<>(true, "候补已取消", resp, HttpStatus.SUCCESS);
    }
}
