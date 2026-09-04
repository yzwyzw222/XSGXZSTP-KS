package com.aacv.system.crawl.domain;

import java.time.Instant;

public record CrawlFailure(
        long id,
        long runId,
        Long rawRecordId,
        String externalRecordId,
        String failureStage,
        String errorCategory,
        String safeMessage,
        boolean retryable,
        int attemptCount,
        boolean resolved,
        String evidenceHash,
        Instant createdAt,
        Instant updatedAt) {
}
