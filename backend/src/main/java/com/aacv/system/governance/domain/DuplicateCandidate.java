package com.aacv.system.governance.domain;

import java.time.Instant;
import java.util.Map;

public record DuplicateCandidate(
        long id,
        GovernedEntityType entityType,
        long leftEntityId,
        long rightEntityId,
        String matchBasis,
        Map<String, Object> evidence,
        CandidateStatus status,
        Long sourceId,
        int ruleVersion,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public DuplicateCandidate {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
