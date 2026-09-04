package com.aacv.system.catalog.application.port;

import com.aacv.system.catalog.domain.AchievementCatalogDetail;
import com.aacv.system.catalog.domain.AchievementCatalogItem;
import com.aacv.system.catalog.domain.CatalogEntityItem;
import com.aacv.system.catalog.domain.CatalogEntityKind;
import com.aacv.system.catalog.domain.CatalogQuery;
import com.aacv.system.shared.domain.PageResult;
import java.util.Optional;

public interface CatalogRepository {

    PageResult<AchievementCatalogItem> findAchievements(
            CatalogQuery query, CatalogEntityKind relatedKind, Long relatedId);

    Optional<AchievementCatalogDetail> findAchievement(long achievementId);

    PageResult<CatalogEntityItem> findEntities(
            CatalogEntityKind kind, String name, int page, int size);
}
