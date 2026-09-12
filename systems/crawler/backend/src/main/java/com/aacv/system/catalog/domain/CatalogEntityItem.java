package com.aacv.system.catalog.domain;

import java.util.List;

public record CatalogEntityItem(
        long id,
        String externalId,
        String displayName,
        String entityType,
        long achievementCount,
        List<String> advisors) {
}
