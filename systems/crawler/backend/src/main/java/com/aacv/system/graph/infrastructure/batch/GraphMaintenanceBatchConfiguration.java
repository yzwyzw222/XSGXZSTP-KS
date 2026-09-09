package com.aacv.system.graph.infrastructure.batch;

import com.aacv.system.graph.application.GraphMaintenanceService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class GraphMaintenanceBatchConfiguration {

    @Bean
    Step graphMaintenanceStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ObjectProvider<GraphMaintenanceService> serviceProvider) {
        return new StepBuilder("graphMaintenanceStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Long runId = org.springframework.batch.core.scope.context.StepSynchronizationManager
                            .getContext().getStepExecution().getJobParameters().getLong("runId");
                    if (runId == null) {
                        throw new IllegalArgumentException("图维护Batch参数缺少runId");
                    }
                    serviceProvider.getObject().execute(runId);
                    return RepeatStatus.FINISHED;
                })
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    Job graphMaintenanceJob(
            JobRepository jobRepository,
            @Qualifier("graphMaintenanceStep") Step step) {
        return new JobBuilder("graphMaintenanceJob", jobRepository).start(step).build();
    }
}
