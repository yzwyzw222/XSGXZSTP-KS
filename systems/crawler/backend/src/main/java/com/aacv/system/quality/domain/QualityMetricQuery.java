package com.aacv.system.quality.domain;

public record QualityMetricQuery(Long sourceId, Long runId, QualityMetricCode metricCode) {

    public QualityMetricQuery {
        if (sourceId != null && sourceId < 1 || runId != null && runId < 1) {
            throw new IllegalArgumentException("质量指标筛选ID无效");
        }
    }
}
