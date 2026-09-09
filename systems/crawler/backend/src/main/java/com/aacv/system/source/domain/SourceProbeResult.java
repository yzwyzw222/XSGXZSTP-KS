package com.aacv.system.source.domain;

import java.time.Instant;
import java.util.Map;

public record SourceProbeResult(
        boolean reachable,
        Integer statusCode,
        String errorCategory,
        Map<String, String> rateLimitSummary,
        Instant checkedAt) {

    public SourceProbeResult {
        rateLimitSummary = rateLimitSummary == null ? Map.of() : Map.copyOf(rateLimitSummary);
        if (checkedAt == null) {
            throw new IllegalArgumentException("探测时间不能为空");
        }
        if (reachable && (statusCode == null || statusCode < 200 || statusCode >= 400)) {
            throw new IllegalArgumentException("可达探测必须包含成功HTTP状态");
        }
        if (!reachable && (errorCategory == null || errorCategory.isBlank())) {
            throw new IllegalArgumentException("失败探测必须包含错误分类");
        }
    }
}
