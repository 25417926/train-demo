package com.guo.batch.job;

/**
 * @Description
 * @Author: 派
 * @Date: 2025/1/2
 */

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class testJob implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        System.out.println("testJob");
    }
}
