package com.aacv.system.operations.domain;

import java.time.Instant;
import java.util.Map;

public record AlertEvent(
        long id,
        AlertType type,
        AlertSeverity severity,
        AlertStatus status,
        AlertSubjectType subjectType,
        String subjectId,
        String summary,
        Map<String, Object> evidence,
        Instant detectedSignalAt,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        long occurrenceCount,
        Long acknowledgedBy,
        Instant acknowledgedAt,
        String acknowledgementReason,
        long version) {

    public AlertEvent {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
