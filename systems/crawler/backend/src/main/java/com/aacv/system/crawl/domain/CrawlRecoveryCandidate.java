package com.aacv.system.crawl.domain;

public record CrawlRecoveryCandidate(
        long runId,
        CrawlRunStatus businessStatus,
        Long batchJobExecutionId,
        String batchStatus,
        boolean checkpointPresent) {
}
