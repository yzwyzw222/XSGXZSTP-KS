package com.aacv.system.crawl.domain;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record CrawlScope(
        LocalDate publicationDateFrom,
        LocalDate publicationDateTo,
        String keyword,
        List<String> authorIds,
        List<String> institutionIds,
        List<String> dois,
        List<String> orcids,
        List<String> rorIds,
        Instant updatedFrom,
        Instant updatedUntil,
        int maxPages,
        int maxRecords) {

    public CrawlScope {
        keyword = normalizeOptional(keyword, 200, "关键词");
        authorIds = normalizeIds(authorIds, 128, "作者ID");
        institutionIds = normalizeIds(institutionIds, 128, "机构ID");
        dois = normalizeIds(dois, 255, "DOI");
        orcids = normalizeIds(orcids, 64, "ORCID");
        rorIds = normalizeIds(rorIds, 128, "ROR");
        if (publicationDateFrom != null && publicationDateTo != null
                && publicationDateFrom.isAfter(publicationDateTo)) {
            throw new IllegalArgumentException("发表日期开始时间不能晚于结束时间");
        }
        if ((updatedFrom == null) != (updatedUntil == null)) {
            throw new IllegalArgumentException("更新时间窗口必须同时提供起止值");
        }
        if (updatedFrom != null && updatedFrom.isAfter(updatedUntil)) {
            throw new IllegalArgumentException("更新时间窗口开始时间不能晚于结束时间");
        }
        if (maxPages < 1 || maxPages > 5) {
            throw new IllegalArgumentException("最大页数必须在1至5之间");
        }
        if (maxRecords < 1 || maxRecords > 500) {
            throw new IllegalArgumentException("最大记录数必须在1至500之间");
        }
    }

    public CrawlScope(
            LocalDate publicationDateFrom,
            LocalDate publicationDateTo,
            String keyword,
            List<String> authorIds,
            List<String> institutionIds,
            int maxPages,
            int maxRecords) {
        this(publicationDateFrom, publicationDateTo, keyword, authorIds, institutionIds,
                List.of(), List.of(), List.of(), null, null, maxPages, maxRecords);
    }

    private static String normalizeOptional(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度超出限制");
        }
        return normalized;
    }

    private static List<String> normalizeIds(List<String> values, int maxLength, String fieldName) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > 50) {
            throw new IllegalArgumentException(fieldName + "数量不能超过50个");
        }
        List<String> normalized = values.stream()
                .map(value -> normalizeOptional(value, maxLength, fieldName))
                .toList();
        if (normalized.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(fieldName + "不能包含空值");
        }
        return normalized.stream().distinct().toList();
    }
}
