package com.aacv.system.graph.domain;

import java.time.Instant;

public record GraphSyncStatus(
        boolean neo4jAvailable,
        Integer schemaVersion,
        long pendingCount,
        long processingCount,
        long deadCount,
        Long oldestPendingAgeSeconds,
        Instant lastSucceededAt,
        boolean lagThresholdExceeded,
        boolean rebuildInProgress) {
}
