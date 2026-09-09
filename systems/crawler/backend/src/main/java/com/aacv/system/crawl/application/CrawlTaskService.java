package com.aacv.system.crawl.application;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlRunLaunchPort;
import com.aacv.system.crawl.application.port.CrawlSchedulePort;
import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlSchedule;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.shared.domain.PageResult;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrawlTaskService {

    private final CrawlRepository repository;
    private final DataSourceRepository sourceRepository;
    private final CrawlScopeCodec scopeCodec;
    private final CurrentActorProvider currentActorProvider;
    private final AuditService auditService;
    private final CrawlRunLaunchPort launchPort;
    private final CrawlSchedulePort schedulePort;
    private final Clock clock;

    public CrawlTaskService(
            CrawlRepository repository,
            DataSourceRepository sourceRepository,
            CrawlScopeCodec scopeCodec,
            CurrentActorProvider currentActorProvider,
            AuditService auditService,
            CrawlRunLaunchPort launchPort,
            CrawlSchedulePort schedulePort,
            Clock clock) {
        this.repository = repository;
        this.sourceRepository = sourceRepository;
        this.scopeCodec = scopeCodec;
        this.currentActorProvider = currentActorProvider;
        this.auditService = auditService;
        this.launchPort = launchPort;
        this.schedulePort = schedulePort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CRAWL_TASK_READ')")
    public PageResult<CrawlTask> findPage(int page, int size) {
        return repository.findTaskPage(page, size);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CRAWL_TASK_READ')")
    public CrawlTask requireTask(long taskId) {
        return repository.findTaskById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("采集任务不存在"));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CRAWL_TASK_CREATE')")
    public CrawlTask create(long sourceId, String name, CrawlScope scope) {
        DataSourceConfiguration source = sourceRepository.lockById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("数据源不存在"));
        if (!source.enabled()) {
            throw new ResourceConflictException("数据源已停用，不能创建新任务");
        }
        String normalizedName = normalizeName(name);
        if (repository.taskNameExists(sourceId, normalizedName)) {
            throw new ResourceConflictException("同一数据源下的任务名称已存在");
        }
        long actorId = currentActorProvider.currentUserId().orElseThrow();
        Instant now = clock.instant();
        int parameterVersion = parameterVersion(source, scope);
        CrawlTask created = repository.insertTask(new CrawlTask(
                0, sourceId, normalizedName, scope, parameterVersion,
                scopeCodec.hash(scope), true, 0, actorId, now, now));
        auditService.record(
                AuditAction.CRAWL_TASK_CREATED,
                "CRAWL_TASK",
                Long.toString(created.id()),
                AuditResult.SUCCESS,
                Map.of("sourceId", Long.toString(sourceId)));
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CRAWL_TASK_UPDATE')")
    public CrawlTask update(long taskId, String name, CrawlScope scope, long expectedVersion) {
        CrawlTask current = repository.lockTaskById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("采集任务不存在"));
        if (repository.taskHasRuns(taskId)) {
            throw new ResourceConflictException("已经运行过的任务不能修改范围，请创建新任务");
        }
        String normalizedName = normalizeName(name);
        if (current.parameterVersion() == 1) {
            requireVersionOneScope(scope);
        }
        CrawlTask candidate = new CrawlTask(
                current.id(), current.sourceId(), normalizedName, scope, current.parameterVersion(),
                scopeCodec.hash(scope),
                current.enabled(), current.version(), current.createdBy(), current.createdAt(), clock.instant());
        CrawlTask updated = repository.updateTask(candidate, expectedVersion)
                .orElseThrow(() -> new ResourceConflictException("采集任务已被其他操作更新"));
        auditService.record(
                AuditAction.CRAWL_TASK_UPDATED,
                "CRAWL_TASK",
                Long.toString(taskId),
                AuditResult.SUCCESS,
                Map.of());
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CRAWL_TASK_CONTROL')")
    public CrawlRun trigger(long taskId) {
        CrawlTask task = repository.lockTaskById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("采集任务不存在"));
        DataSourceConfiguration source = sourceRepository.lockById(task.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("数据源不存在"));
        if (!task.enabled() || !source.enabled()) {
            throw new ResourceConflictException("任务或数据源已停用");
        }
        if (repository.hasActiveConflict(task.sourceId(), task.parameterHash())) {
            throw new ResourceConflictException("相同来源和参数已经存在活动运行");
        }
        long actorId = currentActorProvider.currentUserId().orElseThrow();
        CrawlRun run = repository.insertPendingRun(task, UUID.randomUUID().toString(), actorId);
        launchPort.launchAfterCommit(run.id());
        auditService.record(
                AuditAction.CRAWL_TASK_TRIGGERED,
                "CRAWL_RUN",
                Long.toString(run.id()),
                AuditResult.SUCCESS,
                Map.of("taskId", Long.toString(taskId)));
        return run;
    }

    @Transactional
    public CrawlRun triggerScheduled(long taskId) {
        CrawlTask task = repository.lockTaskById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("采集任务不存在"));
        DataSourceConfiguration source = sourceRepository.lockById(task.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("数据源不存在"));
        if (!task.enabled() || !source.enabled()) {
            throw new ResourceConflictException("任务或数据源已停用");
        }
        if (repository.hasActiveConflict(task.sourceId(), task.parameterHash())) {
            throw new ResourceConflictException("相同来源和参数已经存在活动运行");
        }
        CrawlRun run = repository.insertPendingRun(
                task, UUID.randomUUID().toString(), "SCHEDULED", task.createdBy());
        launchPort.launchAfterCommit(run.id());
        auditService.record(
                AuditAction.CRAWL_TASK_TRIGGERED,
                "CRAWL_RUN",
                Long.toString(run.id()),
                AuditResult.SUCCESS,
                Map.of("taskId", Long.toString(taskId), "triggerType", "SCHEDULED"));
        return run;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CRAWL_RUN_READ')")
    public CrawlRun requireRun(long runId) {
        return repository.findRunById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("采集运行不存在"));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CRAWL_SCHEDULE_MANAGE')")
    public CrawlSchedule configureDailySchedule(
            long taskId, LocalTime localTime, ZoneId zoneId, Long expectedVersion) {
        CrawlTask task = repository.lockTaskById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("采集任务不存在"));
        DataSourceConfiguration source = sourceRepository.lockById(task.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("数据源不存在"));
        CrawlSchedule current = repository.findScheduleByTaskId(taskId).orElse(null);
        Instant nextFireAt = nextFireAt(localTime, zoneId);
        CrawlSchedule candidate = new CrawlSchedule(
                current == null ? 0 : current.id(),
                task.id(),
                current == null ? "crawl-task-" + task.id() : current.scheduleKey(),
                localTime,
                zoneId,
                "FIXED_SCOPE_REFRESH",
                nextFireAt,
                true,
                current == null ? 0 : current.version());
        CrawlSchedule saved = repository.saveSchedule(candidate, expectedVersion)
                .orElseThrow(() -> new ResourceConflictException("采集计划已被其他操作更新"));
        schedulePort.synchronizeAfterCommit(saved);
        auditService.record(
                AuditAction.CRAWL_SCHEDULE_CHANGED,
                "CRAWL_SCHEDULE",
                Long.toString(saved.id()),
                AuditResult.SUCCESS,
                Map.of("taskId", Long.toString(taskId)));
        return saved;
    }

    private Instant nextFireAt(LocalTime localTime, ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), zoneId);
        ZonedDateTime candidate = now.toLocalDate().atTime(localTime).atZone(zoneId);
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 128) {
            throw new IllegalArgumentException("任务名称不能为空且不能超过128个字符");
        }
        return name.trim();
    }

    private int parameterVersion(DataSourceConfiguration source, CrawlScope scope) {
        if (source.sourceType() == com.aacv.system.source.domain.SourceType.OPENALEX) {
            requireVersionOneScope(scope);
            return 1;
        }
        return 2;
    }

    private void requireVersionOneScope(CrawlScope scope) {
        if (!scope.dois().isEmpty() || !scope.orcids().isEmpty() || !scope.rorIds().isEmpty()
                || scope.updatedFrom() != null || scope.updatedUntil() != null) {
            throw new IllegalArgumentException("OpenAlex参数版本1不接受Crossref专用筛选条件");
        }
    }
}
