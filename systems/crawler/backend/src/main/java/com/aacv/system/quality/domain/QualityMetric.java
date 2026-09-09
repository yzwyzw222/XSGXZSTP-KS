package com.aacv.system.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record QualityMetric(
        long id,
        long sourceId,
        long taskId,
        long runId,
        QualityMetricCode metricCode,
        long numerator,
        long denominator,
        BigDecimal metricValue,
        Instant measuredAt,
        long version) {
}
