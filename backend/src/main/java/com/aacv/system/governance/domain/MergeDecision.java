package com.aacv.system.governance.domain;

import java.time.Instant;

public record MergeDecision(
        long id,
        long candidateId,
        String decision,
        Long canonicalEntityId,
        long revisionId,
        long actorUserId,
        String reason,
        long version,
        Instant decidedAt) {
}
