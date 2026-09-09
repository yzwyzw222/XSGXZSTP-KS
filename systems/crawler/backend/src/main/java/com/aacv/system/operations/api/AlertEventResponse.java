package com.aacv.system.operations.api;

import com.aacv.system.operations.domain.AlertEvent;
import com.aacv.system.operations.domain.AlertSeverity;
import com.aacv.system.operations.domain.AlertStatus;
import com.aacv.system.operations.domain.AlertSubjectType;
import com.aacv.system.operations.domain.AlertType;
import java.time.Instant;
import java.util.Map;

public record AlertEventResponse(
        long id,
        AlertType type,
        AlertSeverity severity,
        AlertStatus status,
        AlertSubjectType subjectType,
        String subjectId,
        String summary,
        Map<String, Object> evidence,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        long occurrenceCount,
        Long acknowledgedBy,
        Instant acknowledgedAt,
        String acknowledgementReason,
        long version) {

    public static AlertEventResponse from(AlertEvent event) {
        return new AlertEventResponse(
                event.id(),
                event.type(),
                event.severity(),
                event.status(),
                event.subjectType(),
                event.subjectId(),
                event.summary(),
                event.evidence(),
                event.firstDetectedAt(),
                event.lastDetectedAt(),
                event.occurrenceCount(),
                event.acknowledgedBy(),
                event.acknowledgedAt(),
                event.acknowledgementReason(),
                event.version());
    }
}
