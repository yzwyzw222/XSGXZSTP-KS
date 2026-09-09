package com.aacv.system.crawl.application.port;

public interface CrawlRunLaunchPort {

    void launchAfterCommit(long runId);
}
