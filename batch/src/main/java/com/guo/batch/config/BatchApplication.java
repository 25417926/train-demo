package com.guo.batch.config;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/11
 */
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import java.util.logging.Logger;

@SpringBootApplication
@ComponentScan("com.guo")
@MapperScan("com.guo.*.mapper")
public class BatchApplication {
    private static final Logger LOG = Logger.getLogger(BatchApplication.class.getName());
    public static void main(String[] args) {
        SpringApplication app =new SpringApplication(BatchApplication.class);
        Environment env = app.run(args).getEnvironment();
        LOG.info("启动成功");
        LOG.info("地址：\t 127.0.0.1:"+env.getProperty("server.port"));
    }

}