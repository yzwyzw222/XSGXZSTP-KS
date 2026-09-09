package com.aacv.system.governance.api;

import com.aacv.system.governance.domain.DuplicateCandidate;
import java.time.Instant;
import java.util.Map;

public record DuplicateCandidateResponse(
        long id,
        String entityType,
        long leftEntityId,
        long rightEntityId,
        String matchBasis,
        Map<String, Object> evidence,
        String status,
        Long sourceId,
        int ruleVersion,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static DuplicateCandidateResponse from(DuplicateCandidate candidate) {
        return new DuplicateCandidateResponse(
                candidate.id(), candidate.entityType().name(), candidate.leftEntityId(),
                candidate.rightEntityId(), candidate.matchBasis(), candidate.evidence(),
                candidate.status().name(), candidate.sourceId(), candidate.ruleVersion(),
                candidate.version(), candidate.createdAt(), candidate.updatedAt());
    }
}
