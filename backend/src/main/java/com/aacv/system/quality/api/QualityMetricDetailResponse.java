package com.aacv.system.quality.api;

import com.aacv.system.quality.domain.QualityMetricDetail;
import java.util.List;

public record QualityMetricDetailResponse(
        QualityMetricResponse metric,
        List<QualityIssueSampleResponse> samples) {

    static QualityMetricDetailResponse from(QualityMetricDetail detail) {
        return new QualityMetricDetailResponse(
                QualityMetricResponse.from(detail.metric()),
                detail.samples().stream().map(QualityIssueSampleResponse::from).toList());
    }
}
