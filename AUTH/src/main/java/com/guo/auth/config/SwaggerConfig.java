package com.guo.auth.config;

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
    public OpenAPI authOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("train-demo · auth 认证服务 API")
                        .description("用户/角色/权限/登录鉴权 接口")
                        .version("v1.0")
                        .contact(new Contact().name("train-demo")));
    }
}
