package com.guo.business.config;

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
    public OpenAPI businessOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("train-demo · business 业务服务 API")
                        .description("车次/车厢/座位/余票/订单/支付/退款/退改签 接口")
                        .version("v1.0")
                        .contact(new Contact().name("train-demo")));
    }
}
