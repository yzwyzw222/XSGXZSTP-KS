package com.aacv.system.crawl.domain;

import java.time.Instant;

/** 保存每次执行的实际窗口，任务定义和历史运行不会随每日日期推进而改变。 */
public record CrawlWindow(String mode, Instant start, Instant end, CrawlScope scope) {
    public CrawlWindow {
        if (mode == null || !java.util.Set.of("ROLLING_PUBLICATION_DATE_WINDOW", "CLOSED_INDEX_DATE_WINDOW").contains(mode)
                || start == null || end == null || !start.isBefore(end) || scope == null) {
            throw new IllegalArgumentException("采集增量窗口无效");
        }
    }
}
