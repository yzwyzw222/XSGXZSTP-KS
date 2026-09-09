package com.aacv.system.export.application;

import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.application.port.ExportFileStore.StoredExport;
import com.aacv.system.export.domain.ExportTask;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportTaskFinalizer {

    private final ExportRepository repository;
    private final AuditService auditService;

    public ExportTaskFinalizer(ExportRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public void succeed(ExportTask task, StoredExport stored, Instant completedAt, Instant expiresAt) {
        repository.markSucceeded(
                task.id(), task.requestedCount(), stored.fileName(), stored.relativePath(), completedAt, expiresAt);
        auditService.recordForActor(
                task.requestedBy(), AuditAction.EXPORT_SUCCEEDED, "EXPORT_TASK", task.id(), AuditResult.SUCCESS,
                Map.of(
                        "format", task.format().name(),
                        "exportedCount", Long.toString(task.requestedCount())));
    }

    @Transactional
    public void fail(String taskId, String errorCode, String errorMessage, Instant completedAt) {
        repository.findById(taskId).ifPresent(task -> {
            if (repository.markFailed(taskId, errorCode, errorMessage, completedAt)) {
                auditService.recordForActor(
                        task.requestedBy(), AuditAction.EXPORT_FAILED, "EXPORT_TASK", task.id(), AuditResult.FAILURE,
                        Map.of("format", task.format().name(), "errorCode", errorCode));
            }
        });
    }
}
