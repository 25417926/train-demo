package com.guo.batch.job;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.annotation.Resource;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/2
 */
public class MyJobFactory extends SpringBeanJobFactory {

    @Resource
    private AutowireCapableBeanFactory autowireCapableBeanFactory;

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        //调用父类的方法
        Object jobInstance = super.createJobInstance(bundle);
        //进行注入
        autowireCapableBeanFactory.autowireBean(jobInstance);
        return jobInstance;
    }
}
