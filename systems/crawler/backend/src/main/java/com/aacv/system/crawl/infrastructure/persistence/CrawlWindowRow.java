package com.aacv.system.crawl.infrastructure.persistence;

import java.time.Instant;

public record CrawlWindowRow(String mode, Instant windowStart, Instant windowEnd, String parametersJson) { }
