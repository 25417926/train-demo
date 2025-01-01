package com.guo.business.controller;

import com.guo.business.req.trainQueryReq;
import com.guo.business.req.trainSaveReq;
import com.guo.business.resp.trainQueryResp;
import com.guo.business.service.trainService;
import com.guo.business.service.train_seatService;
import com.guo.common.resp.CommonResp;
import com.guo.common.resp.PageResp;
import com.guo.common.util.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 */

@RestController
@RequestMapping("/train")
public class trainController {
    @Resource
    private trainService trainService;
    @Resource
    private train_seatService seatService;
    /**
     * 处理乘客信息保存请求
     * 该方法使用HTTP POST请求来接收乘客信息，并将其保存到系统中
     * 使用了参数校验（@Valid注解）以确保请求数据的完整性
     *
     * @param req 一个经过校验的trainSaveReq对象，包含要保存的乘客信息
     *            该对象通过请求体（@RequestBody注解）传递
     * @return 返回一个CommonResp对象，表示保存操作的结果
     * 包含操作是否成功、提示信息以及HTTP状态码
     */
    @PostMapping("/save")
    public CommonResp save(@Valid @RequestBody trainSaveReq req) {
// 调用trainService的save方法来保存乘客信息
        trainService.save(req);

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
    public CommonResp<PageResp<trainQueryResp>> list(@Valid trainQueryReq req) {

        // 调用乘客服务查询列表方法，获取乘客信息列表
        PageResp<trainQueryResp> list = trainService.queryList(req);

        // 创建并返回一个通用响应对象，包含查询结果列表
        CommonResp<PageResp<trainQueryResp>> commonResp = new CommonResp(true, "成功", list, HttpStatus.SUCCESS);
        return commonResp;
    }
    @GetMapping("/Query-All")
    public CommonResp<List<trainQueryResp>> AllList() {

        // 调用乘客服务查询列表方法，获取乘客信息列表
        List<trainQueryResp> list = trainService.queryAll();

        // 创建并返回一个通用响应对象，包含查询结果列表
        CommonResp<List<trainQueryResp>> commonResp = new CommonResp(true, "成功", list, HttpStatus.SUCCESS);
        return commonResp;
    }
    //    @GetMapping("/update")
    //    public CommonResp update(@Valid trainSaveReq req) {
    //        trainService.save(req);
    //        CommonResp commonResp = new CommonResp(true, "成功",HttpStatus.SUCCESS);
    //        return commonResp;
    //    }
    @DeleteMapping("/del/{id}")
    public CommonResp delete(@PathVariable Long id) {
        // 调用trainService的save方法来保存乘客信息
        trainService.delete(id);

        // 创建一个CommonResp对象，表示成功的响应
        // 参数分别为：操作成功（true）、提示信息（"成功"）和HTTP状态码（HttpStatus.SUCCESS）
        CommonResp commonResp = new CommonResp(true, "成功", HttpStatus.SUCCESS);

        // 返回创建的CommonResp对象
        return commonResp;
    }
    @GetMapping("/gen_seat/{trainCode}")
    public CommonResp gen_seat(@PathVariable String trainCode) {
        // 调用trainService的save方法来保存乘客信息
        seatService.genSeat(trainCode);
        // 创建一个CommonResp对象，表示成功的响应
        // 参数分别为：操作成功（true）、提示信息（"成功"）和HTTP状态码（HttpStatus.SUCCESS）
        CommonResp commonResp = new CommonResp(true, "成功", HttpStatus.SUCCESS);

        // 返回创建的CommonResp对象
        return commonResp;
    }
}
