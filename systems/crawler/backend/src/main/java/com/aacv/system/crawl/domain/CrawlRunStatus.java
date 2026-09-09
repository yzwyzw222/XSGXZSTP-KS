package com.aacv.system.crawl.domain;

public enum CrawlRunStatus {
    PENDING,
    RUNNING,
    PAUSING,
    PAUSED,
    SUCCEEDED,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLING,
    CANCELLED
}
