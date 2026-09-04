package com.aacv.system.crawl.infrastructure.quartz;

import com.aacv.system.crawl.application.CrawlTaskService;
import com.aacv.system.shared.application.ResourceConflictException;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class QuartzCrawlTriggerJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzCrawlTriggerJob.class);

    @Autowired
    private CrawlTaskService crawlTaskService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long taskId = context.getMergedJobDataMap().getLong("taskId");
        try {
            crawlTaskService.triggerScheduled(taskId);
        } catch (ResourceConflictException exception) {
            LOGGER.info("每日采集计划本次未创建运行，taskId={}，原因={}", taskId, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "每日采集计划触发失败，taskId={}，异常类型={}",
                    taskId,
                    exception.getClass().getSimpleName());
            throw new JobExecutionException("每日采集计划触发失败", exception, false);
        }
    }
}
