package com.guo.batch.job;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/2
 */

import cn.hutool.core.util.RandomUtil;
import com.guo.batch.controller.JobController;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class DailyTrainJob implements Job {
    private static Logger LOG = LoggerFactory.getLogger(JobController.class);
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        MDC.put("LOG_id",System.currentTimeMillis()+ RandomUtil.randomString(3));
        LOG.info("生成每日车次数据开始");



        LOG.info("生成每日车次数据结束");
    }
}
