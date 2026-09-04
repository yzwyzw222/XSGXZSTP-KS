package com.aacv.system.ingestion.infrastructure.retention;

import java.util.TimeZone;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
class RawPayloadRetentionScheduler implements ApplicationRunner, Ordered {

    private static final String GROUP = "aacv-maintenance";
    private static final JobKey JOB_KEY = JobKey.jobKey("raw-payload-retention", GROUP);
    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("raw-payload-retention-daily", GROUP);

    private final Scheduler scheduler;

    RawPayloadRetentionScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        var job = JobBuilder.newJob(RawPayloadRetentionJob.class)
                .withIdentity(JOB_KEY)
                .storeDurably()
                .build();
        var trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_KEY)
                .forJob(JOB_KEY)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 15 3 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("UTC"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
        try {
            scheduler.addJob(job, true, true);
            if (scheduler.checkExists(TRIGGER_KEY)) {
                scheduler.rescheduleJob(TRIGGER_KEY, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("原始Payload保留清理计划同步失败", exception);
        }
    }
}
