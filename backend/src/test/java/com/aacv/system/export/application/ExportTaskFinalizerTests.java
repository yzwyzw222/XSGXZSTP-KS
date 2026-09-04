package com.aacv.system.export.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.export.application.port.ExportFileStore;
import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.domain.ExportFilter;
import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportStatus;
import com.aacv.system.export.domain.ExportTask;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExportTaskFinalizerTests {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void completesTaskAndRecordsSafeAuditSummary() {
        ExportRepository repository = mock(ExportRepository.class);
        AuditService auditService = mock(AuditService.class);
        ExportTask task = task(ExportStatus.RUNNING);
        var stored = new ExportFileStore.StoredExport("file.csv", "file.csv");

        new ExportTaskFinalizer(repository, auditService)
                .succeed(task, stored, NOW, NOW.plusSeconds(3600));

        verify(repository).markSucceeded(
                task.id(), 3, "file.csv", "file.csv", NOW, NOW.plusSeconds(3600));
        verify(auditService).recordForActor(
                7, AuditAction.EXPORT_SUCCEEDED, "EXPORT_TASK", task.id(), AuditResult.SUCCESS,
                Map.of("format", "CSV", "exportedCount", "3"));
    }

    @Test
    void failsTaskAndRecordsOnlyStableErrorCode() {
        ExportRepository repository = mock(ExportRepository.class);
        AuditService auditService = mock(AuditService.class);
        ExportTask task = task(ExportStatus.RUNNING);
        when(repository.findById(task.id())).thenReturn(Optional.of(task));
        when(repository.markFailed(task.id(), "EXPORT_INTERRUPTED", "安全摘要", NOW)).thenReturn(true);

        new ExportTaskFinalizer(repository, auditService)
                .fail(task.id(), "EXPORT_INTERRUPTED", "安全摘要", NOW);

        verify(repository).markFailed(task.id(), "EXPORT_INTERRUPTED", "安全摘要", NOW);
        verify(auditService).recordForActor(
                7, AuditAction.EXPORT_FAILED, "EXPORT_TASK", task.id(), AuditResult.FAILURE,
                Map.of("format", "CSV", "errorCode", "EXPORT_INTERRUPTED"));
    }

    @Test
    void doesNotRecordFailureWhenStateWasAlreadyTerminal() {
        ExportRepository repository = mock(ExportRepository.class);
        AuditService auditService = mock(AuditService.class);
        ExportTask task = task(ExportStatus.SUCCEEDED);
        when(repository.findById(task.id())).thenReturn(Optional.of(task));

        new ExportTaskFinalizer(repository, auditService)
                .fail(task.id(), "EXPORT_INTERRUPTED", "安全摘要", NOW);

        verify(auditService, never()).recordForActor(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
    }

    private ExportTask task(ExportStatus status) {
        return new ExportTask(
                "00000000-0000-0000-0000-000000000001", ExportFormat.CSV, status,
                new ExportFilter(null, null, null, null, null, null, null, null, null),
                7, 3, 0, "token", null, null, NOW, NOW, null, null, null, null, 1);
    }
}
