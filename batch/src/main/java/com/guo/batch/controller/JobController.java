package com.guo.batch.controller;

import com.guo.batch.req.CronJobReq;
import com.guo.batch.resp.CronJobResp;
import com.guo.common.resp.CommonResp;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.triggers.CronTriggerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Tag(name = "定时任务", description = "Quartz 任务调度管理（新增/暂停/恢复/查询）")
@RestController
@RequestMapping(value = "/job")
public class JobController {

    private static final Logger LOG = LoggerFactory.getLogger(JobController.class);
    private static final String JOB_PACKAGE = "com.guo.batch.job.";
    private static final String DAILY_TRAIN_JOB = JOB_PACKAGE + "DailyTrainJob";
    private static final String TEST_JOB = JOB_PACKAGE + "testJob";

    @Autowired
    private SchedulerFactoryBean schedulerFactoryBean;

    @RequestMapping(value = "/run")
    public CommonResp<Object> run(@RequestBody CronJobReq cronJobReq) throws SchedulerException {
        String jobName = cronJobReq.getName();
        String jobGroupName = cronJobReq.getGroup();
        LOG.info("manual run start: {}, {}", jobName, jobGroupName);
        schedulerFactoryBean.getScheduler().triggerJob(JobKey.jobKey(jobName, jobGroupName));
        return new CommonResp<>();
    }

    @RequestMapping(value = "/add")
    public CommonResp add(@RequestBody CronJobReq cronJobReq) {
        String rawJobName = cronJobReq.getName();
        String jobGroupName = cronJobReq.getGroup();
        String cronExpression = cronJobReq.getCronExpression();
        String description = cronJobReq.getDescription();
        LOG.info("create job start: {}, {}, {}, {}", rawJobName, jobGroupName, cronExpression, description);
        CommonResp commonResp = new CommonResp();

        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            scheduler.start();

            Class<? extends Job> jobClass = resolveJobClass(rawJobName);
            String jobName = normalizeJobName(rawJobName, jobClass);

            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                    .withIdentity(jobName, jobGroupName)
                    .build();
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression);
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobName, jobGroupName)
                    .withDescription(description)
                    .withSchedule(scheduleBuilder)
                    .build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            LOG.error("create job failed", e);
            commonResp.setSuccess(false);
            commonResp.setMessage("创建定时任务失败: 调度异常");
        } catch (ClassNotFoundException e) {
            LOG.error("create job failed", e);
            commonResp.setSuccess(false);
            commonResp.setMessage("创建定时任务失败：任务类不存在");
        }

        LOG.info("create job end: {}", commonResp);
        return commonResp;
    }

    @RequestMapping(value = "/pause")
    public CommonResp pause(@RequestBody CronJobReq cronJobReq) {
        String jobName = cronJobReq.getName();
        String jobGroupName = cronJobReq.getGroup();
        LOG.info("pause job start: {}, {}", jobName, jobGroupName);
        CommonResp commonResp = new CommonResp();
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            scheduler.pauseJob(JobKey.jobKey(jobName, jobGroupName));
        } catch (SchedulerException e) {
            LOG.error("pause job failed", e);
            commonResp.setSuccess(false);
            commonResp.setMessage("暂停定时任务失败: 调度异常");
        }
        LOG.info("pause job end: {}", commonResp);
        return commonResp;
    }

    @RequestMapping(value = "/resume")
    public CommonResp resume(@RequestBody CronJobReq cronJobReq) {
        String jobName = cronJobReq.getName();
        String jobGroupName = cronJobReq.getGroup();
        LOG.info("resume job start: {}, {}", jobName, jobGroupName);
        CommonResp commonResp = new CommonResp();
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            scheduler.resumeJob(JobKey.jobKey(jobName, jobGroupName));
        } catch (SchedulerException e) {
            LOG.error("resume job failed", e);
            commonResp.setSuccess(false);
            commonResp.setMessage("重启定时任务失败: 调度异常");
        }
        LOG.info("resume job end: {}", commonResp);
        return commonResp;
    }

    @RequestMapping(value = "/reschedule")
    public CommonResp reschedule(@RequestBody CronJobReq cronJobReq) {
        String jobName = cronJobReq.getName();
        String jobGroupName = cronJobReq.getGroup();
        String cronExpression = cronJobReq.getCronExpression();
        String description = cronJobReq.getDescription();
        LOG.info("reschedule job start: {}, {}, {}, {}", jobName, jobGroupName, cronExpression, description);
        CommonResp commonResp = new CommonResp();
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroupName);
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression);
            CronTriggerImpl currentTrigger = (CronTriggerImpl) scheduler.getTrigger(triggerKey);
            currentTrigger.setStartTime(new Date());
            CronTrigger trigger = currentTrigger.getTriggerBuilder()
                    .withIdentity(triggerKey)
                    .withDescription(description)
                    .withSchedule(scheduleBuilder)
                    .build();
            scheduler.rescheduleJob(triggerKey, trigger);
        } catch (Exception e) {
            LOG.error("reschedule job failed", e);
            commonResp.setSuccess(false);
            commonResp.setMessage("更新定时任务失败: 调度异常");
        }
        LOG.info("reschedule job end: {}", commonResp);
        return commonResp;
    }

    @RequestMapping(value = "/delete")
    public CommonResp delete(@RequestBody CronJobReq cronJobReq) {
        String jobName = cronJobReq.getName();
        String jobGroupName = cronJobReq.getGroup();
        LOG.info("delete job start: {}, {}", jobName, jobGroupName);
        CommonResp commonResp = new CommonResp();
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            scheduler.pauseTrigger(TriggerKey.triggerKey(jobName, jobGroupName));
            scheduler.unscheduleJob(TriggerKey.triggerKey(jobName, jobGroupName));
            scheduler.deleteJob(JobKey.jobKey(jobName, jobGroupName));
        } catch (SchedulerException e) {
            LOG.error("delete job failed", e);
            commonResp.setSuccess(false);
            commonResp.setMessage("删除定时任务失败: 调度异常");
        }
        LOG.info("delete job end: {}", commonResp);
        return commonResp;
    }

    @RequestMapping(value = "/query")
    public CommonResp query() {
        LOG.info("query jobs start");
        CommonResp commonResp = new CommonResp();
        List<CronJobResp> cronJobRespList = new ArrayList<>();
        try {
            Scheduler scheduler = schedulerFactoryBean.getScheduler();
            for (String groupName : scheduler.getJobGroupNames()) {
                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                    CronJobResp cronJobResp = new CronJobResp();
                    cronJobResp.setName(jobKey.getName());
                    cronJobResp.setGroup(jobKey.getGroup());

                    List<Trigger> triggers = (List<Trigger>) scheduler.getTriggersOfJob(jobKey);
                    CronTrigger cronTrigger = (CronTrigger) triggers.get(0);
                    cronJobResp.setNextFireTime(cronTrigger.getNextFireTime());
                    cronJobResp.setPreFireTime(cronTrigger.getPreviousFireTime());
                    cronJobResp.setCronExpression(cronTrigger.getCronExpression());
                    cronJobResp.setDescription(cronTrigger.getDescription());
                    cronJobResp.setState(scheduler.getTriggerState(cronTrigger.getKey()).name());
                    cronJobRespList.add(cronJobResp);
                }
            }
        } catch (SchedulerException e) {
            LOG.error("query jobs failed", e);
            commonResp.setSuccess(false);
            commonResp.setMessage("查看定时任务失败: 调度异常");
        }
        commonResp.setContent(cronJobRespList);
        LOG.info("query jobs end: {}", commonResp);
        return commonResp;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Job> resolveJobClass(String rawJobName) throws ClassNotFoundException {
        ClassNotFoundException lastException = null;
        for (String candidate : buildJobClassCandidates(rawJobName)) {
            try {
                Class<?> jobClass = Class.forName(candidate);
                if (Job.class.isAssignableFrom(jobClass)) {
                    return (Class<? extends Job>) jobClass;
                }
            } catch (ClassNotFoundException e) {
                lastException = e;
            }
        }
        throw lastException == null ? new ClassNotFoundException(rawJobName) : new ClassNotFoundException(rawJobName, lastException);
    }

    private List<String> buildJobClassCandidates(String rawJobName) {
        String jobName = rawJobName == null ? "" : rawJobName.trim();
        Set<String> candidates = new LinkedHashSet<>();
        String aliasTarget = resolveAlias(jobName);
        if (aliasTarget != null) {
            candidates.add(aliasTarget);
        }
        if (!jobName.isEmpty()) {
            if (jobName.contains(".")) {
                candidates.add(jobName);
            } else {
                candidates.add(JOB_PACKAGE + jobName);
                candidates.add(jobName);
            }
        }
        return new ArrayList<>(candidates);
    }

    private String resolveAlias(String jobName) {
        String normalized = jobName.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "1":
            case "dailytrainjob":
            case "dailytrain":
            case "daily-train":
            case "daily_train":
                return DAILY_TRAIN_JOB;
            case "2":
            case "testjob":
            case "test":
            case "test-job":
            case "test_job":
                return TEST_JOB;
            default:
                return null;
        }
    }

    private String normalizeJobName(String rawJobName, Class<? extends Job> jobClass) {
        String jobName = rawJobName == null ? "" : rawJobName.trim();
        if (jobName.isEmpty() || jobName.contains(".") || resolveAlias(jobName) != null) {
            return jobClass.getSimpleName();
        }
        return jobName;
    }
}
