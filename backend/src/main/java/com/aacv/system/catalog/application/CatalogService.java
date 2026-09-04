package com.aacv.system.catalog.application;

import com.aacv.system.catalog.application.port.CatalogRepository;
import com.aacv.system.catalog.domain.AchievementCatalogDetail;
import com.aacv.system.catalog.domain.AchievementCatalogItem;
import com.aacv.system.catalog.domain.CatalogEntityItem;
import com.aacv.system.catalog.domain.CatalogEntityKind;
import com.aacv.system.catalog.domain.CatalogQuery;
import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.shared.domain.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public PageResult<AchievementCatalogItem> findAchievements(CatalogQuery query) {
        return repository.findAchievements(query, null, null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public PageResult<AchievementCatalogItem> findRelatedAchievements(
            CatalogEntityKind kind, long entityId, CatalogQuery query) {
        if (entityId < 1) {
            throw new IllegalArgumentException("目录实体ID无效");
        }
        return repository.findAchievements(query, kind, entityId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public AchievementCatalogDetail requireAchievement(long achievementId) {
        return repository.findAchievement(achievementId)
                .orElseThrow(() -> new ResourceNotFoundException("成果不存在"));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public PageResult<CatalogEntityItem> findEntities(
            CatalogEntityKind kind, String name, int page, int size) {
        return repository.findEntities(kind, name, page, size);
    }
}
