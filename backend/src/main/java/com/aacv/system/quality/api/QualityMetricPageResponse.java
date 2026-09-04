package com.aacv.system.quality.api;

import com.aacv.system.quality.domain.QualityMetric;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;

public record QualityMetricPageResponse(
        List<QualityMetricResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static QualityMetricPageResponse from(PageResult<QualityMetric> result) {
        return new QualityMetricPageResponse(
                result.items().stream().map(QualityMetricResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
