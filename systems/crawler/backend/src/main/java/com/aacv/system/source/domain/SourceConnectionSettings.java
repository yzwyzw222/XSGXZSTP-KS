package com.aacv.system.source.domain;

import java.time.Duration;

public record SourceConnectionSettings(
        int requestsPerSecond,
        int maxConcurrency,
        Duration connectTimeout,
        Duration responseTimeout,
        int maxRetries,
        int maxResponseBytes) {

    public SourceConnectionSettings {
        if (requestsPerSecond < 1 || requestsPerSecond > 10) {
            throw new IllegalArgumentException("每秒请求数必须在1至10之间");
        }
        if (maxConcurrency < 1 || maxConcurrency > 4) {
            throw new IllegalArgumentException("单来源并发数必须在1至4之间");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || connectTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("连接超时必须大于0且不超过30秒");
        }
        if (responseTimeout == null || responseTimeout.isNegative() || responseTimeout.isZero()
                || responseTimeout.compareTo(Duration.ofSeconds(120)) > 0) {
            throw new IllegalArgumentException("响应超时必须大于0且不超过120秒");
        }
        if (maxRetries < 0 || maxRetries > 5) {
            throw new IllegalArgumentException("最大重试次数必须在0至5之间");
        }
        if (maxResponseBytes < 1024 || maxResponseBytes > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("最大响应大小必须在1KB至20MB之间");
        }
    }
}
