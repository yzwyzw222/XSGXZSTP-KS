package com.aacv.system.operations.domain;

import java.time.Instant;
import java.util.Map;

public record AuditLogEntry(
        long id,
        Long actorUserId,
        AuditAction action,
        String targetType,
        String targetId,
        AuditResult result,
        String traceId,
        Map<String, String> summary,
        Instant createdAt) {

    public AuditLogEntry {
        summary = summary == null ? Map.of() : Map.copyOf(summary);
    }
}
