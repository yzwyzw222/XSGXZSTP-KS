package com.aacv.system.quality.application;

import com.aacv.system.quality.application.port.QualityMetricRepository;
import com.aacv.system.quality.domain.QualityMetric;
import com.aacv.system.quality.domain.QualityMetricDetail;
import com.aacv.system.quality.domain.QualityMetricQuery;
import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.shared.domain.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityMetricService {

    private final QualityMetricRepository repository;

    public QualityMetricService(QualityMetricRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GOVERNANCE_READ')")
    public PageResult<QualityMetric> findPage(QualityMetricQuery query, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return repository.findPage(query, page, size);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GOVERNANCE_READ')")
    public QualityMetricDetail requireDetail(long metricId, int sampleLimit) {
        if (metricId < 1 || sampleLimit < 1 || sampleLimit > 50) {
            throw new IllegalArgumentException("质量指标详情参数无效");
        }
        return repository.findDetail(metricId, sampleLimit)
                .orElseThrow(() -> new ResourceNotFoundException("质量指标不存在"));
    }
}
