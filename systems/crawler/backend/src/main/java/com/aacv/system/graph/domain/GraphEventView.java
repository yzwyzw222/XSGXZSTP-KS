package com.aacv.system.graph.domain;

import java.time.Instant;

public record GraphEventView(
        String eventId,
        long achievementId,
        long desiredVersion,
        GraphEventType eventType,
        GraphOutboxStatus status,
        int attempts,
        Instant nextAttemptAt,
        String errorCode,
        String errorSummary,
        String replayOfEventId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
