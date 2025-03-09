package com.guo.auth.controller;

import cn.dev33.satoken.stp.StpUtil;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class testController {


    @RequestMapping("doLogin")
    public String doLogin(String username, String password){
        if("zhang".equals(username) && "123456".equals(password)) {
            StpUtil.login(10001);
            return "登录成功";
        }
        return "登录失败";
    }
    @RequestMapping("/user/isLogin")
    public String isLogin(){
        if(StpUtil.isLogin()){
            return "已登录";
        }
        return "未登录";
    }
}
