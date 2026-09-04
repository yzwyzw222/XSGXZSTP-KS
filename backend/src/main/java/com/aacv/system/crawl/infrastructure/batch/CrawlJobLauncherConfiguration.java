package com.aacv.system.crawl.infrastructure.batch;

import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class CrawlJobLauncherConfiguration {

    @Bean
    JobRegistry jobRegistry() {
        return new MapJobRegistry();
    }

    @Bean
    ThreadPoolTaskExecutor crawlJobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("aacv-crawl-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("crawlJobOperator")
    JobOperator crawlJobOperator(
            JobRepository jobRepository,
            JobRegistry jobRegistry,
            ThreadPoolTaskExecutor crawlJobTaskExecutor) throws Exception {
        TaskExecutorJobOperator operator = new TaskExecutorJobOperator();
        operator.setJobRepository(jobRepository);
        operator.setJobRegistry(jobRegistry);
        operator.setTaskExecutor(crawlJobTaskExecutor);
        operator.afterPropertiesSet();
        return operator;
    }
}
