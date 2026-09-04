package com.aacv.system.crawl.application.port;

import com.aacv.system.crawl.domain.CrawlSchedule;

public interface CrawlSchedulePort {

    void synchronizeAfterCommit(CrawlSchedule schedule);
}
