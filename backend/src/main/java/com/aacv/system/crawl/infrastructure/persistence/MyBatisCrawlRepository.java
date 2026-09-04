package com.aacv.system.crawl.infrastructure.persistence;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlCheckpointState;
import com.aacv.system.crawl.domain.CrawlControlIntent;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlTriggerType;
import com.aacv.system.crawl.domain.CrawlFailure;
import com.aacv.system.crawl.domain.CrawlRecoveryCandidate;
import com.aacv.system.crawl.domain.CrawlSchedule;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.shared.domain.PageResult;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCrawlRepository implements CrawlRepository {

    private final CrawlMapper mapper;
    private final CrawlScopeCodec scopeCodec;
    private final Clock clock;

    public MyBatisCrawlRepository(CrawlMapper mapper, CrawlScopeCodec scopeCodec, Clock clock) {
        this.mapper = mapper;
        this.scopeCodec = scopeCodec;
        this.clock = clock;
    }

    @Override
    public PageResult<CrawlTask> findTaskPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return PageResult.of(
                mapper.findTaskPage((long) page * size, size).stream().map(this::toTask).toList(),
                page, size, mapper.countTasks());
    }

    @Override
    public Optional<CrawlTask> findTaskById(long id) {
        return Optional.ofNullable(mapper.findTaskById(id)).map(this::toTask);
    }

    @Override
    public Optional<CrawlTask> lockTaskById(long id) {
        return Optional.ofNullable(mapper.lockTaskById(id)).map(this::toTask);
    }

    @Override
    public boolean taskNameExists(long sourceId, String name) {
        return mapper.countTaskName(sourceId, name) > 0;
    }

    @Override
    public boolean taskHasRuns(long taskId) {
        return mapper.countTaskRuns(taskId) > 0;
    }

    @Override
    public CrawlTask insertTask(CrawlTask task) {
        CrawlTaskRow row = toTaskRow(task);
        mapper.insertTask(row);
        return findTaskById(row.getId()).orElseThrow();
    }

    @Override
    public Optional<CrawlTask> updateTask(CrawlTask task, long expectedVersion) {
        if (mapper.updateTask(toTaskRow(task), expectedVersion) != 1) {
            return Optional.empty();
        }
        return findTaskById(task.id());
    }

    @Override
    public boolean hasActiveConflict(long sourceId, String parameterHash) {
        return mapper.countActiveConflicts(sourceId, parameterHash) > 0;
    }

    @Override
    public CrawlRun insertPendingRun(
            CrawlTask task,
            String runNumber,
            String triggerType,
            Long requestedBy,
            Long parentRunId) {
        mapper.insertPendingRun(
                task.id(), runNumber, task.parameterHash(), task.scope().publicationDateFrom(),
                task.scope().publicationDateTo(), triggerType, requestedBy, parentRunId, clock.instant());
        return Optional.ofNullable(mapper.findRunByNumber(runNumber)).map(this::toRun).orElseThrow();
    }

    @Override
    public int countRetryableFailures(long runId, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("失败重试条数必须在1至100之间");
        }
        return mapper.countRetryableFailures(runId, limit);
    }

    @Override
    public PageResult<CrawlFailure> findFailurePage(long runId, int page, int size) {
        if (runId < 1 || page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("失败记录分页参数无效");
        }
        java.util.List<CrawlFailure> items = mapper.findFailurePage(runId, (long) page * size, size)
                .stream()
                .map(row -> new CrawlFailure(
                        row.getId(), row.getRunId(), row.getRawRecordId(), row.getExternalRecordId(),
                        row.getFailureStage(), row.getErrorCategory(), row.getSafeMessage(),
                        row.isRetryable(), row.getAttemptCount(), row.isResolved(), row.getEvidenceHash(),
                        row.getCreatedAt(), row.getUpdatedAt()))
                .toList();
        return PageResult.of(items, page, size, mapper.countFailures(runId));
    }

    @Override
    public Optional<CrawlRun> findRunById(long runId) {
        return Optional.ofNullable(mapper.findRunById(runId)).map(this::toRun);
    }

    @Override
    public Optional<CrawlRun> lockRunById(long runId) {
        return Optional.ofNullable(mapper.lockRunById(runId)).map(this::toRun);
    }

    @Override
    public Optional<CrawlCheckpointState> findCheckpoint(long runId) {
        return Optional.ofNullable(mapper.findCheckpoint(runId)).map(row -> new CrawlCheckpointState(
                row.getCursorValue(), row.getCommittedPages(), row.getCommittedRecords(), row.getVersion()));
    }

    @Override
    public CrawlControlIntent findControlIntent(long runId) {
        String intent = mapper.findControlIntent(runId);
        return intent == null ? null : CrawlControlIntent.valueOf(intent);
    }

    @Override
    public Optional<CrawlRun> transitionRun(
            long runId,
            CrawlRunStatus expectedStatus,
            CrawlRunStatus targetStatus,
            CrawlControlIntent controlIntent,
            Long batchJobExecutionId,
            boolean setStartedAt,
            boolean setFinishedAt) {
        int changed = mapper.transitionRun(
                runId,
                expectedStatus.name(),
                targetStatus.name(),
                controlIntent == null ? null : controlIntent.name(),
                batchJobExecutionId,
                setStartedAt,
                setFinishedAt);
        return changed == 1 ? findRunById(runId) : Optional.empty();
    }

    @Override
    public Optional<CrawlRun> attachBatchExecution(
            long runId, CrawlRunStatus expectedStatus, long batchJobExecutionId) {
        return mapper.attachBatchExecution(runId, expectedStatus.name(), batchJobExecutionId) == 1
                ? findRunById(runId)
                : Optional.empty();
    }

    @Override
    public Optional<CrawlSchedule> findScheduleByTaskId(long taskId) {
        return Optional.ofNullable(mapper.findScheduleByTaskId(taskId)).map(this::toSchedule);
    }

    @Override
    public Optional<CrawlSchedule> saveSchedule(CrawlSchedule schedule, Long expectedVersion) {
        CrawlScheduleRow row = toScheduleRow(schedule);
        if (schedule.id() == 0) {
            if (findScheduleByTaskId(schedule.taskId()).isPresent()) {
                return Optional.empty();
            }
            mapper.insertSchedule(row);
        } else {
            if (expectedVersion == null || mapper.updateSchedule(row, expectedVersion) != 1) {
                return Optional.empty();
            }
        }
        return findScheduleByTaskId(schedule.taskId());
    }

    @Override
    public java.util.List<CrawlSchedule> findEnabledSchedules() {
        return mapper.findEnabledSchedules().stream().map(this::toSchedule).toList();
    }

    @Override
    public java.util.List<CrawlRecoveryCandidate> findRecoveryCandidates() {
        return mapper.findRecoveryCandidates().stream()
                .map(row -> new CrawlRecoveryCandidate(
                        row.getRunId(),
                        CrawlRunStatus.valueOf(row.getBusinessStatus()),
                        row.getBatchJobExecutionId(),
                        row.getBatchStatus(),
                        row.isCheckpointPresent()))
                .toList();
    }

    private CrawlTask toTask(CrawlTaskRow row) {
        return new CrawlTask(
                row.getId(), row.getSourceId(), row.getTaskName(), scopeCodec.decode(row.getParametersJson()),
                row.getParameterVersion(), row.getParameterHash(), row.isEnabled(), row.getVersion(), row.getCreatedBy(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private CrawlTaskRow toTaskRow(CrawlTask task) {
        CrawlTaskRow row = new CrawlTaskRow();
        row.setId(task.id() == 0 ? null : task.id());
        row.setSourceId(task.sourceId());
        row.setTaskName(task.name());
        row.setParameterVersion(task.parameterVersion());
        row.setParametersJson(scopeCodec.encode(task.scope()));
        row.setParameterHash(task.parameterHash());
        row.setEnabled(task.enabled());
        row.setVersion(task.version());
        row.setCreatedBy(task.createdBy());
        row.setCreatedAt(task.createdAt());
        row.setUpdatedAt(task.updatedAt());
        return row;
    }

    private CrawlRun toRun(CrawlRunRow row) {
        return new CrawlRun(
                row.getId(), row.getTaskId(), row.getRunNumber(),
                CrawlTriggerType.valueOf(row.getTriggerType()), row.getParentRunId(),
                CrawlRunStatus.valueOf(row.getStatus()),
                row.getBatchJobExecutionId(), row.getReadCount(), row.getParsedCount(), row.getCreatedCount(),
                row.getUpdatedCount(), row.getDuplicateCount(), row.getFailureCount(), row.getRequestCount(),
                row.getCheckpoint(), row.getStartedAt(), row.getFinishedAt(), row.getVersion());
    }

    private CrawlSchedule toSchedule(CrawlScheduleRow row) {
        return new CrawlSchedule(
                row.getId(), row.getTaskId(), row.getScheduleKey(), row.getLocalTime(),
                ZoneId.of(row.getTimeZone()), row.getIncrementalMode(),
                row.getNextFireAt(), row.isEnabled(), row.getVersion());
    }

    private CrawlScheduleRow toScheduleRow(CrawlSchedule schedule) {
        CrawlScheduleRow row = new CrawlScheduleRow();
        row.setId(schedule.id() == 0 ? null : schedule.id());
        row.setTaskId(schedule.taskId());
        row.setScheduleKey(schedule.scheduleKey());
        row.setLocalTime(schedule.localTime());
        row.setTimeZone(schedule.timeZone().getId());
        row.setIncrementalMode(schedule.incrementalMode());
        row.setNextFireAt(schedule.nextFireAt());
        row.setEnabled(schedule.enabled());
        row.setVersion(schedule.version());
        return row;
    }
}
