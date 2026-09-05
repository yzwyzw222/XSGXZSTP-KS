package com.aacv.system.crawl.api;

import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;

public record CrawlTaskPageResponse(
        List<CrawlTaskResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static CrawlTaskPageResponse from(PageResult<CrawlTask> result) {
        return new CrawlTaskPageResponse(
                result.items().stream().map(CrawlTaskPageResponse::toTaskResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    static CrawlTaskResponse toTaskResponse(CrawlTask task) {
        CrawlScope scope = task.scope();
        return new CrawlTaskResponse(
                task.id(), task.sourceId(), task.name(),
                task.parameterVersion(),
                new CrawlTaskParametersRequest(
                        scope.publicationDateFrom(), scope.publicationDateTo(), scope.keyword(),
                        scope.authorIds(), scope.institutionIds(), scope.dois(), scope.orcids(), scope.rorIds(),
                        scope.updatedFrom(), scope.updatedUntil(), scope.maxPages(), scope.maxRecords()),
                task.enabled(), task.version(), task.createdAt(), task.updatedAt());
    }

    static CrawlRunResponse toRunResponse(CrawlRun run) {
        return new CrawlRunResponse(
                run.id(), run.taskId(), run.runNumber(), run.triggerType().name(), run.parentRunId(),
                run.status().name(), run.batchJobExecutionId(),
                run.readCount(), run.parsedCount(), run.createdCount(), run.updatedCount(), run.duplicateCount(),
                run.failureCount(), run.requestCount(), run.checkpoint(), run.startedAt(), run.finishedAt(),
                run.completionReason() == null ? null : run.completionReason().name(),
                run.deferredUntil(), run.quotaDeferrals());
    }
}
