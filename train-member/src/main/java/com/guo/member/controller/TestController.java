package com.guo.member.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/11
 */
@RestController
public class Test {
    @GetMapping("/hello")
    public String hello()
    {
        return "hello";
    }
}
