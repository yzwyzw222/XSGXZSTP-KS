package com.aacv.system.crawl.infrastructure.quartz;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class QuartzQuotaResumeConfiguration {

    @Bean
    JobDetail crawlQuotaResumeJob() {
        return JobBuilder.newJob(QuartzQuotaResumeJob.class)
                .withIdentity("quota-resume", "aacv-crawl").storeDurably().build();
    }

    @Bean
    Trigger crawlQuotaResumeTrigger(@Qualifier("crawlQuotaResumeJob") JobDetail job) {
        return TriggerBuilder.newTrigger().withIdentity("quota-resume", "aacv-crawl").forJob(job)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(60)
                        .repeatForever().withMisfireHandlingInstructionNextWithRemainingCount())
                .build();
    }
}
