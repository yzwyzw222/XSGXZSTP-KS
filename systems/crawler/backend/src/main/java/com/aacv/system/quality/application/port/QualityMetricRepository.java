package com.aacv.system.quality.application.port;

import com.aacv.system.quality.domain.QualityMetric;
import com.aacv.system.quality.domain.QualityMetricDetail;
import com.aacv.system.quality.domain.QualityMetricQuery;
import com.aacv.system.shared.domain.PageResult;
import java.util.Optional;

public interface QualityMetricRepository {

    PageResult<QualityMetric> findPage(QualityMetricQuery query, int page, int size);

    Optional<QualityMetricDetail> findDetail(long metricId, int sampleLimit);
}
