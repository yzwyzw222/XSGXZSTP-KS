package com.aacv.system.crawl.api;

import com.aacv.system.crawl.domain.CrawlFailure;
import com.aacv.system.shared.domain.PageResult;
import java.time.Instant;
import java.util.List;

public record CrawlFailurePageResponse(
        List<CrawlFailureResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static CrawlFailurePageResponse from(PageResult<CrawlFailure> result) {
        return new CrawlFailurePageResponse(
                result.items().stream().map(CrawlFailureResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    public record CrawlFailureResponse(
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
        static CrawlFailureResponse from(CrawlFailure failure) {
            return new CrawlFailureResponse(
                    failure.id(), failure.runId(), failure.rawRecordId(), failure.externalRecordId(),
                    failure.failureStage(), failure.errorCategory(), failure.safeMessage(), failure.retryable(),
                    failure.attemptCount(), failure.resolved(), failure.evidenceHash(),
                    failure.createdAt(), failure.updatedAt());
        }
    }
}
