package com.aacv.system.export.domain;

import java.time.Instant;

public record ExportTask(
        String id,
        ExportFormat format,
        ExportStatus status,
        ExportFilter filters,
        long requestedBy,
        long requestedCount,
        long exportedCount,
        String downloadToken,
        String fileName,
        String fileRelativePath,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt,
        String errorCode,
        String errorMessage,
        long version) {

    public boolean downloadAvailable(Instant now) {
        return status == ExportStatus.SUCCEEDED
                && expiresAt != null
                && expiresAt.isAfter(now)
                && fileRelativePath != null;
    }
}
