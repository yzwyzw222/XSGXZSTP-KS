package com.aacv.system.crawl.api;

import com.aacv.system.crawl.domain.CrawlSchedule;
import java.time.Instant;

public record CrawlScheduleResponse(
        long taskId,
        String localTime,
        String timeZone,
        Instant nextFireAt,
        long version,
        String incrementalMode) {

    static CrawlScheduleResponse from(CrawlSchedule schedule) {
        return new CrawlScheduleResponse(
                schedule.taskId(),
                schedule.localTime().toString(),
                schedule.timeZone().getId(),
                schedule.nextFireAt(),
                schedule.version(),
                schedule.incrementalMode());
    }
}
