package com.aacv.system.catalog.domain;

public record CatalogEntityItem(
        long id,
        String externalId,
        String displayName,
        String entityType,
        long achievementCount) {
}
