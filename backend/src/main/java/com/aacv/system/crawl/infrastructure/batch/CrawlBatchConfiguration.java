package com.aacv.system.crawl.infrastructure.batch;

import com.aacv.system.crawl.application.CrawlRunService;
import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.ingestion.application.IngestionPageService;
import com.aacv.system.ingestion.application.IngestionPageResult;
import com.aacv.system.ingestion.application.port.IngestionRepository;
import com.aacv.system.source.application.DataSourceAdapterRegistry;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.SourcePage;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.batch.core.configuration.annotation.StepScope;

@Configuration
class CrawlBatchConfiguration {

    @Bean
    @StepScope
    OpenAlexPageItemReader sourcePageItemReader(
            @Value("#{jobParameters['runId']}") Long runId,
            CrawlRepository crawlRepository,
            DataSourceRepository sourceRepository,
            DataSourceAdapterRegistry adapterRegistry,
            IngestionRepository ingestionRepository) {
        if (runId == null) {
            throw new IllegalArgumentException("Batch Job参数缺少runId");
        }
        return new OpenAlexPageItemReader(
                runId, crawlRepository, sourceRepository, adapterRegistry, ingestionRepository);
    }

    @Bean
    ItemWriter<CrawlPageItem> sourcePageItemWriter(
            IngestionPageService ingestionPageService,
            CrawlRepository crawlRepository,
            IngestionRepository ingestionRepository,
            DataSourceRepository sourceRepository) {
        return chunk -> {
            for (CrawlPageItem item : chunk) {
                Long runId = org.springframework.batch.core.scope.context.StepSynchronizationManager
                        .getContext()
                        .getStepExecution()
                        .getJobParameters()
                        .getLong("runId");
                if (runId == null) {
                    throw new IllegalStateException("Batch写入阶段缺少业务运行ID");
                }
                long taskId = crawlRepository.findRunById(runId).orElseThrow().taskId();
                long sourceId = crawlRepository.findTaskById(taskId).orElseThrow().sourceId();
                com.aacv.system.source.domain.SourceType sourceType = sourceRepository
                        .findById(sourceId)
                        .orElseThrow()
                        .sourceType();
                IngestionPageResult result = ingestionPageService.processPage(
                        sourceType, sourceId, runId, item.page());
                if (item.retryFailureId() != null) {
                    ingestionRepository.recordRetryAttempt(item.retryFailureId(), result.failureCount() == 0);
                }
            }
        };
    }

    @Bean
    Step sourcePageStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            OpenAlexPageItemReader reader,
            @Qualifier("sourcePageItemWriter") ItemWriter<CrawlPageItem> writer) {
        return new StepBuilder("sourcePageStep", jobRepository)
                .<CrawlPageItem, CrawlPageItem>chunk(1)
                .transactionManager(transactionManager)
                .reader(reader)
                .writer(writer)
                .build();
    }

    @Bean
    Job sourceIngestionJob(
            JobRepository jobRepository,
            @Qualifier("sourcePageStep") Step step,
            ObjectProvider<CrawlRunService> crawlRunServiceProvider) {
        return new JobBuilder("sourceIngestionJob", jobRepository)
                .listener(new CrawlJobExecutionListener(crawlRunServiceProvider))
                .start(step)
                .build();
    }

}
