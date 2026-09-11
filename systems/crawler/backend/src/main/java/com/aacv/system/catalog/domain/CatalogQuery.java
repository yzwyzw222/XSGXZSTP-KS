package com.aacv.system.catalog.domain;

public record CatalogQuery(
        String title,
        String author,
        String organization,
        Integer publicationYear,
        String achievementType,
        String sourceCode,
        String venue,
        String topic,
        int page,
        int size,
        Long authorId,
        Long organizationId,
        Long venueId,
        Long topicId) {

    public CatalogQuery(String title, String author, String organization, Integer publicationYear,
            String achievementType, String sourceCode, String venue, String topic, int page, int size) {
        this(title, author, organization, publicationYear, achievementType, sourceCode, venue, topic,
                page, size, null, null, null, null);
    }

    // 目录和导出共用筛选 SQL；目录的单年条件等价于闭合年份区间。
    public Integer publicationYearFrom() { return publicationYear; }
    public Integer publicationYearTo() { return publicationYear; }
    public String sourceType() { return null; }

    public CatalogQuery {
        for (Long id : new Long[] {authorId, organizationId, venueId, topicId}) {
            if (id != null && id < 1) throw new IllegalArgumentException("检索实体ID无效");
        }
        if (publicationYear != null && (publicationYear < 1000 || publicationYear > 9999)) {
            throw new IllegalArgumentException("发表年份无效");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        title = normalize(title);
        author = normalize(author);
        organization = normalize(organization);
        achievementType = normalize(achievementType);
        sourceCode = normalize(sourceCode);
        venue = normalize(venue);
        topic = normalize(topic);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("检索条件长度超出限制");
        }
        return normalized;
    }
}
