package com.aacv.system.export.application;

import com.aacv.system.export.application.port.ExportFileStore;
import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.infrastructure.config.ExportProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExportTaskProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportTaskProcessor.class);

    private final ExportRepository repository;
    private final ExportFileStore fileStore;
    private final ExportTaskFinalizer finalizer;
    private final ExportProperties properties;
    private final Clock clock;

    public ExportTaskProcessor(
            ExportRepository repository,
            ExportFileStore fileStore,
            ExportTaskFinalizer finalizer,
            ExportProperties properties,
            Clock clock) {
        this.repository = repository;
        this.fileStore = fileStore;
        this.finalizer = finalizer;
        this.properties = properties;
        this.clock = clock;
    }

    public void process(String taskId) {
        Instant startedAt = clock.instant();
        if (!repository.claim(taskId, startedAt)) {
            return;
        }
        ExportFileStore.StoredExport stored = null;
        try {
            var task = repository.findById(taskId).orElseThrow();
            var records = repository.findRecords(task.filters(), properties.getMaxRecords());
            if (records.size() != task.requestedCount()) {
                throw new IllegalStateException("导出数据范围在任务执行前发生变化");
            }
            stored = fileStore.write(task.id(), task.format(), records);
            Instant completedAt = clock.instant();
            finalizer.succeed(
                    task, stored, completedAt,
                    completedAt.plus(properties.getRetentionHours(), ChronoUnit.HOURS));
        } catch (Exception exception) {
            LOGGER.error("导出任务生成失败，taskId={}，failureType={}", taskId, exception.getClass().getSimpleName());
            deletePartialFile(taskId, stored);
            finalizer.fail(taskId, "EXPORT_GENERATION_FAILED", "导出文件生成失败", clock.instant());
        }
    }

    private void deletePartialFile(String taskId, ExportFileStore.StoredExport stored) {
        if (stored == null) {
            return;
        }
        try {
            fileStore.delete(stored.relativePath());
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "失败导出文件清理未完成，taskId={}，failureType={}",
                    taskId, exception.getClass().getSimpleName());
        }
    }
}
