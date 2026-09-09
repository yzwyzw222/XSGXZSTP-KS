package com.aacv.system.operations.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record AuditRecord(
        Long actorUserId,
        AuditAction action,
        String targetType,
        String targetId,
        AuditResult result,
        String traceId,
        Map<String, String> summary,
        Instant createdAt, String clientIp, String userAgent) {

    public AuditRecord(Long actorUserId, AuditAction action, String targetType, String targetId, AuditResult result,
            String traceId, Map<String, String> summary, Instant createdAt) {
        this(actorUserId, action, targetType, targetId, result, traceId, summary, createdAt, null, null);
    }

    private static final Set<String> FORBIDDEN_KEY_PARTS =
            Set.of("password", "cookie", "session", "token", "authorization", "secret");

    public AuditRecord {
        if (action == null || result == null || createdAt == null) {
            throw new IllegalArgumentException("审计操作、结果和时间不能为空");
        }
        if (targetType == null || targetType.isBlank() || targetType.length() > 64) {
            throw new IllegalArgumentException("审计目标类型无效");
        }
        if (targetId != null && targetId.length() > 128) {
            throw new IllegalArgumentException("审计目标标识过长");
        }
        if (traceId == null || traceId.isBlank() || traceId.length() > 64) {
            throw new IllegalArgumentException("审计traceId无效");
        }
        if ((clientIp != null && clientIp.length() > 64) || (userAgent != null && userAgent.length() > 512)) {
            throw new IllegalArgumentException("审计来源信息过长");
        }
        summary = sanitizeSummary(summary);
    }

    private static Map<String, String> sanitizeSummary(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.size() > 16) {
            throw new IllegalArgumentException("审计摘要字段过多");
        }
        LinkedHashMap<String, String> safe = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (normalizedKey.isBlank()
                    || normalizedKey.length() > 64
                    || FORBIDDEN_KEY_PARTS.stream().anyMatch(normalizedKey::contains)) {
                throw new IllegalArgumentException("审计摘要包含不允许的字段");
            }
            if (value != null && value.length() > 256) {
                throw new IllegalArgumentException("审计摘要字段值过长");
            }
            safe.put(key, value);
        });
        return Map.copyOf(safe);
    }
}
