package com.aacv.system.catalog.api;

import com.aacv.system.catalog.domain.CatalogEntityItem;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;

public record CatalogEntityPageResponse(
        List<CatalogEntityResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static CatalogEntityPageResponse from(PageResult<CatalogEntityItem> result) {
        return new CatalogEntityPageResponse(
                result.items().stream()
                        .map(item -> new CatalogEntityResponse(
                                item.id(),
                                item.externalId(),
                                item.displayName(),
                                item.entityType(),
                                item.achievementCount()))
                        .toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    public record CatalogEntityResponse(
            long id,
            String externalId,
            String displayName,
            String entityType,
            long achievementCount) {
    }
}
