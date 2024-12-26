package com.guo.member.controller;

import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.HttpStatus;
import com.guo.member.req.PassengerQueryReq;
import com.guo.member.req.PassengerSaveReq;
import com.guo.member.resp.PassengerQueryResp;
import com.guo.member.service.PassengerService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 */

@RestController
@RequestMapping("/Passenger")
public class PassengerController {
    @Resource
    private PassengerService PassengerService;

    @PostMapping("/save")
    public CommonResp save(@Valid @RequestBody PassengerSaveReq req) {
        PassengerService.save(req);
        CommonResp commonResp = new CommonResp(true, "成功",HttpStatus.SUCCESS);
        return commonResp;
    }
    /**
     * 查询乘客列表
     * 此方法用于处理乘客查询请求它接收一个经过验证的乘客查询请求对象，
     * 添加了当前登录成员的ID后，调用乘客服务查询列表方法获取乘客信息列表，
     * 并将结果封装在一个通用响应对象中返回
     *
     * @param req 经过验证的乘客查询请求对象，包含查询所需的各种参数
     * @return 返回一个通用响应对象，其中包含查询结果列表
     */
    @GetMapping("/Query-List")
    public CommonResp<PageResp<PassengerQueryResp>> list(@Valid PassengerQueryReq req) {
        // 设置查询请求中的成员ID为当前登录成员的ID
        req.setMemberId(LoginMemberContext.getId());

        // 调用乘客服务查询列表方法，获取乘客信息列表
        PageResp<PassengerQueryResp> list = PassengerService.QueryList(req);

        // 创建并返回一个通用响应对象，包含查询结果列表
        CommonResp<PageResp<PassengerQueryResp>> commonResp = new CommonResp(true, "成功",list,HttpStatus.SUCCESS);
        return commonResp;
    }

}
