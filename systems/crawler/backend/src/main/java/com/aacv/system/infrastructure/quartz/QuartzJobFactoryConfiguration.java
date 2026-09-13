package com.aacv.system.infrastructure.quartz;

import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class QuartzJobFactoryConfiguration {

    @Bean
    SchedulerFactoryBeanCustomizer quartzJobFactoryCustomizer(
            AutowiringSpringBeanJobFactory jobFactory) {
        return schedulerFactoryBean -> schedulerFactoryBean.setJobFactory(jobFactory);
    }
}
