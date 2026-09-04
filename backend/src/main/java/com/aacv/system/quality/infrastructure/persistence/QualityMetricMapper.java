package com.aacv.system.quality.infrastructure.persistence;

import com.aacv.system.quality.domain.QualityMetricQuery;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface QualityMetricMapper {
    long count(@Param("query") QualityMetricQuery query);
    List<QualityMetricRow> findPage(
            @Param("query") QualityMetricQuery query,
            @Param("offset") long offset,
            @Param("size") int size);
    QualityMetricRow findById(long metricId);
    List<QualityIssueRow> findSamples(
            @Param("runId") long runId,
            @Param("metricCode") String metricCode,
            @Param("limit") int limit);
}
