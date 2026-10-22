package com.guo.batch.config;

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
    public OpenAPI batchOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("train-demo · batch 跑批服务 API")
                        .description("Quartz 定时任务管理接口")
                        .version("v1.0")
                        .contact(new Contact().name("train-demo")));
    }
}
