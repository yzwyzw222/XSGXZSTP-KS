package com.aacv.system.crawl.infrastructure.quartz;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

/** 兼容历史 Quartz 持久化类名，清理旧触发器后不再发起采集。 */
public class QuartzCrawlTriggerJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            context.getScheduler().deleteJob(context.getJobDetail().getKey());
        } catch (SchedulerException exception) {
            throw new JobExecutionException("无法清理已停用的采集计划", exception, false);
        }
    }
}
