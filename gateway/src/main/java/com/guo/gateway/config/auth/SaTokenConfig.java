package com.guo.gateway.config.auth;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaTokenConfig {
    // 注册 Sa-Token全局过滤器
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")    /* 拦截全部path */
                // 开放地址
                .addExclude("/favicon.ico")
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {

                    // 放行所有 OPTIONS 预检请求，交给 Spring Cloud Gateway 的 globalcors 处理 CORS 头
                    SaRouter.match(r -> "OPTIONS".equalsIgnoreCase(
                            SaHolder.getRequest().getMethod())).stop();

                    // 内部 Feign 调用接口禁止外部访问（** 只能放在末尾，按模块分别声明）
                    SaRouter.match("/member/internal/**").back("internal api forbidden");
                    SaRouter.match("/business/internal/**").back("internal api forbidden");
                    SaRouter.match("/batch/internal/**").back("internal api forbidden");
                    SaRouter.match("/auth/internal/**").back("internal api forbidden");

                    // 登录校验 -- 拦截所有路由，并排除 /auth/sso/** 用于开放登录
                    SaRouter.match("/**", "/auth/sso/**", r -> StpUtil.checkLogin());

                    // 权限认证 -- 不同模块, 校验不同权限
                    // /member/**       会员中心：user 或 admin 都可访问
                    // /business/web/** 会员侧业务（车次查询、下单、支付、退款、改签等）：user 或 admin 都可访问
                    // /business/**     其余视为后台管理：仅 admin 可访问
                    SaRouter.match("/member/**", r -> StpUtil.checkRoleOr("user", "admin"));
                    SaRouter.match("/business/web/**", r -> StpUtil.checkRoleOr("user", "admin"));
                    SaRouter.match("/business/**", "/business/web/**", r -> StpUtil.checkRole("admin"));



                    // 更多匹配 ...  */
                })
                // 异常处理方法：每次setAuth函数出现异常时进入
                .setError(e -> {
                    // return SaResult.error(e.getMessage());
                    // 手动抛出异常，抛给全局异常处理器
                    if (e instanceof NotLoginException) { // 未登录异常
                        throw new NotLoginException(e.getMessage(), null, null);
                    } else if (e instanceof NotPermissionException || e instanceof NotRoleException) { // 权限不足，或不具备角色，统一抛出权限不足异常
                        throw new NotPermissionException(e.getMessage());
                    } else { // 其他异常，则抛出一个运行时异常
                        throw new RuntimeException(e.getMessage());
                    }
                })
                ;
    }
}