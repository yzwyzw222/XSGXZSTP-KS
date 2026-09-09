package com.aacv.system.crawl.api;

import java.time.Instant;

public record CrawlTaskResponse(
        long id,
        long sourceId,
        String name,
        int parameterVersion,
        CrawlTaskParametersRequest parameters,
        boolean enabled,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
