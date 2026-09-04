package com.aacv.system.quality.api;

import com.aacv.system.quality.application.QualityMetricService;
import com.aacv.system.quality.domain.QualityMetricCode;
import com.aacv.system.quality.domain.QualityMetricQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/quality-metrics")
public class QualityMetricController {

    private final QualityMetricService service;

    public QualityMetricController(QualityMetricService service) {
        this.service = service;
    }

    @GetMapping
    public QualityMetricPageResponse findPage(
            @RequestParam(required = false) @Min(1) Long sourceId,
            @RequestParam(required = false) @Min(1) Long runId,
            @RequestParam(required = false) QualityMetricCode metricCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return QualityMetricPageResponse.from(service.findPage(
                new QualityMetricQuery(sourceId, runId, metricCode), page, size));
    }

    @GetMapping("/{metricId}")
    public QualityMetricDetailResponse findDetail(
            @PathVariable @Min(1) long metricId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int sampleLimit) {
        return QualityMetricDetailResponse.from(service.requireDetail(metricId, sampleLimit));
    }
}
