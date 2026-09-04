package com.aacv.system.crawl.infrastructure.batch;

import com.aacv.system.crawl.application.CrawlRunService;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.ObjectProvider;

class CrawlJobExecutionListener implements JobExecutionListener {

    private final ObjectProvider<CrawlRunService> crawlRunServiceProvider;

    CrawlJobExecutionListener(ObjectProvider<CrawlRunService> crawlRunServiceProvider) {
        this.crawlRunServiceProvider = crawlRunServiceProvider;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        crawlRunServiceProvider.getObject().markBatchStarted(runId(jobExecution), jobExecution.getId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        crawlRunServiceProvider.getObject().completeBatch(
                runId(jobExecution), jobExecution.getStatus() == BatchStatus.COMPLETED);
    }

    private long runId(JobExecution jobExecution) {
        Long runId = jobExecution.getJobParameters().getLong("runId");
        if (runId == null || runId < 1) {
            throw new IllegalStateException("Batch执行缺少业务运行ID");
        }
        return runId;
    }
}
