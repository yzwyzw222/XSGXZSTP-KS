package com.aacv.system.operations.infrastructure.quartz;

import com.aacv.system.operations.infrastructure.config.OperationsProperties;
import java.time.Instant;
import java.util.Date;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aacv.operations.alerts-enabled", havingValue = "true", matchIfMissing = true)
class AlertEvaluationQuartzSchedule implements ApplicationRunner {

    private static final String GROUP = "aacv-operations";
    private final Scheduler scheduler;
    private final OperationsProperties properties;

    AlertEvaluationQuartzSchedule(Scheduler scheduler, OperationsProperties properties) {
        this.scheduler = scheduler;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.validate();
        int interval = properties.getAlertEvaluationIntervalSeconds();
        JobDetail job = JobBuilder.newJob(AlertEvaluationQuartzJob.class)
                .withIdentity("alert-evaluation", GROUP)
                .storeDurably()
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("alert-evaluation", GROUP)
                .forJob(job)
                .startAt(Date.from(Instant.now().plusSeconds(interval)))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(interval)
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithExistingCount())
                .build();
        try {
            scheduler.addJob(job, true, true);
            if (scheduler.checkExists(trigger.getKey())) {
                scheduler.rescheduleJob(trigger.getKey(), trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("系统内告警调度注册失败", exception);
        }
    }
}
