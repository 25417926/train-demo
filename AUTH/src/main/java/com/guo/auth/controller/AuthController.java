package com.guo.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.guo.auth.req.UserReq;
import com.guo.auth.resp.UserResp;
import com.guo.auth.service.AuthService;
import com.guo.common.context.LoginMemberContext;
import com.guo.common.resp.CommonResp;
import com.guo.common.util.HttpStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.ibatis.annotations.Param;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Tag(name = "认证授权", description = "登录/单点登录/邮箱验证码")
@RestController
public class AuthController {

    @Resource
    private AuthService authService;

    @GetMapping("/sso/isLogin")
    public boolean isLogin(String token) {
        return StpUtil.isLogin(token);
    }

    @PostMapping("/sso/login")
    public CommonResp login(@Validated @RequestBody UserReq user) {
        UserResp login = authService.login(user);
        return new CommonResp(HttpStatus.SUCCESS, login, "登录成功");
    }

    @GetMapping("/sso/SendCode/{email}")
    public CommonResp SendCode(@PathVariable("email") String email) throws Exception {
        byte[] sendCode = authService.SendCode(email);
        return new CommonResp(HttpStatus.SUCCESS, sendCode, "发送成功");
    }

      @PostMapping("/sso/layout")
   public CommonResp layout() {
       // 增加登录状态校验
       if (StpUtil.isLogin()) {
           Long id = LoginMemberContext.getId();
           StpUtil.logout(id);
           return new CommonResp(HttpStatus.SUCCESS, null, "退出成功");
       } else {
           return new CommonResp(HttpStatus.UNAUTHORIZED, null, "请先登录");
       }
   }

}
