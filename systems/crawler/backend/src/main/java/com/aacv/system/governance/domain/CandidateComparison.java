package com.aacv.system.governance.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CandidateComparison(
        long candidateId, long candidateVersion, GovernedEntityType entityType,
        long leftEntityId, long rightEntityId, Map<String, Object> left, Map<String, Object> right,
        boolean explicitVersionRelation) {

    public CandidateComparison {
        left = Collections.unmodifiableMap(new LinkedHashMap<>(left));
        right = Collections.unmodifiableMap(new LinkedHashMap<>(right));
    }
}
