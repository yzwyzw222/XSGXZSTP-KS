package com.aacv.system.crawl.domain;

import java.time.Instant;

public record CrawlTask(
        long id,
        long sourceId,
        String name,
        CrawlScope scope,
        int parameterVersion,
        String parameterHash,
        boolean enabled,
        long version,
        long createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public CrawlTask {
        if (sourceId < 1 || name == null || name.isBlank() || name.length() > 128 || scope == null
                || (parameterVersion != 1 && parameterVersion != 2)
                || parameterHash == null || parameterHash.length() != 64 || version < 0 || createdBy < 1) {
            throw new IllegalArgumentException("采集任务定义无效");
        }
    }

    public CrawlTask(
            long id,
            long sourceId,
            String name,
            CrawlScope scope,
            String parameterHash,
            boolean enabled,
            long version,
            long createdBy,
            Instant createdAt,
            Instant updatedAt) {
        this(id, sourceId, name, scope, 1, parameterHash, enabled, version, createdBy, createdAt, updatedAt);
    }
}
