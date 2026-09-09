package com.aacv.system.analytics.domain;

import com.aacv.system.source.domain.SourceType;

public record AnalyticsQuery(
        Integer publicationYearFrom,
        Integer publicationYearTo,
        String achievementType,
        SourceType sourceType,
        Long organizationId,
        Long topicId) {

    public AnalyticsQuery {
        if (publicationYearFrom != null && (publicationYearFrom < 1000 || publicationYearFrom > 9999)
                || publicationYearTo != null && (publicationYearTo < 1000 || publicationYearTo > 9999)) {
            throw new IllegalArgumentException("发表年份无效");
        }
        if (publicationYearFrom != null && publicationYearTo != null
                && publicationYearFrom > publicationYearTo) {
            throw new IllegalArgumentException("起始年份不能晚于结束年份");
        }
        if (organizationId != null && organizationId < 1 || topicId != null && topicId < 1) {
            throw new IllegalArgumentException("统计实体ID无效");
        }
        achievementType = normalize(achievementType);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("成果类型长度超出限制");
        }
        return normalized;
    }
}
