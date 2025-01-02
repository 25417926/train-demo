package com.guo.batch.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.annotation.Resource;
import javax.sql.DataSource;

/**
 * 配置调度任务相关设置
 *
 * @Author: 派
 * @Date: 2025/1/2
 */
public class ScheduleConfig {

    /**
     * 自定义的Job工厂，用于创建和管理调度任务中的Job实例
     */
    @Resource
    private MyJobFactory myJobFactory;

    /**
     * 创建并配置SchedulerFactoryBean实例
     *
     * @param dataSource 数据源，用于调度任务中与数据库的交互
     * @return SchedulerFactoryBean实例，用于管理和调度任务
     */
    public SchedulerFactoryBean SchedulerFactoryBean(@Qualifier("dataSource") DataSource dataSource) {
        // 配置调度工厂
        SchedulerFactoryBean schedulerFactoryBean = new SchedulerFactoryBean();
        // 设置数据源
        schedulerFactoryBean.setDataSource(dataSource);
        // 设置自定义的Job工厂
        schedulerFactoryBean.setJobFactory(myJobFactory);
        // 延迟2秒启动
        schedulerFactoryBean.setStartupDelay(2);
        return schedulerFactoryBean;
    }
}
