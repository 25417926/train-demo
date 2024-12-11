package com.guo.gateway.config;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/11
 */
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import java.util.logging.Logger;

@SpringBootApplication
@ComponentScan("com.guo")
public class GateWayApplication {
    private static final Logger LOG = Logger.getLogger(GateWayApplication.class.getName());
    public static void main(String[] args) {
        SpringApplication app =new SpringApplication(GateWayApplication.class);
        Environment env = app.run(args).getEnvironment();
        LOG.info("启动成功");
        LOG.info("地址：\t 127.0.0.1:"+env.getProperty("server.port"));
    }

}