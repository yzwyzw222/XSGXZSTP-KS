package com.aacv.system.export.api;

import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportStatus;
import com.aacv.system.export.domain.ExportTask;
import java.time.Instant;

public record ExportTaskResponse(
        String id,
        ExportFormat format,
        ExportStatus status,
        long requestedBy,
        long requestedCount,
        long exportedCount,
        boolean downloadAvailable,
        String downloadToken,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt,
        String errorCode,
        String errorMessage) {

    static ExportTaskResponse from(ExportTask task, Instant now) {
        boolean available = task.downloadAvailable(now);
        return new ExportTaskResponse(
                task.id(), task.format(), task.status(), task.requestedBy(), task.requestedCount(),
                task.exportedCount(), available, available ? task.downloadToken() : null,
                task.createdAt(), task.startedAt(), task.completedAt(), task.expiresAt(),
                task.errorCode(), task.errorMessage());
    }
}
