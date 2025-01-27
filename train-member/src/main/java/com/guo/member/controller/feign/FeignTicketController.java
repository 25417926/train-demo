package com.guo.member.controller.feign;

import com.guo.common.req.MemberTicketReq;
import com.guo.common.req.ValidReq;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import com.guo.member.service.TicketService;
import com.guo.member.service.ValidCodeService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/7
 */
@RestController
@RequestMapping("/feign")
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
}
