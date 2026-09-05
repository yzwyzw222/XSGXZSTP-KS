package com.aacv.system.crawl.infrastructure.batch;

import com.aacv.system.crawl.application.CrawlRunService;
import com.aacv.system.crawl.application.port.CrawlRunLaunchPort;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class SpringBatchCrawlRunLauncher implements CrawlRunLaunchPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringBatchCrawlRunLauncher.class);

    private final JobOperator jobOperator;
    private final Job job;
    private final ObjectProvider<CrawlRunService> crawlRunServiceProvider;
    private final TransactionTemplate withoutTransaction;

    SpringBatchCrawlRunLauncher(
            @Qualifier("crawlJobOperator") JobOperator jobOperator,
            @Qualifier("sourceIngestionJob") Job job,
            ObjectProvider<CrawlRunService> crawlRunServiceProvider,
            PlatformTransactionManager transactionManager) {
        this.jobOperator = jobOperator;
        this.job = job;
        this.crawlRunServiceProvider = crawlRunServiceProvider;
        this.withoutTransaction = new TransactionTemplate(transactionManager);
        this.withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Override
    public void launchAfterCommit(long runId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    launch(runId);
                }
            });
        } else {
            launch(runId);
        }
    }

    private void launch(long runId) {
        // afterCommit期间原事务资源尚未解绑；先挂起它，让Batch独立创建元数据事务。
        withoutTransaction.executeWithoutResult(status -> startJob(runId));
    }

    private void startJob(long runId) {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("runId", runId)
                .addLong("launchEpoch", System.currentTimeMillis())
                .toJobParameters();
        try {
            jobOperator.start(job, parameters);
        } catch (Exception exception) {
            LOGGER.error(
                    "Spring Batch采集运行启动失败，runId={}，异常类型={}",
                    runId,
                    exception.getClass().getSimpleName());
            crawlRunServiceProvider.getObject().failLaunch(runId);
        }
    }
}
