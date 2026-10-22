package com.guo.business.config;

/**
 * @Description
 * @Author: 派
 * @Date: 2024/12/11
 */

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@SpringBootApplication
@ComponentScan("com.guo")
@MapperScan("com.guo.*.mapper")
@EnableFeignClients("com.guo.business.feign")
@EnableCaching
@EnableScheduling
public class BusinessApplication {
    private static final Logger LOG = Logger.getLogger(BusinessApplication.class.getName());
    public static void main(String[] args) {
        SpringApplication app =new SpringApplication(BusinessApplication.class);
        Environment env = app.run(args).getEnvironment();
        LOG.info("启动成功");
        LOG.info("地址：\t 127.0.0.1:"+env.getProperty("server.port"));
        //限流规则
//        initFlowRules();
//        LOG.info("限流规则：\t 127.0.0.1:"+env.getProperty("server.port"));
    }

    private static void initFlowRules(){
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        // 对DoStorage接口进行限流
        rule.setResource("DoStorage");
        //设置规则的等级为QPS（每秒查询率）
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // Set limit QPS to 20.
        // 设置限流阈值为20
        rule.setCount(20);
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }
}