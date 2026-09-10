package com.aacv.system.crawl.domain;

/** 运行级失败只保存安全类别和固定建议，不保存原始异常或请求参数。 */
public record CrawlExecutionFailure(String stage, String category, String message) {
    public CrawlExecutionFailure {
        if (stage == null || !java.util.Set.of("FETCH", "PARSE", "VALIDATE", "PERSIST", "SYSTEM").contains(stage)
                || category == null || !category.matches("[A-Z0-9_]{1,64}")
                || message == null || message.isBlank() || message.length() > 1000) {
            throw new IllegalArgumentException("运行失败分类无效");
        }
    }
}
