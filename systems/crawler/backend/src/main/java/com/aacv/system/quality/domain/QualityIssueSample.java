package com.aacv.system.quality.domain;

import java.time.Instant;
import java.util.Map;

public record QualityIssueSample(
        long id,
        long rawRecordId,
        String externalRecordId,
        Map<String, Object> evidence,
        Instant createdAt) {

    public QualityIssueSample {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
