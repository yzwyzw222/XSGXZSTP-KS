package com.aacv.system.catalog.infrastructure.persistence;

import com.aacv.system.catalog.domain.CatalogQuery;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface CatalogMapper {
    long countAchievements(
            @Param("query") CatalogQuery query,
            @Param("relatedKind") String relatedKind,
            @Param("relatedId") Long relatedId);
    List<CatalogRow> findAchievementPage(
            @Param("query") CatalogQuery query,
            @Param("relatedKind") String relatedKind,
            @Param("relatedId") Long relatedId,
            @Param("offset") long offset,
            @Param("size") int size);
    CatalogRow findAchievementBase(@Param("id") long id);
    List<CatalogRow> findAchievementAuthors(@Param("ids") List<Long> ids);
    List<CatalogRow> findAchievementTopics(@Param("ids") List<Long> ids);
    List<CatalogRow> findAuthorshipOrganizations(@Param("id") long id);
    List<CatalogRow> findAchievementSources(@Param("id") long id);
    List<CatalogRow> findAchievementReferences(@Param("id") long id);
    List<CatalogRow> findAchievementFieldStates(@Param("id") long id);
    long countEntities(@Param("kind") String kind, @Param("name") String name);
    Long findEvidenceEntityId(@Param("kind") String kind, @Param("entityId") long entityId);
    List<CatalogEvidenceRow> findOrganizationNames(long entityId);
    List<CatalogEvidenceRow> findAuthorAffiliations(long entityId);
    List<CatalogRow> findEntityPage(
            @Param("kind") String kind,
            @Param("name") String name,
            @Param("offset") long offset,
            @Param("size") int size);
}
