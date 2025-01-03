package com.guo.business.controller;

import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.HttpStatus;
import com.guo.business.req.Daily_Train_CarriageQueryReq;
import com.guo.business.req.Daily_Train_CarriageSaveReq;
import com.guo.business.resp.Daily_Train_CarriageQueryResp;
import com.guo.business.service.Daily_Train_CarriageService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
* @Description
* @Author: 派
* @Date: 2024/12/12
*/

@RestController
@RequestMapping("/daily-train-carriage")
public class Daily_Train_CarriageController {
@Resource
private Daily_Train_CarriageService Daily_Train_CarriageService;

/**
* 处理乘客信息保存请求
* 该方法使用HTTP POST请求来接收乘客信息，并将其保存到系统中
* 使用了参数校验（@Valid注解）以确保请求数据的完整性
*
* @param req 一个经过校验的Daily_Train_CarriageSaveReq对象，包含要保存的乘客信息
*            该对象通过请求体（@RequestBody注解）传递
* @return 返回一个CommonResp对象，表示保存操作的结果
* 包含操作是否成功、提示信息以及HTTP状态码
*/
@PostMapping("/save")
public CommonResp save(@Valid @RequestBody Daily_Train_CarriageSaveReq req) {
// 调用Daily_Train_CarriageService的save方法来保存乘客信息
Daily_Train_CarriageService.save(req);

// 创建一个CommonResp对象，表示成功的响应
// 参数分别为：操作成功（true）、提示信息（"成功"）和HTTP状态码（HttpStatus.SUCCESS）
CommonResp commonResp = new CommonResp(true, "成功", HttpStatus.SUCCESS);

// 返回创建的CommonResp对象
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
public CommonResp<PageResp<Daily_Train_CarriageQueryResp>> list(@Valid Daily_Train_CarriageQueryReq req) {
    // 设置查询请求中的成员ID为当前登录成员的ID
    //req.setMemberId(LoginMemberContext.getId());

    // 调用乘客服务查询列表方法，获取乘客信息列表
    PageResp<Daily_Train_CarriageQueryResp> list = Daily_Train_CarriageService.QueryList(req);

        // 创建并返回一个通用响应对象，包含查询结果列表
        CommonResp<PageResp<Daily_Train_CarriageQueryResp>> commonResp = new CommonResp(true, "成功", list, HttpStatus.SUCCESS);
            return commonResp;
            }

            //    @GetMapping("/update")
            //    public CommonResp update(@Valid Daily_Train_CarriageSaveReq req) {
            //        Daily_Train_CarriageService.save(req);
            //        CommonResp commonResp = new CommonResp(true, "成功",HttpStatus.SUCCESS);
            //        return commonResp;
            //    }
            @DeleteMapping("/del/{id}")
            public CommonResp delete(@PathVariable Long id) {
            // 调用Daily_Train_CarriageService的save方法来保存乘客信息
            Daily_Train_CarriageService.delete(id);

            // 创建一个CommonResp对象，表示成功的响应
            // 参数分别为：操作成功（true）、提示信息（"成功"）和HTTP状态码（HttpStatus.SUCCESS）
            CommonResp commonResp = new CommonResp(true, "成功", HttpStatus.SUCCESS);

            // 返回创建的CommonResp对象
            return commonResp;
            }
            }
