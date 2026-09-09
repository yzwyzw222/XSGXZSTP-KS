package com.aacv.system.crawl.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

public record CrawlSchedule(
        long id,
        long taskId,
        String scheduleKey,
        LocalTime localTime,
        ZoneId timeZone,
        String incrementalMode,
        Instant nextFireAt,
        boolean enabled,
        long version) {

    public CrawlSchedule {
        if (taskId < 1 || scheduleKey == null || scheduleKey.isBlank() || localTime == null
                || timeZone == null || incrementalMode == null || incrementalMode.isBlank() || version < 0) {
            throw new IllegalArgumentException("每日计划定义无效");
        }
    }

    public CrawlSchedule(
            long id,
            long taskId,
            String scheduleKey,
            LocalTime localTime,
            ZoneId timeZone,
            Instant nextFireAt,
            boolean enabled,
            long version) {
        this(id, taskId, scheduleKey, localTime, timeZone,
                "FIXED_SCOPE_REFRESH", nextFireAt, enabled, version);
    }
}
