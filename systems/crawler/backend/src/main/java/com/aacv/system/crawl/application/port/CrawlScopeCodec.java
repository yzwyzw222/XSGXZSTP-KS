package com.aacv.system.crawl.application.port;

import com.aacv.system.crawl.domain.CrawlScope;

public interface CrawlScopeCodec {
    String encode(CrawlScope scope);
    CrawlScope decode(String json);
    String hash(CrawlScope scope);
}
