package com.guo.member.controller;

import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import com.guo.member.req.MemberLoginReq;
import com.guo.member.req.MemberRegisterReq;
import com.guo.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/12
 */

@Tag(name = "会员管理", description = "会员注册、登录、统计")
@RestController
@RequestMapping
public class MemberController {
    @Resource
    private MemberService memberService;

    @Operation(summary = "会员总数")
    @GetMapping("/count")
    public CommonResp<Integer> count() {
        String count = memberService.count();
        CommonResp<Integer> commonResp = new CommonResp(true, "成功", count, HttpStatus.SUCCESS);
        return commonResp;
    }

    @Operation(summary = "会员注册（手机号）")
    @PostMapping("/register")
    public CommonResp<Long> register(@Valid MemberRegisterReq req) {
        Long id = memberService.register(req);
        CommonResp<Long> commonResp = new CommonResp(HttpStatus.SUCCESS,id,"注册成功");
        return commonResp;
    }



    @Operation(summary = "验证码登录")
    @PostMapping("/LoginCode")
    public CommonResp<MemberLoginReq> LoginCode(@Valid @RequestBody MemberLoginReq req) {

        String token =memberService.Login(req);
        req.setToken(token);
        CommonResp commonResp = new CommonResp<>(HttpStatus.SUCCESS,req,"登录成功");
        return commonResp;
    }
}
