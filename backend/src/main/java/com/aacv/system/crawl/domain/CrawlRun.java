package com.aacv.system.crawl.domain;

import java.time.Instant;

public record CrawlRun(
        long id,
        long taskId,
        String runNumber,
        CrawlTriggerType triggerType,
        Long parentRunId,
        CrawlRunStatus status,
        Long batchJobExecutionId,
        long readCount,
        long parsedCount,
        long createdCount,
        long updatedCount,
        long duplicateCount,
        long failureCount,
        long requestCount,
        String checkpoint,
        Instant startedAt,
        Instant finishedAt,
        long version) {

    public CrawlRun {
        if (taskId < 1 || runNumber == null || runNumber.isBlank()
                || triggerType == null || status == null || version < 0) {
            throw new IllegalArgumentException("采集运行状态无效");
        }
        if ((triggerType == CrawlTriggerType.RETRY_FAILURES) != (parentRunId != null)) {
            throw new IllegalArgumentException("失败重试运行与父运行关联不一致");
        }
    }
}
