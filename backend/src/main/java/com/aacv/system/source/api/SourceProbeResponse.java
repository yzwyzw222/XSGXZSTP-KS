package com.aacv.system.source.api;

import java.time.Instant;
import java.util.Map;

public record SourceProbeResponse(
        boolean reachable,
        Integer statusCode,
        String errorCategory,
        Map<String, String> rateLimitSummary,
        Instant checkedAt) {

    public SourceProbeResponse {
        rateLimitSummary = rateLimitSummary == null ? Map.of() : Map.copyOf(rateLimitSummary);
    }
}
