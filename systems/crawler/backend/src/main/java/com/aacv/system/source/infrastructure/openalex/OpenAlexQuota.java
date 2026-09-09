package com.aacv.system.source.infrastructure.openalex;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

final class OpenAlexQuota {

    private OpenAlexQuota() {
    }

    static Instant resetAt(Map<String, String> metadata, Instant now) {
        String remaining = metadata.get("X-RateLimit-Remaining");
        String reset = metadata.get("X-RateLimit-Reset");
        if (remaining == null || reset == null || remaining.length() > 128 || reset.length() > 128) {
            return null;
        }
        try {
            long seconds = Long.parseLong(reset.trim());
            // 两个额度头同时有效才延后任务；缺失或无效头按普通429进行有界重试。
            if (new BigDecimal(remaining.trim()).signum() <= 0 && seconds >= 0 && seconds <= 86_400) {
                return now.plusSeconds(seconds + 5);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }
}
