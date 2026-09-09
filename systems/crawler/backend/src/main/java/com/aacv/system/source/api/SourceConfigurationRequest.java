package com.aacv.system.source.api;

import com.aacv.system.source.domain.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SourceConfigurationRequest(
        SourceType sourceType,
        @Min(1) @Max(10) int requestsPerSecond,
        @Min(1) @Max(4) int maxConcurrency,
        @Min(1) @Max(30) int connectTimeoutSeconds,
        @Min(1) @Max(120) int responseTimeoutSeconds,
        @Min(0) @Max(5) int maxRetries,
        @Min(1024) @Max(20971520) int maxResponseBytes,
        @NotBlank @Size(max = 1000) String complianceNote,
        @Min(0) Long version) {

    public SourceConfigurationRequest(
            int requestsPerSecond,
            int maxConcurrency,
            int connectTimeoutSeconds,
            int responseTimeoutSeconds,
            int maxRetries,
            int maxResponseBytes,
            String complianceNote,
            Long version) {
        this(null, requestsPerSecond, maxConcurrency, connectTimeoutSeconds, responseTimeoutSeconds,
                maxRetries, maxResponseBytes, complianceNote, version);
    }
}
