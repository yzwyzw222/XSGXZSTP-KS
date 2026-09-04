package com.aacv.system.crawl.infrastructure.batch;

import com.aacv.system.source.domain.SourcePage;

record CrawlPageItem(SourcePage page, Long retryFailureId) {

    CrawlPageItem {
        if (page == null || (retryFailureId != null && retryFailureId < 1)) {
            throw new IllegalArgumentException("Batch页面项无效");
        }
    }
}
