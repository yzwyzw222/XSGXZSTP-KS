package com.aacv.system.source.api;

import com.aacv.system.identity.api.VersionRequest;
import com.aacv.system.source.application.DataSourceService;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceProbeResult;
import com.aacv.system.source.domain.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/sources")
public class DataSourceController {

    private final DataSourceService service;

    public DataSourceController(DataSourceService service) {
        this.service = service;
    }

    @GetMapping
    public DataSourcePageResponse findPage(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return DataSourcePageResponse.from(service.findPage(page, size));
    }

    @GetMapping("/{sourceId}")
    public DataSourceResponse findById(@PathVariable @Min(1) long sourceId) {
        return DataSourcePageResponse.toResponse(service.requireById(sourceId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataSourceResponse create(@Valid @RequestBody SourceConfigurationRequest request) {
        SourceType sourceType = request.sourceType() == null ? SourceType.OPENALEX : request.sourceType();
        return DataSourcePageResponse.toResponse(service.create(
                sourceType, settings(request), request.complianceNote()));
    }

    @PutMapping("/{sourceId}")
    public DataSourceResponse update(
            @PathVariable @Min(1) long sourceId,
            @Valid @RequestBody SourceConfigurationRequest request) {
        if (request.version() == null) {
            throw new IllegalArgumentException("更新数据源必须提供版本号");
        }
        return DataSourcePageResponse.toResponse(service.update(
                sourceId, settings(request), request.complianceNote(), request.version()));
    }

    @PostMapping("/{sourceId}/enable")
    public DataSourceResponse enable(
            @PathVariable @Min(1) long sourceId, @Valid @RequestBody VersionRequest request) {
        return DataSourcePageResponse.toResponse(service.setEnabled(sourceId, true, request.version()));
    }

    @PostMapping("/{sourceId}/disable")
    public DataSourceResponse disable(
            @PathVariable @Min(1) long sourceId, @Valid @RequestBody VersionRequest request) {
        return DataSourcePageResponse.toResponse(service.setEnabled(sourceId, false, request.version()));
    }

    @PostMapping("/{sourceId}/probe")
    public SourceProbeResponse probe(@PathVariable @Min(1) long sourceId) {
        SourceProbeResult result = service.probe(sourceId);
        return new SourceProbeResponse(
                result.reachable(), result.statusCode(), result.errorCategory(),
                result.rateLimitSummary(), result.checkedAt());
    }

    private SourceConnectionSettings settings(SourceConfigurationRequest request) {
        return new SourceConnectionSettings(
                request.requestsPerSecond(),
                request.maxConcurrency(),
                Duration.ofSeconds(request.connectTimeoutSeconds()),
                Duration.ofSeconds(request.responseTimeoutSeconds()),
                request.maxRetries(),
                request.maxResponseBytes());
    }
}
