package com.guo.batch.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.annotation.Resource;

/**
 * 自定义Job工厂类，用于创建和初始化Quartz任务
 * 继承自SpringBeanJobFactory，以实现自动装配功能
 */
public class MyJobFactory extends SpringBeanJobFactory {

    //自动装配Spring的AutowireCapableBeanFactory，用于注入依赖
    @Resource
    private AutowireCapableBeanFactory autowireCapableBeanFactory;

    /**
     * 创建Job实例并进行自动装配
     *
     * @param bundle 包含触发器、作业详细信息等的捆绑对象
     * @return 返回创建并自动装配后的Job实例
     * @throws Exception 如果创建Job实例过程中出现错误，则抛出异常
     */
    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        //调用父类的方法创建Job实例
        Object jobInstance = super.createJobInstance(bundle);
        //进行注入
        autowireCapableBeanFactory.autowireBean(jobInstance);
        return jobInstance;
    }
}
