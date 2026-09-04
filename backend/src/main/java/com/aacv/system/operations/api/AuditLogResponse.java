package com.aacv.system.operations.api;

import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditLogEntry;
import com.aacv.system.operations.domain.AuditResult;
import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        long id,
        Long actorUserId,
        AuditAction action,
        String targetType,
        String targetId,
        AuditResult result,
        String traceId,
        Map<String, String> summary,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLogEntry entry) {
        return new AuditLogResponse(
                entry.id(),
                entry.actorUserId(),
                entry.action(),
                entry.targetType(),
                entry.targetId(),
                entry.result(),
                entry.traceId(),
                entry.summary(),
                entry.createdAt());
    }
}
