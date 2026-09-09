package com.aacv.system.export.domain;

import com.aacv.system.source.domain.SourceType;

public record ExportFilter(
        String title,
        Long authorId,
        Long organizationId,
        Integer publicationYearFrom,
        Integer publicationYearTo,
        String achievementType,
        SourceType sourceType,
        Long venueId,
        Long topicId) {

    public ExportFilter {
        title = normalize(title, 512, "题名条件长度超出限制");
        achievementType = normalize(achievementType, 64, "成果类型长度超出限制");
        if (publicationYearFrom != null && (publicationYearFrom < 1000 || publicationYearFrom > 9999)
                || publicationYearTo != null && (publicationYearTo < 1000 || publicationYearTo > 9999)) {
            throw new IllegalArgumentException("发表年份无效");
        }
        if (publicationYearFrom != null && publicationYearTo != null
                && publicationYearFrom > publicationYearTo) {
            throw new IllegalArgumentException("起始年份不能晚于结束年份");
        }
        if (invalidId(authorId) || invalidId(organizationId) || invalidId(venueId) || invalidId(topicId)) {
            throw new IllegalArgumentException("导出筛选实体ID无效");
        }
    }

    private static boolean invalidId(Long value) {
        return value != null && value < 1;
    }

    private static String normalize(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
