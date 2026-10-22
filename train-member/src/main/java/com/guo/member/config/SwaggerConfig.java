package com.guo.member.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Description Knife4j(OpenAPI3) 文档配置
 * @Author: 派
 * @Date: 2026/06/04
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI memberOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("train-demo · train-member 会员服务 API")
                        .description("会员登录注册/乘客管理/车票查询 接口")
                        .version("v1.0")
                        .contact(new Contact().name("train-demo")));
    }
}
