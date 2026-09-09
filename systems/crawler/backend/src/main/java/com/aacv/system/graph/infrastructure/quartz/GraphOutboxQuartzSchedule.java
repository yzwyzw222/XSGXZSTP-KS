package com.aacv.system.graph.infrastructure.quartz;

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
@ConditionalOnProperty(name = "aacv.graph.outbox.enabled", havingValue = "true", matchIfMissing = true)
class GraphOutboxQuartzSchedule implements ApplicationRunner {

    private static final String GROUP = "aacv-graph";
    private final Scheduler scheduler;

    GraphOutboxQuartzSchedule(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        JobDetail job = JobBuilder.newJob(GraphOutboxQuartzJob.class)
                .withIdentity("graph-outbox-consumer", GROUP)
                .storeDurably()
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("graph-outbox-consumer", GROUP)
                .forJob(job)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(10)
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
            throw new IllegalStateException("图投影Outbox调度注册失败", exception);
        }
    }
}
