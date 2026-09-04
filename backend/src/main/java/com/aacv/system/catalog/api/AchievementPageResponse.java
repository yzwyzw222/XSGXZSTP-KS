package com.aacv.system.catalog.api;

import com.aacv.system.catalog.domain.AchievementCatalogItem;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;

public record AchievementPageResponse(
        List<AchievementSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static AchievementPageResponse from(PageResult<AchievementCatalogItem> result) {
        return new AchievementPageResponse(
                result.items().stream().map(AchievementPageResponse::toSummary).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    static AchievementSummaryResponse toSummary(AchievementCatalogItem item) {
        return new AchievementSummaryResponse(
                item.id(),
                item.title(),
                item.doi(),
                item.achievementType(),
                item.publicationDate(),
                item.primaryVenue(),
                item.authors(),
                item.topics());
    }
}
