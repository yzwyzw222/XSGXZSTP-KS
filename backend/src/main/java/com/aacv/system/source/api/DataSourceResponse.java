package com.aacv.system.source.api;

import com.aacv.system.source.domain.SourceType;
import java.net.URI;
import java.time.Instant;

public record DataSourceResponse(
        long id,
        String sourceCode,
        SourceType sourceType,
        URI baseUri,
        boolean enabled,
        int requestsPerSecond,
        int maxConcurrency,
        int connectTimeoutSeconds,
        int responseTimeoutSeconds,
        int maxRetries,
        int maxResponseBytes,
        String complianceNote,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        int consecutiveFailures,
        long version) {
}
