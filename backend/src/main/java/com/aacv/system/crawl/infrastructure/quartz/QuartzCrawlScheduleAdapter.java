package com.aacv.system.crawl.infrastructure.quartz;

import com.aacv.system.crawl.application.port.CrawlSchedulePort;
import com.aacv.system.crawl.domain.CrawlSchedule;
import java.util.TimeZone;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class QuartzCrawlScheduleAdapter implements CrawlSchedulePort {

    private static final String GROUP = "aacv-crawl";

    private final Scheduler scheduler;

    QuartzCrawlScheduleAdapter(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void synchronizeAfterCommit(CrawlSchedule schedule) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    synchronize(schedule);
                }
            });
        } else {
            synchronize(schedule);
        }
    }

    private void synchronize(CrawlSchedule schedule) {
        JobKey jobKey = JobKey.jobKey(schedule.scheduleKey(), GROUP);
        TriggerKey triggerKey = TriggerKey.triggerKey(schedule.scheduleKey(), GROUP);
        JobDetail job = JobBuilder.newJob(QuartzCrawlTriggerJob.class)
                .withIdentity(jobKey)
                .usingJobData("taskId", schedule.taskId())
                .storeDurably()
                .build();
        String cron = "0 " + schedule.localTime().getMinute() + " " + schedule.localTime().getHour() + " * * ?";
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(TimeZone.getTimeZone(schedule.timeZone()))
                        .withMisfireHandlingInstructionDoNothing())
                .startAt(java.util.Date.from(schedule.nextFireAt()))
                .build();
        try {
            scheduler.addJob(job, true, true);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("Quartz每日采集计划同步失败", exception);
        }
    }
}
