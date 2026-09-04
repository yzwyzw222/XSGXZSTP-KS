package com.aacv.system.export.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.export.application.port.ExportFileStore;
import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.domain.ExportFilter;
import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportRecord;
import com.aacv.system.export.domain.ExportStatus;
import com.aacv.system.export.domain.ExportTask;
import com.aacv.system.export.infrastructure.config.ExportProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExportTaskProcessorTests {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void atomicallyClaimsWritesAndCompletesTask() {
        ExportRepository repository = mock(ExportRepository.class);
        ExportFileStore fileStore = mock(ExportFileStore.class);
        ExportTaskFinalizer finalizer = mock(ExportTaskFinalizer.class);
        ExportProperties properties = new ExportProperties();
        ExportTask task = pendingTask(1);
        List<ExportRecord> records = List.of(new ExportRecord(1, "Paper", null, "article", null, null, null));
        when(repository.claim(task.id(), NOW)).thenReturn(true);
        when(repository.findById(task.id())).thenReturn(Optional.of(task));
        when(repository.findRecords(task.filters(), 10_000)).thenReturn(records);
        when(fileStore.write(task.id(), ExportFormat.CSV, records))
                .thenReturn(new ExportFileStore.StoredExport("result.csv", "result.csv"));

        new ExportTaskProcessor(repository, fileStore, finalizer, properties, Clock.fixed(NOW, ZoneOffset.UTC))
                .process(task.id());

        verify(finalizer).succeed(
                task, new ExportFileStore.StoredExport("result.csv", "result.csv"),
                NOW, NOW.plusSeconds(24 * 3600));
        verify(finalizer, never()).fail(eq(task.id()), any(), any(), any());
    }

    @Test
    void recordsSafeFailureWithoutRetryWhenSnapshotChanges() {
        ExportRepository repository = mock(ExportRepository.class);
        ExportFileStore fileStore = mock(ExportFileStore.class);
        ExportTaskFinalizer finalizer = mock(ExportTaskFinalizer.class);
        ExportTask task = pendingTask(2);
        when(repository.claim(task.id(), NOW)).thenReturn(true);
        when(repository.findById(task.id())).thenReturn(Optional.of(task));
        when(repository.findRecords(task.filters(), 10_000)).thenReturn(List.of());

        new ExportTaskProcessor(
                repository, fileStore, finalizer, new ExportProperties(), Clock.fixed(NOW, ZoneOffset.UTC))
                .process(task.id());

        verify(finalizer).fail(task.id(), "EXPORT_GENERATION_FAILED", "导出文件生成失败", NOW);
        verify(fileStore, never()).write(any(), any(), any());
    }

    private ExportTask pendingTask(long count) {
        return new ExportTask(
                "00000000-0000-0000-0000-000000000001", ExportFormat.CSV, ExportStatus.PENDING,
                new ExportFilter(null, null, null, null, null, null, null, null, null),
                7, count, 0, "token", null, null, NOW, null, null, null, null, null, 0);
    }
}
