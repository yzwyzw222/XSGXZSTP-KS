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
        int size) {

    public CatalogQuery {
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
