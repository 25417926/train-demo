package com.guo.member.controller;

import com.guo.member.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

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
    public int count() {
        return memberService.count();
    }

    @PostMapping("/register")
    public Long register(String mobile) {
        return memberService.register(mobile);
    }
}
