package com.aacv.system.export.application;

import com.aacv.system.export.application.port.ExportActorProvider;
import com.aacv.system.export.application.port.ExportFileStore;
import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.application.port.ExportTaskDispatcher;
import com.aacv.system.export.domain.ExportDownload;
import com.aacv.system.export.domain.ExportFilter;
import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportStatus;
import com.aacv.system.export.domain.ExportTask;
import com.aacv.system.export.infrastructure.config.ExportProperties;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportService.class);

    private final ExportRepository repository;
    private final ExportActorProvider actorProvider;
    private final ExportTaskDispatcher dispatcher;
    private final ExportFileStore fileStore;
    private final ExportProperties properties;
    private final AuditService auditService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ExportService(
            ExportRepository repository,
            ExportActorProvider actorProvider,
            ExportTaskDispatcher dispatcher,
            ExportFileStore fileStore,
            ExportProperties properties,
            AuditService auditService,
            Clock clock) {
        this.repository = repository;
        this.actorProvider = actorProvider;
        this.dispatcher = dispatcher;
        this.fileStore = fileStore;
        this.properties = properties;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAuthority('EXPORT_CREATE')")
    public ExportTask create(ExportFormat format, ExportFilter filters) {
        if (format == null || filters == null) {
            throw new IllegalArgumentException("导出格式和筛选条件不能为空");
        }
        properties.validate();
        var actor = actorProvider.current();
        repository.lockRequester(actor.userId());
        if (repository.countActiveByRequester(actor.userId()) >= properties.getUserActiveLimit()) {
            throw new ExportConcurrencyLimitException("当前账号的活动导出任务已达上限");
        }
        if (repository.countActive() >= properties.getConcurrency() + properties.getQueueCapacity()) {
            throw new ExportConcurrencyLimitException("导出执行队列已满");
        }
        long requestedCount = repository.countRecords(filters);
        if (requestedCount > properties.getMaxRecords()) {
            throw new ExportLimitExceededException("导出结果超过10,000条，请收窄筛选范围");
        }

        Instant createdAt = clock.instant();
        ExportTask task = new ExportTask(
                UUID.randomUUID().toString(), format, ExportStatus.PENDING, filters,
                actor.userId(), requestedCount, 0, downloadToken(), null, null,
                createdAt, null, null, null, null, null, 0);
        repository.insert(task);
        auditService.record(
                AuditAction.EXPORT_CREATED, "EXPORT_TASK", task.id(), AuditResult.SUCCESS,
                Map.of("format", format.name(), "requestedCount", Long.toString(requestedCount)));
        dispatchAfterCommit(task.id());
        return task;
    }

    @Transactional
    @PreAuthorize("hasAuthority('EXPORT_READ')")
    public ExportTask get(String taskId) {
        ExportTask task = authorizedTask(taskId);
        if (isExpired(task, clock.instant())) {
            expire(task);
            return repository.findById(task.id()).orElse(task);
        }
        return task;
    }

    @Transactional
    @PreAuthorize("hasAuthority('EXPORT_READ')")
    public ExportDownload download(String taskId, String token) {
        if (token == null || token.length() < 32 || token.length() > 128) {
            throw new AccessDeniedException("导出下载令牌无效");
        }
        ExportTask task = authorizedTask(taskId);
        Instant now = clock.instant();
        if (isExpired(task, now) || task.status() == ExportStatus.EXPIRED) {
            expire(task);
            throw new ExportExpiredException("导出文件已过期");
        }
        if (!task.downloadAvailable(now)) {
            throw new ResourceNotFoundException("导出文件尚不可用");
        }
        if (!MessageDigest.isEqual(
                task.downloadToken().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("导出下载令牌无效");
        }
        String contentType = task.format() == ExportFormat.CSV ? "text/csv" : "application/json";
        ExportDownload download = new ExportDownload(
                fileStore.resolve(task.fileRelativePath()), task.fileName(), contentType);
        auditService.record(
                AuditAction.EXPORT_DOWNLOADED, "EXPORT_TASK", task.id(), AuditResult.SUCCESS,
                Map.of("format", task.format().name(), "exportedCount", Long.toString(task.exportedCount())));
        return download;
    }

    private ExportTask authorizedTask(String taskId) {
        String normalizedId = validateTaskId(taskId);
        ExportTask task = repository.findById(normalizedId)
                .orElseThrow(() -> new ResourceNotFoundException("导出任务不存在"));
        var actor = actorProvider.current();
        if (!actor.administrator() && task.requestedBy() != actor.userId()) {
            throw new AccessDeniedException("无权访问该导出任务");
        }
        return task;
    }

    private void dispatchAfterCommit(String taskId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.submit(taskId);
                }
            });
        } else {
            dispatcher.submit(taskId);
        }
    }

    private String downloadToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String validateTaskId(String taskId) {
        try {
            String normalized = UUID.fromString(taskId).toString();
            if (!normalized.equalsIgnoreCase(taskId)) {
                throw new IllegalArgumentException("导出任务ID无效");
            }
            return normalized;
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("导出任务ID无效");
        }
    }

    private boolean isExpired(ExportTask task, Instant now) {
        return task.status() == ExportStatus.SUCCEEDED
                && task.expiresAt() != null
                && !task.expiresAt().isAfter(now);
    }

    private void expire(ExportTask task) {
        if (task.status() == ExportStatus.SUCCEEDED && repository.markExpired(task.id(), clock.instant())) {
            try {
                fileStore.delete(task.fileRelativePath());
            } catch (RuntimeException exception) {
                LOGGER.warn("过期导出文件清理失败，taskId={}", task.id(), exception);
            }
        }
    }
}
