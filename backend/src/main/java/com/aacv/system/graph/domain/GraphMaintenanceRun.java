package com.aacv.system.graph.domain;

import java.time.Instant;

public record GraphMaintenanceRun(
        long id,
        GraphMaintenanceType runType,
        GraphMaintenanceStatus status,
        long cursorAchievementId,
        long scannedCount,
        long repairedCount,
        long differenceCount,
        long requestedBy,
        String errorCode,
        String errorSummary,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {
}
