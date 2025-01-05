package com.guo.business.config;

import com.guo.common.interceptor.MemberInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/5
 */
public class SpringMvcConfig implements WebMvcConfigurer {
    @Resource
    private MemberInterceptor memberInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(memberInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/business/hello")
                .order(1);

    }
}
