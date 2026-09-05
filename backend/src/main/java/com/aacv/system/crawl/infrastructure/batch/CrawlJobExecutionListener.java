package com.aacv.system.crawl.infrastructure.batch;

import com.aacv.system.crawl.application.CrawlRunService;
import com.aacv.system.source.application.SourceQuotaExhaustedException;
import java.time.Instant;
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
                runId(jobExecution), jobExecution.getStatus() == BatchStatus.COMPLETED, quotaResumeAt(jobExecution));
    }

    private Instant quotaResumeAt(JobExecution execution) {
        for (Throwable failure : execution.getAllFailureExceptions()) {
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                if (cause instanceof SourceQuotaExhaustedException quota) {
                    return quota.resumeAt();
                }
            }
        }
        return null;
    }

    private long runId(JobExecution jobExecution) {
        Long runId = jobExecution.getJobParameters().getLong("runId");
        if (runId == null || runId < 1) {
            throw new IllegalStateException("Batch执行缺少业务运行ID");
        }
        return runId;
    }
}
