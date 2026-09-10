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
                runId(jobExecution), jobExecution.getStatus() == BatchStatus.COMPLETED, quotaResumeAt(jobExecution),
                executionFailure(jobExecution));
    }

    private com.aacv.system.crawl.domain.CrawlExecutionFailure executionFailure(JobExecution execution) {
        if (execution.getStatus() == BatchStatus.COMPLETED || quotaResumeAt(execution) != null) return null;
        for (Throwable failure : execution.getAllFailureExceptions()) {
            for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
                if (failure instanceof com.aacv.system.source.application.SourceClientException source) {
                    String category = source.category() != null && source.category().matches("[A-Z0-9_]{1,50}")
                            ? source.category() : "UNKNOWN";
                    String status = source.statusCode() == null ? "" : "（HTTP " + source.statusCode() + "）";
                    return new com.aacv.system.crawl.domain.CrawlExecutionFailure(
                            category.contains("PARSE") ? "PARSE" : "FETCH", "SOURCE_" + category,
                            "来源请求或响应处理失败" + status + "，请核对来源配置与服务状态，再从检查点重试。");
                }
                if (failure instanceof org.springframework.dao.DataAccessException) {
                    return new com.aacv.system.crawl.domain.CrawlExecutionFailure("PERSIST", "STORAGE_FAILURE",
                            "本页数据库事务失败，未提交本页数据，请检查数据库后从检查点重试。");
                }
                if (failure instanceof IllegalArgumentException) {
                    return new com.aacv.system.crawl.domain.CrawlExecutionFailure("VALIDATE", "INVALID_INPUT",
                            "任务参数或来源响应校验失败，请检查采集范围和来源适配规则。");
                }
            }
        }
        return new com.aacv.system.crawl.domain.CrawlExecutionFailure("SYSTEM", "EXECUTION_FAILURE",
                "采集执行失败，已保留已提交数据，请按运行编号检查后台日志后从检查点重试。");
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
