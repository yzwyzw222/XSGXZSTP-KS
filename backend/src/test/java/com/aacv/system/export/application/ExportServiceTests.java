package com.aacv.system.export.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.export.application.port.ExportActorProvider;
import com.aacv.system.export.application.port.ExportFileStore;
import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.application.port.ExportTaskDispatcher;
import com.aacv.system.export.domain.ExportFilter;
import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportStatus;
import com.aacv.system.export.domain.ExportTask;
import com.aacv.system.export.infrastructure.config.ExportProperties;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ExportServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");
    private static final String TASK_ID = "00000000-0000-0000-0000-000000000001";
    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    private ExportRepository repository;
    private ExportActorProvider actorProvider;
    private ExportTaskDispatcher dispatcher;
    private ExportFileStore fileStore;
    private ExportProperties properties;
    private AuditService auditService;
    private ExportService service;

    @BeforeEach
    void setUp() {
        repository = mock(ExportRepository.class);
        actorProvider = mock(ExportActorProvider.class);
        dispatcher = mock(ExportTaskDispatcher.class);
        fileStore = mock(ExportFileStore.class);
        properties = new ExportProperties();
        auditService = mock(AuditService.class);
        service = new ExportService(
                repository, actorProvider, dispatcher, fileStore, properties, auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(actorProvider.current()).thenReturn(new ExportActorProvider.ExportActor(7, false));
    }

    @Test
    void createsBoundedPendingTaskAndDispatchesIt() {
        when(repository.countRecords(any())).thenReturn(12L);

        ExportTask task = service.create(ExportFormat.CSV, emptyFilter());

        assertEquals(ExportStatus.PENDING, task.status());
        assertEquals(12, task.requestedCount());
        assertEquals(7, task.requestedBy());
        assertEquals(43, task.downloadToken().length());
        verify(repository).lockRequester(7);
        verify(repository).insert(task);
        verify(auditService).record(
                AuditAction.EXPORT_CREATED, "EXPORT_TASK", task.id(), AuditResult.SUCCESS,
                java.util.Map.of("format", "CSV", "requestedCount", "12"));
        verify(dispatcher).submit(task.id());
    }

    @Test
    void rejectsRecordAndConcurrencyLimitsBeforeInsert() {
        when(repository.countRecords(any())).thenReturn(10_001L);
        assertThrows(ExportLimitExceededException.class,
                () -> service.create(ExportFormat.JSON, emptyFilter()));
        verify(repository, never()).insert(any());

        when(repository.countActiveByRequester(7)).thenReturn(2L);
        assertThrows(ExportConcurrencyLimitException.class,
                () -> service.create(ExportFormat.JSON, emptyFilter()));
    }

    @Test
    void enforcesOwnerAndDownloadToken() {
        ExportTask task = succeededTask(7, NOW.plusSeconds(3600));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(fileStore.resolve("file.csv")).thenReturn(Path.of("file.csv"));

        assertThrows(AccessDeniedException.class, () -> service.download(TASK_ID, "x".repeat(32)));
        var download = service.download(TASK_ID, TOKEN);
        assertEquals("text/csv", download.contentType());
        assertEquals("file.csv", download.fileName());
        verify(auditService).record(
                AuditAction.EXPORT_DOWNLOADED, "EXPORT_TASK", TASK_ID, AuditResult.SUCCESS,
                java.util.Map.of("format", "CSV", "exportedCount", "1"));

        when(actorProvider.current()).thenReturn(new ExportActorProvider.ExportActor(8, false));
        assertThrows(AccessDeniedException.class, () -> service.get(TASK_ID));
    }

    @Test
    void expiresTaskBeforeDownloadAndRemovesFile() {
        ExportTask expired = succeededTask(7, NOW);
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(expired));
        when(repository.markExpired(TASK_ID, NOW)).thenReturn(true);

        assertThrows(ExportExpiredException.class, () -> service.download(TASK_ID, TOKEN));

        verify(repository).markExpired(TASK_ID, NOW);
        verify(fileStore).delete("file.csv");
    }

    @Test
    void pendingTaskDoesNotExposeAFile() {
        ExportTask pending = new ExportTask(
                TASK_ID, ExportFormat.JSON, ExportStatus.PENDING, emptyFilter(), 7, 0, 0,
                TOKEN, null, null, NOW, null, null, null, null, null, 0);
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(pending));

        assertThrows(com.aacv.system.shared.application.ResourceNotFoundException.class,
                () -> service.download(TASK_ID, TOKEN));
        assertNotNull(service.get(TASK_ID));
        assertNull(pending.fileRelativePath());
        verify(fileStore, never()).resolve(any());
    }

    private ExportTask succeededTask(long requestedBy, Instant expiresAt) {
        return new ExportTask(
                TASK_ID, ExportFormat.CSV, ExportStatus.SUCCEEDED, emptyFilter(), requestedBy, 1, 1,
                TOKEN, "file.csv", "file.csv", NOW.minusSeconds(60), NOW.minusSeconds(30), NOW, expiresAt,
                null, null, 2);
    }

    private ExportFilter emptyFilter() {
        return new ExportFilter(null, null, null, null, null, null, null, null, null);
    }
}
