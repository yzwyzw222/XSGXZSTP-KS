package com.aacv.system.source.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ScholarlyMetadata(
        Instant observedAt,
        Long citedByCount,
        Boolean retracted,
        Boolean openAccess,
        String openAccessStatus,
        List<VersionRelation> versionRelations) {

    public ScholarlyMetadata {
        if (observedAt == null || (citedByCount != null && citedByCount < 0)) {
            throw new IllegalArgumentException("来源学术指标的观测时间或被引次数无效");
        }
        versionRelations = versionRelations == null ? List.of() : List.copyOf(versionRelations);
        if (versionRelations.size() > 50) throw new IllegalArgumentException("来源版本关系超过上限");
        if (openAccessStatus != null
                && !Set.of("diamond", "gold", "green", "hybrid", "bronze", "closed").contains(openAccessStatus)) {
            throw new IllegalArgumentException("开放状态无效");
        }
    }

    public record VersionRelation(String relationType, String targetDoi) {
        public VersionRelation {
            if (relationType == null || !Set.of("is-preprint-of", "has-preprint", "is-version-of", "has-version").contains(relationType)
                    || targetDoi == null || targetDoi.length() > 255 || !targetDoi.matches("10\\.\\d{4,9}/\\S+")) {
                throw new IllegalArgumentException("来源版本关系无效");
            }
        }
    }
}
