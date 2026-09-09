package com.aacv.system.crawl.domain;

public record CrawlCheckpointState(
        String cursor,
        int committedPages,
        long committedRecords,
        long version) {

    public CrawlCheckpointState {
        if (cursor == null || cursor.isBlank() || committedPages < 0 || committedRecords < 0 || version < 0) {
            throw new IllegalArgumentException("采集检查点状态无效");
        }
    }
}
