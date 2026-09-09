package com.aacv.system.source.api;

import com.aacv.system.shared.domain.PageResult;
import com.aacv.system.source.domain.DataSourceConfiguration;
import java.util.List;

public record DataSourcePageResponse(
        List<DataSourceResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static DataSourcePageResponse from(PageResult<DataSourceConfiguration> result) {
        return new DataSourcePageResponse(
                result.items().stream().map(DataSourcePageResponse::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    static DataSourceResponse toResponse(DataSourceConfiguration source) {
        return new DataSourceResponse(
                source.id(), source.sourceCode(), source.sourceType(), source.baseUri(), source.enabled(),
                source.settings().requestsPerSecond(), source.settings().maxConcurrency(),
                Math.toIntExact(source.settings().connectTimeout().toSeconds()),
                Math.toIntExact(source.settings().responseTimeout().toSeconds()),
                source.settings().maxRetries(), source.settings().maxResponseBytes(), source.complianceNote(),
                source.lastSuccessAt(), source.lastFailureAt(), source.consecutiveFailures(), source.version());
    }
}
