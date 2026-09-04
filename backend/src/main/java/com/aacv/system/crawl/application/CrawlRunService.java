package com.aacv.system.crawl.application;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlRunLaunchPort;
import com.aacv.system.crawl.domain.CrawlControlIntent;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlRunStateMachine;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlFailure;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
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

    public CrawlRunService(
            CrawlRepository repository,
            CrawlRunLaunchPort launchPort,
            AuditService auditService,
            CurrentActorProvider currentActorProvider) {
        this.repository = repository;
        this.launchPort = launchPort;
        this.auditService = auditService;
        this.currentActorProvider = currentActorProvider;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CRAWL_TASK_CONTROL')")
    public CrawlRun requestPause(long runId) {
        CrawlRun current = lock(runId);
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
        CrawlRun current = lock(runId);
        CrawlRunStatus target;
        if (current.status() == CrawlRunStatus.PAUSING && batchSucceeded) {
            target = CrawlRunStatus.PAUSED;
        } else if (current.status() == CrawlRunStatus.CANCELLING && batchSucceeded) {
            target = CrawlRunStatus.CANCELLED;
        } else if (current.status() == CrawlRunStatus.RUNNING && batchSucceeded) {
            target = current.failureCount() > 0
                    ? CrawlRunStatus.PARTIAL_SUCCESS
                    : CrawlRunStatus.SUCCEEDED;
        } else if (current.status() == CrawlRunStatus.RUNNING
                || current.status() == CrawlRunStatus.PAUSING
                || current.status() == CrawlRunStatus.CANCELLING) {
            target = CrawlRunStatus.FAILED;
        } else if (CrawlRunStateMachine.isTerminal(current.status()) || current.status() == CrawlRunStatus.PAUSED) {
            return current;
        } else {
            throw new ResourceConflictException("Batch完成状态与业务运行状态不一致");
        }
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
        return transition(current, CrawlRunStatus.FAILED, null, null, false, true);
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
