package com.aacv.system.governance.api;

import com.aacv.system.governance.domain.FieldOverride;
import java.time.Instant;

public record FieldOverrideResponse(
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

    static FieldOverrideResponse from(FieldOverride override) {
        return new FieldOverrideResponse(
                override.id(), override.achievementId(), override.fieldName(), override.value(),
                override.revisionId(), override.actorUserId(), override.reason(), override.active(),
                override.version(), override.createdAt(), override.updatedAt());
    }
}
