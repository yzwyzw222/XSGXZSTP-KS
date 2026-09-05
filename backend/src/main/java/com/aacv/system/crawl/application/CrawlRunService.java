package com.aacv.system.crawl.application;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlRunLaunchPort;
import com.aacv.system.crawl.domain.CrawlControlIntent;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlRunStateMachine;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlFailure;
import com.aacv.system.crawl.domain.CrawlCompletionReason;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aacv.system.shared.domain.PageResult;

@Service
public class CrawlRunService {

    private final CrawlRepository repository;
    private final CrawlRunLaunchPort launchPort;
    private final AuditService auditService;
    private final CurrentActorProvider currentActorProvider;
    private final Clock clock;

    public CrawlRunService(
            CrawlRepository repository,
            CrawlRunLaunchPort launchPort,
            AuditService auditService,
            CurrentActorProvider currentActorProvider,
            Clock clock) {
        this.repository = repository;
        this.launchPort = launchPort;
        this.auditService = auditService;
        this.currentActorProvider = currentActorProvider;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CRAWL_TASK_CONTROL')")
    public CrawlRun requestPause(long runId) {
        CrawlRun current = lock(runId);
        if (current.status() == CrawlRunStatus.PAUSED
                && current.completionReason() == CrawlCompletionReason.QUOTA_EXHAUSTED) {
            repository.recordQuotaDeferral(runId, null, current.quotaDeferrals());
            repository.recordCompletionReason(runId, CrawlCompletionReason.USER_PAUSED);
            auditService.record(AuditAction.CRAWL_RUN_PAUSE_REQUESTED, "CRAWL_RUN", Long.toString(runId),
                    AuditResult.SUCCESS, Map.of("automaticResume", "disabled"));
            return lock(runId);
        }
        CrawlRun updated = transition(current, CrawlRunStatus.PAUSING, CrawlControlIntent.PAUSE, null, false, false);
        auditService.record(
                AuditAction.CRAWL_RUN_PAUSE_REQUESTED,
                "CRAWL_RUN",
                Long.toString(runId),
                AuditResult.SUCCESS,
                Map.of());
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CRAWL_TASK_CONTROL')")
    public CrawlRun requestCancel(long runId) {
        CrawlRun current = lock(runId);
        repository.recordCompletionReason(runId, CrawlCompletionReason.USER_CANCELLED);
        repository.recordQuotaDeferral(runId, null, current.quotaDeferrals());
        CrawlRunStatus target = current.status() == CrawlRunStatus.PENDING
                ? CrawlRunStatus.CANCELLED
                : CrawlRunStatus.CANCELLING;
        CrawlRun updated = transition(
                current,
                target,
                target == CrawlRunStatus.CANCELLING ? CrawlControlIntent.CANCEL : null,
                null,
                false,
                target == CrawlRunStatus.CANCELLED);
        if (current.status() == CrawlRunStatus.PAUSED) {
            updated = transition(updated, CrawlRunStatus.CANCELLED, null, null, false, true);
        }
        auditService.record(
                AuditAction.CRAWL_RUN_CANCEL_REQUESTED,
                "CRAWL_RUN",
                Long.toString(runId),
                AuditResult.SUCCESS,
                Map.of());
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CRAWL_TASK_CONTROL')")
    public CrawlRun resume(long runId) {
        CrawlRun current = lock(runId);
        if (current.deferredUntil() != null && clock.instant().isBefore(current.deferredUntil())) {
            throw new ResourceConflictException("来源额度尚未恢复，请等待页面提示的恢复时间");
        }
        repository.recordQuotaDeferral(runId, null, current.quotaDeferrals());
        repository.recordCompletionReason(runId, null);
        CrawlRun updated = transition(current, CrawlRunStatus.RUNNING, null, null, false, false);
        launchPort.launchAfterCommit(runId);
        auditService.record(
                AuditAction.CRAWL_RUN_RESUMED,
                "CRAWL_RUN",
                Long.toString(runId),
                AuditResult.SUCCESS,
                Map.of());
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CRAWL_TASK_CONTROL')")
    public CrawlRun retryFailures(long runId) {
        CrawlRun parent = lock(runId);
        if (parent.status() != CrawlRunStatus.PARTIAL_SUCCESS
                && parent.status() != CrawlRunStatus.FAILED) {
            throw new ResourceConflictException("只有部分成功或失败的运行可以重试失败记录");
        }
        int retryCount = repository.countRetryableFailures(runId, 100);
        if (retryCount == 0) {
            throw new ResourceConflictException("当前运行没有可重试的失败记录");
        }
        com.aacv.system.crawl.domain.CrawlTask task = repository.lockTaskById(parent.taskId())
                .orElseThrow(() -> new ResourceNotFoundException("采集任务不存在"));
        if (repository.hasActiveConflict(task.sourceId(), task.parameterHash())) {
            throw new ResourceConflictException("相同来源和参数已经存在活动运行");
        }
        long actorId = currentActorProvider.currentUserId().orElseThrow();
        CrawlRun retryRun = repository.insertPendingRun(
                task,
                UUID.randomUUID().toString(),
                "RETRY_FAILURES",
                actorId,
                parent.id());
        launchPort.launchAfterCommit(retryRun.id());
        auditService.record(
                AuditAction.CRAWL_FAILURES_RETRIED,
                "CRAWL_RUN",
                Long.toString(retryRun.id()),
                AuditResult.SUCCESS,
                Map.of("parentRunId", Long.toString(parent.id()), "retryCount", Integer.toString(retryCount)));
        return retryRun;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CRAWL_RUN_READ')")
    public PageResult<CrawlFailure> findFailures(long runId, int page, int size) {
        if (repository.findRunById(runId).isEmpty()) {
            throw new ResourceNotFoundException("采集运行不存在");
        }
        return repository.findFailurePage(runId, page, size);
    }

    @Transactional
    public CrawlRun markBatchStarted(long runId, long batchJobExecutionId) {
        CrawlRun current = lock(runId);
        if (current.status() == CrawlRunStatus.PENDING) {
            return transition(
                    current, CrawlRunStatus.RUNNING, null, batchJobExecutionId, true, false);
        }
        if (current.status() == CrawlRunStatus.RUNNING) {
            return repository.attachBatchExecution(
                            runId, CrawlRunStatus.RUNNING, batchJobExecutionId)
                    .orElseThrow(() -> new ResourceConflictException("采集运行绑定Batch执行失败"));
        }
        throw new ResourceConflictException("当前采集运行不能启动Batch执行");
    }

    @Transactional
    public CrawlRun completeBatch(long runId, boolean batchSucceeded) {
        return completeBatch(runId, batchSucceeded, null);
    }

    @Transactional
    public CrawlRun completeBatch(long runId, boolean batchSucceeded, Instant quotaResumeAt) {
        CrawlRun current = lock(runId);
        if (CrawlRunStateMachine.isTerminal(current.status()) || current.status() == CrawlRunStatus.PAUSED) {
            return current;
        }
        boolean quotaExhausted = !batchSucceeded && quotaResumeAt != null;
        if (quotaExhausted && current.status() == CrawlRunStatus.RUNNING && current.quotaDeferrals() < 3) {
            Instant boundedResumeAt = quotaResumeAt.isBefore(clock.instant().plusSeconds(5))
                    ? clock.instant().plusSeconds(5) : quotaResumeAt;
            if (boundedResumeAt.isAfter(clock.instant().plusSeconds(86_405))) {
                throw new IllegalArgumentException("来源额度恢复时间超过一天的上限");
            }
            repository.recordCompletionReason(runId, CrawlCompletionReason.QUOTA_EXHAUSTED);
            repository.recordQuotaDeferral(runId, boundedResumeAt, current.quotaDeferrals() + 1);
            CrawlRun pausing = transition(current, CrawlRunStatus.PAUSING, null, null, false, false);
            return transition(pausing, CrawlRunStatus.PAUSED, null, null, false, false);
        }
        CrawlRunStatus target;
        CrawlCompletionReason reason = current.completionReason();
        if (current.status() == CrawlRunStatus.PAUSING && (batchSucceeded || quotaExhausted)) {
            target = CrawlRunStatus.PAUSED;
            reason = CrawlCompletionReason.USER_PAUSED;
        } else if (current.status() == CrawlRunStatus.CANCELLING && (batchSucceeded || quotaExhausted)) {
            target = CrawlRunStatus.CANCELLED;
            reason = CrawlCompletionReason.USER_CANCELLED;
        } else if (current.status() == CrawlRunStatus.RUNNING && batchSucceeded) {
            if (reason == null) reason = checkpointCompletionReason(current);
            target = current.failureCount() > 0 || (reason != null && reason.limited())
                    ? CrawlRunStatus.PARTIAL_SUCCESS
                    : CrawlRunStatus.SUCCEEDED;
        } else if (current.status() == CrawlRunStatus.RUNNING
                || current.status() == CrawlRunStatus.PAUSING
                || current.status() == CrawlRunStatus.CANCELLING) {
            target = CrawlRunStatus.FAILED;
            reason = quotaExhausted ? CrawlCompletionReason.QUOTA_RETRY_LIMIT : CrawlCompletionReason.BATCH_FAILED;
        } else {
            throw new ResourceConflictException("Batch完成状态与业务运行状态不一致");
        }
        repository.recordCompletionReason(runId, reason);
        repository.recordQuotaDeferral(runId, null, current.quotaDeferrals());
        return transition(
                current,
                target,
                null,
                null,
                false,
                target != CrawlRunStatus.PAUSED);
    }

    @Transactional
    public CrawlRun failLaunch(long runId) {
        CrawlRun current = lock(runId);
        if (current.status() != CrawlRunStatus.PENDING && current.status() != CrawlRunStatus.RUNNING) {
            return current;
        }
        repository.recordCompletionReason(runId, CrawlCompletionReason.BATCH_FAILED);
        repository.recordQuotaDeferral(runId, null, current.quotaDeferrals());
        return transition(current, CrawlRunStatus.FAILED, null, null, false, true);
    }

    @Transactional
    public boolean resumeQuotaIfDue(long runId) {
        CrawlRun current = lock(runId);
        if (current.status() != CrawlRunStatus.PAUSED
                || current.completionReason() != CrawlCompletionReason.QUOTA_EXHAUSTED
                || current.deferredUntil() == null || current.deferredUntil().isAfter(clock.instant())) {
            return false;
        }
        repository.recordQuotaDeferral(runId, null, current.quotaDeferrals());
        repository.recordCompletionReason(runId, null);
        transition(current, CrawlRunStatus.RUNNING, null, null, false, false);
        launchPort.launchAfterCommit(runId);
        auditService.record(AuditAction.CRAWL_RUN_RESUMED, "CRAWL_RUN", Long.toString(runId),
                AuditResult.SUCCESS, Map.of("trigger", "QUOTA_RESET"));
        return true;
    }

    private CrawlCompletionReason checkpointCompletionReason(CrawlRun run) {
        if (run.triggerType() == com.aacv.system.crawl.domain.CrawlTriggerType.RETRY_FAILURES) {
            return CrawlCompletionReason.RETRY_BATCH_COMPLETED;
        }
        var checkpoint = repository.findCheckpoint(run.id()).orElse(null);
        var task = repository.findTaskById(run.taskId()).orElse(null);
        if (checkpoint == null || task == null) return null;
        // 历史检查点缺少截断标记时保守报告上限，避免把截断的末页认作完整来源。
        if (checkpoint.committedRecords() >= task.scope().maxRecords()) return CrawlCompletionReason.RECORD_LIMIT;
        if (checkpoint.committedPages() >= task.scope().maxPages()) return CrawlCompletionReason.PAGE_LIMIT;
        return "__END__".equals(checkpoint.cursor()) ? CrawlCompletionReason.SOURCE_EXHAUSTED : null;
    }

    private CrawlRun lock(long runId) {
        return repository.lockRunById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("采集运行不存在"));
    }

    private CrawlRun transition(
            CrawlRun current,
            CrawlRunStatus target,
            CrawlControlIntent intent,
            Long batchJobExecutionId,
            boolean started,
            boolean finished) {
        try {
            CrawlRunStateMachine.requireTransition(current.status(), target);
        } catch (IllegalStateException exception) {
            throw new ResourceConflictException(exception.getMessage());
        }
        return repository.transitionRun(
                        current.id(),
                        current.status(),
                        target,
                        intent,
                        batchJobExecutionId,
                        started,
                        finished)
                .orElseThrow(() -> new ResourceConflictException("采集运行状态已被其他操作更新"));
    }
}
