package com.aacv.system.quality.api;

import com.aacv.system.quality.domain.QualityIssueSample;
import java.time.Instant;
import java.util.Map;

public record QualityIssueSampleResponse(
        long id,
        long rawRecordId,
        String externalRecordId,
        Map<String, Object> evidence,
        Instant createdAt) {

    static QualityIssueSampleResponse from(QualityIssueSample sample) {
        return new QualityIssueSampleResponse(
                sample.id(), sample.rawRecordId(), sample.externalRecordId(),
                sample.evidence(), sample.createdAt());
    }
}
