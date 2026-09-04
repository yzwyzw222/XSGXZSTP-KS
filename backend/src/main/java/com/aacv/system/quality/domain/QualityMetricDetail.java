package com.aacv.system.quality.domain;

import java.util.List;

public record QualityMetricDetail(QualityMetric metric, List<QualityIssueSample> samples) {

    public QualityMetricDetail {
        samples = samples == null ? List.of() : List.copyOf(samples);
    }
}
