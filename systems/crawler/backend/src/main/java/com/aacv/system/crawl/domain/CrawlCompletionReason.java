package com.aacv.system.crawl.domain;

public enum CrawlCompletionReason {
    SOURCE_EXHAUSTED,
    PAGE_LIMIT,
    RECORD_LIMIT,
    RETRY_BATCH_COMPLETED,
    QUOTA_EXHAUSTED,
    QUOTA_RETRY_LIMIT,
    USER_PAUSED,
    USER_CANCELLED,
    BATCH_FAILED;

    public boolean limited() {
        return this == PAGE_LIMIT || this == RECORD_LIMIT;
    }
}
