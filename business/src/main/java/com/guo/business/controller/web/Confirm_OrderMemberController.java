package com.guo.business.controller.web;

import com.guo.business.req.Confirm_OrderDoReq;
import com.guo.business.service.Confirm_OrderService;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 */

// 定义一个控制器类，处理与确认订单相关的HTTP请求
@RestController
@RequestMapping("web/confirm-order")
public class Confirm_OrderMemberController {
    // 注入Confirm_OrderService接口的实现，用于处理订单相关的业务逻辑
    @Resource
    private Confirm_OrderService Confirm_OrderService;

    /**
     * 处理乘客信息保存请求
     * 该方法使用HTTP POST请求来接收乘客信息，并将其保存到系统中
     * 使用了参数校验（@Valid注解）以确保请求数据的完整性
     *
     * @param req 一个经过校验的Confirm_OrderSaveReq对象，包含要保存的乘客信息
     *            该对象通过请求体（@RequestBody注解）传递
     * @return 返回一个CommonResp对象，表示保存操作的结果
     * 包含操作是否成功、提示信息以及HTTP状态码
     */
    @PostMapping("/do-storage")
    public CommonResp save(@Valid @RequestBody Confirm_OrderDoReq req) {
        // 调用Confirm_OrderService的save方法来保存乘客信息
        Confirm_OrderService.DoStorage(req);

        // 创建一个CommonResp对象，表示成功的响应
        // 参数分别为：操作成功（true）、提示信息（"成功"）和HTTP状态码（HttpStatus.SUCCESS）
        CommonResp commonResp = new CommonResp(true, "成功", HttpStatus.SUCCESS);

        // 返回创建的CommonResp对象
        return commonResp;
    }

}
