package com.guo.member.controller.admin;

import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.HttpStatus;
import com.guo.member.req.TicketQueryReq;
import com.guo.member.resp.TicketQueryResp;
import com.guo.member.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 */

@RestController
@RequestMapping("admin/ticket")
public class AdminTicketController {
    @Resource
    TicketService TicketService;



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
    public CommonResp<PageResp<TicketQueryResp>> list(@Valid TicketQueryReq req) {



        // 调用乘客服务查询列表方法，获取乘客信息列表
        PageResp<TicketQueryResp> list = TicketService.QueryList(req);

        // 创建并返回一个通用响应对象，包含查询结果列表
        CommonResp<PageResp<TicketQueryResp>> commonResp = new CommonResp(true, "成功", list, HttpStatus.SUCCESS);
        return commonResp;
    }

    //    @GetMapping("/update")
    //    public CommonResp update(@Valid TicketSaveReq req) {
    //        TicketService.save(req);
    //        CommonResp commonResp = new CommonResp(true, "成功",HttpStatus.SUCCESS);
    //        return commonResp;
    //    }

}
