package com.aacv.system.quality.infrastructure.persistence;

import com.aacv.system.quality.application.port.QualityMetricRepository;
import com.aacv.system.quality.domain.QualityIssueSample;
import com.aacv.system.quality.domain.QualityMetric;
import com.aacv.system.quality.domain.QualityMetricCode;
import com.aacv.system.quality.domain.QualityMetricDetail;
import com.aacv.system.quality.domain.QualityMetricQuery;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class MyBatisQualityMetricRepository implements QualityMetricRepository {

    private static final TypeReference<Map<String, Object>> EVIDENCE_TYPE = new TypeReference<>() { };

    private final QualityMetricMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisQualityMetricRepository(QualityMetricMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<QualityMetric> findPage(QualityMetricQuery query, int page, int size) {
        long total = mapper.count(query);
        List<QualityMetric> items = mapper.findPage(query, (long) page * size, size).stream()
                .map(this::toMetric)
                .toList();
        return PageResult.of(items, page, size, total);
    }

    @Override
    public Optional<QualityMetricDetail> findDetail(long metricId, int sampleLimit) {
        QualityMetricRow row = mapper.findById(metricId);
        if (row == null) {
            return Optional.empty();
        }
        List<QualityIssueSample> samples = mapper.findSamples(
                        row.getRunId(), row.getMetricCode(), sampleLimit)
                .stream()
                .map(this::toSample)
                .toList();
        return Optional.of(new QualityMetricDetail(toMetric(row), samples));
    }

    private QualityMetric toMetric(QualityMetricRow row) {
        return new QualityMetric(
                row.getId(), row.getSourceId(), row.getTaskId(), row.getRunId(),
                QualityMetricCode.valueOf(row.getMetricCode()), row.getNumerator(),
                row.getDenominator(), row.getMetricValue(), row.getMeasuredAt(), row.getVersion());
    }

    private QualityIssueSample toSample(QualityIssueRow row) {
        try {
            return new QualityIssueSample(
                    row.getId(), row.getRawRecordId(), row.getExternalRecordId(),
                    objectMapper.readValue(row.getEvidenceJson(), EVIDENCE_TYPE), row.getCreatedAt());
        } catch (JacksonException exception) {
            throw new IllegalStateException("质量问题样本JSON损坏", exception);
        }
    }
}
