package com.aacv.system.operations.domain;

import java.time.Instant;

public record OperationsOverview(
        Instant generatedAt,
        HealthStatus applicationStatus,
        HealthStatus mysqlStatus,
        HealthStatus neo4jStatus,
        long activeCrawlRunCount,
        long recentCrawlFailureCount,
        long graphPendingCount,
        long graphProcessingCount,
        long graphDeadCount,
        long openAlertCount) {
}
