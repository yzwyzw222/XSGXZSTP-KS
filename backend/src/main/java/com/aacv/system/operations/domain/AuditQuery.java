package com.aacv.system.operations.domain;

import java.time.Instant;

public record AuditQuery(AuditCategory category, String username, Instant from, Instant to,
                         AuditResult result, AuditAction action) {
    public static final AuditQuery ALL = new AuditQuery(null, null, null, null, null, null);

    public AuditQuery {
        username = username == null || username.isBlank() ? null : username.strip();
        if (username != null && (username.length() > 64 || username.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("账号筛选条件无效");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }
        if (category != null && action != null && AuditCategory.of(action) != category) {
            throw new IllegalArgumentException("操作类型与日志分类不一致");
        }
    }

    public String escapedUsername() {
        return username == null ? null : username.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
