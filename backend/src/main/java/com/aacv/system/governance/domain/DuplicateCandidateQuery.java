package com.aacv.system.governance.domain;

import java.time.Instant;

public record DuplicateCandidateQuery(
        GovernedEntityType entityType,
        CandidateStatus status,
        Long sourceId,
        Integer ruleVersion,
        Instant createdFrom,
        Instant createdTo) {

    public DuplicateCandidateQuery {
        if (sourceId != null && sourceId < 1) {
            throw new IllegalArgumentException("来源ID无效");
        }
        if (ruleVersion != null && ruleVersion < 1) {
            throw new IllegalArgumentException("规则版本无效");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException("候选创建时间范围无效");
        }
    }
}
