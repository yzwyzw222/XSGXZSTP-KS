package com.aacv.system.crawl.api;

import java.time.Instant;

public record CrawlRunResponse(
        long id,
        long taskId,
        String runNumber,
        String triggerType,
        Long parentRunId,
        String status,
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
        Instant finishedAt) {
}
