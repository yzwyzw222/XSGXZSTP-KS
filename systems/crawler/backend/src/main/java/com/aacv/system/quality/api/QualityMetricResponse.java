package com.aacv.system.quality.api;

import com.aacv.system.quality.domain.QualityMetric;
import java.math.BigDecimal;
import java.time.Instant;

public record QualityMetricResponse(
        long id,
        long sourceId,
        long taskId,
        long runId,
        String metricCode,
        long numerator,
        long denominator,
        BigDecimal metricValue,
        Instant measuredAt,
        long version) {

    static QualityMetricResponse from(QualityMetric metric) {
        return new QualityMetricResponse(
                metric.id(), metric.sourceId(), metric.taskId(), metric.runId(),
                metric.metricCode().name(), metric.numerator(), metric.denominator(),
                metric.metricValue(), metric.measuredAt(), metric.version());
    }
}
