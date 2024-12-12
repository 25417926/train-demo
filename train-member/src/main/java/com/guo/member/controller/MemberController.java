package com.guo.member.controller;

import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import com.guo.member.req.MemberRegisterReq;
import com.guo.member.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping
public class MemberController {
    @Resource
    private MemberService memberService;

    @GetMapping("/count")
    public CommonResp<Integer> count() {
        int count = memberService.count();
        CommonResp<Integer> commonResp = new CommonResp(true, "成功", count, HttpStatus.SUCCESS);
        return commonResp;
    }

    @PostMapping("/register")
    public CommonResp<Long> register(@Valid MemberRegisterReq req) {
        Long id = memberService.register(req);
        CommonResp<Long> commonResp = new CommonResp(HttpStatus.SUCCESS,id,"注册成功");
        return commonResp;
    }
}
