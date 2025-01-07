package com.guo.member.config;

import com.guo.common.interceptor.MemberInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/21
 */
@Configuration
public class SpringMvcConfig implements WebMvcConfigurer {
    @Resource
    private MemberInterceptor memberInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(memberInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/member/SendCode","/member/LoginCode","/member/register","member/ticket")
                .order(1);

    }
}
