package com.aacv.system.ingestion.application;

public record IngestionPageResult(
        long readCount,
        long parsedCount,
        long createdCount,
        long updatedCount,
        long duplicateCount,
        long failureCount,
        long requestCount,
        String committedCursor) {
}
