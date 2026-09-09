package com.xsyu.academicgraph.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务配置：LLM 抽取专用线程池。
 * LLM 调用一次几十秒，抽取必须异步（HTTP 请求不能挂着等）；池子刻意调小——
 * 并发太高既烧 API 配额又容易触发限流，2 个核 + 4 上限足够课程演示规模。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 抽取执行器：@Async("extractionExecutor") 引用 */
    @Bean(name = "extractionExecutor")
    public ThreadPoolTaskExecutor extractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("extraction-");
        // CallerRunsPolicy：队列满了就让触发线程自己跑，不丢任务（也不会无限堆积）
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
