package com.aacv.system.crawl.application.port;

import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlCheckpointState;
import com.aacv.system.crawl.domain.CrawlControlIntent;
import com.aacv.system.crawl.domain.CrawlSchedule;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlRecoveryCandidate;
import com.aacv.system.crawl.domain.CrawlFailure;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.shared.domain.PageResult;
import java.util.Optional;
import java.util.List;

public interface CrawlRepository {

    PageResult<CrawlTask> findTaskPage(int page, int size);

    Optional<CrawlTask> findTaskById(long id);

    Optional<CrawlTask> lockTaskById(long id);

    boolean taskNameExists(long sourceId, String name);

    boolean taskHasRuns(long taskId);

    CrawlTask insertTask(CrawlTask task);

    Optional<CrawlTask> updateTask(CrawlTask task, long expectedVersion);

    boolean hasActiveConflict(long sourceId, String parameterHash);

    default CrawlRun insertPendingRun(CrawlTask task, String runNumber, long requestedBy) {
        return insertPendingRun(task, runNumber, "MANUAL", requestedBy, null);
    }

    default CrawlRun insertPendingRun(
            CrawlTask task, String runNumber, String triggerType, Long requestedBy) {
        return insertPendingRun(task, runNumber, triggerType, requestedBy, null);
    }

    CrawlRun insertPendingRun(
            CrawlTask task,
            String runNumber,
            String triggerType,
            Long requestedBy,
            Long parentRunId);

    int countRetryableFailures(long runId, int limit);

    PageResult<CrawlFailure> findFailurePage(long runId, int page, int size);

    Optional<CrawlRun> findRunById(long runId);

    Optional<CrawlRun> lockRunById(long runId);

    Optional<CrawlCheckpointState> findCheckpoint(long runId);

    CrawlControlIntent findControlIntent(long runId);

    Optional<CrawlRun> transitionRun(
            long runId,
            CrawlRunStatus expectedStatus,
            CrawlRunStatus targetStatus,
            CrawlControlIntent controlIntent,
            Long batchJobExecutionId,
            boolean setStartedAt,
            boolean setFinishedAt);

    Optional<CrawlRun> attachBatchExecution(long runId, CrawlRunStatus expectedStatus, long batchJobExecutionId);

    Optional<CrawlSchedule> findScheduleByTaskId(long taskId);

    Optional<CrawlSchedule> saveSchedule(CrawlSchedule schedule, Long expectedVersion);

    List<CrawlSchedule> findEnabledSchedules();

    List<CrawlRecoveryCandidate> findRecoveryCandidates();

    void recordCompletionReason(long runId, com.aacv.system.crawl.domain.CrawlCompletionReason reason);

    void recordQuotaDeferral(long runId, java.time.Instant deferredUntil, int quotaDeferrals);

    List<Long> findDueQuotaRuns(java.time.Instant now, int limit);
}
