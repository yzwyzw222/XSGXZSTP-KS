package com.aacv.system.governance.domain;

import java.time.Instant;

public record FieldOverride(
        long id,
        long achievementId,
        String fieldName,
        Object value,
        long revisionId,
        long actorUserId,
        String reason,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
