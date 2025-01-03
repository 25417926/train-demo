package com.guo.batch.job;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/2
 */

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.guo.batch.controller.JobController;
import com.guo.batch.feign.BusinessFeign;
import com.guo.common.resp.CommonResp;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.annotation.Resource;
import java.util.Date;

public class DailyTrainJob implements Job {
    private static Logger LOG = LoggerFactory.getLogger(JobController.class);
    @Resource
    private BusinessFeign businessFeign;
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        MDC.put("LOG_id",System.currentTimeMillis()+ RandomUtil.randomString(3));
        LOG.info("生成15日后车次数据开始");
        Date date = new Date();
        DateTime dateTime = DateUtil.offsetDay(date, 15);
        Date jdkDate = dateTime.toJdkDate();
        CommonResp commonResp = businessFeign.gen_daily(jdkDate);
        if (commonResp.getSuccess()){
            LOG.info("生成每日车次数据成功{}", commonResp);
        }else {
            LOG.info("生成每日车次数据失败");
        }
        LOG.info("生成每日车次数据结束");
    }
}
