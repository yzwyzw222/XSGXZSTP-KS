package com.aacv.system.catalog.infrastructure.persistence;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.aacv.system.source.domain.ScholarlyMetadata;
import com.aacv.system.catalog.domain.CatalogEntityEvidence;
import com.aacv.system.catalog.application.port.CatalogRepository;
import com.aacv.system.catalog.domain.AchievementCatalogDetail;
import com.aacv.system.catalog.domain.AchievementCatalogDetail.Authorship;
import com.aacv.system.catalog.domain.AchievementCatalogDetail.Organization;
import com.aacv.system.catalog.domain.AchievementCatalogDetail.SourceTrace;
import com.aacv.system.catalog.domain.AchievementCatalogDetail.FieldState;
import com.aacv.system.catalog.domain.AchievementCatalogItem;
import com.aacv.system.catalog.domain.CatalogEntityItem;
import com.aacv.system.catalog.domain.CatalogEntityKind;
import com.aacv.system.catalog.domain.CatalogQuery;
import com.aacv.system.shared.domain.PageResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class MyBatisCatalogRepository implements CatalogRepository {

    private final CatalogMapper mapper;
    private final ObjectMapper objectMapper;

    MyBatisCatalogRepository(CatalogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<AchievementCatalogItem> findAchievements(
            CatalogQuery query, CatalogEntityKind relatedKind, Long relatedId) {
        String kind = relatedKind == null ? null : relatedKind.name();
        long total = mapper.countAchievements(query, kind, relatedId);
        List<CatalogRow> rows = mapper.findAchievementPage(
                query, kind, relatedId, (long) query.page() * query.size(), query.size());
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), query.page(), query.size(), total);
        }
        List<Long> ids = rows.stream().map(CatalogRow::getId).toList();
        Map<Long, List<String>> authors = namesByAchievement(mapper.findAchievementAuthors(ids));
        Map<Long, List<String>> topics = namesByAchievement(mapper.findAchievementTopics(ids));
        List<AchievementCatalogItem> items = rows.stream()
                .map(row -> toItem(
                        row,
                        authors.getOrDefault(row.getId(), List.of()),
                        topics.getOrDefault(row.getId(), List.of())))
                .toList();
        return PageResult.of(items, query.page(), query.size(), total);
    }

    @Override
    public Optional<AchievementCatalogDetail> findAchievement(long achievementId) {
        CatalogRow base = mapper.findAchievementBase(achievementId);
        if (base == null) {
            return Optional.empty();
        }
        long canonicalId = base.getId();
        List<CatalogRow> authorRows = mapper.findAchievementAuthors(List.of(canonicalId));
        List<CatalogRow> topicRows = mapper.findAchievementTopics(List.of(canonicalId));
        Map<Long, List<Organization>> organizations = new LinkedHashMap<>();
        for (CatalogRow row : mapper.findAuthorshipOrganizations(canonicalId)) {
            organizations.computeIfAbsent(row.getEntityId(), ignored -> new ArrayList<>())
                    .add(new Organization(
                            row.getOrganizationId(),
                            row.getOrganizationExternalId(),
                            row.getOrganizationName()));
        }
        List<Authorship> authorships = authorRows.stream()
                .map(row -> new Authorship(
                        row.getEntityId(),
                        row.getExternalId(),
                        row.getOrcid(),
                        row.getDisplayName(),
                        row.getPosition(),
                        organizations.getOrDefault(row.getEntityId(), List.of())))
                .toList();
        List<SourceTrace> sources = mapper.findAchievementSources(canonicalId).stream()
                .map(row -> new SourceTrace(
                        row.getId(),
                        row.getRawRecordId(),
                        row.getSourceCode(),
                        row.getExternalRecordId(),
                        row.getSourceUrl(),
                        row.getFirstSeenAt(),
                        row.getLastSeenAt(),
                        row.getParserVersion(), scholarlyMetadata(row.getScholarlyMetadata())))
                .toList();
        AchievementCatalogItem summary = toItem(
                base,
                authorRows.stream().map(CatalogRow::getDisplayName).toList(),
                topicRows.stream().map(CatalogRow::getDisplayName).toList());
        return Optional.of(new AchievementCatalogDetail(
                summary,
                base.getLanguage(),
                base.getAbstractText(),
                base.isAuthorshipsMayBeIncomplete(),
                authorships,
                mapper.findAchievementReferences(canonicalId).stream()
                        .map(CatalogRow::getReferencedExternalWorkId)
                        .toList(),
                sources,
                mapper.findAchievementFieldStates(canonicalId).stream()
                        .map(row -> new FieldState(
                                row.getFieldName(), row.getSourceCode(),
                                row.getRawRecordId() == 0 ? null : row.getRawRecordId(),
                                row.isManualOverride()))
                        .toList()));
    }

    private ScholarlyMetadata scholarlyMetadata(String json) {
        if (json == null || "null".equals(json)) return null;
        try {
            return objectMapper.readValue(json, ScholarlyMetadata.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("来源学术指标快照无效", exception);
        }
    }

    @Override
    public PageResult<CatalogEntityItem> findEntities(
            CatalogEntityKind kind, String name, int page, int size) {
        if (kind == null || page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("目录实体分页参数无效");
        }
        String normalizedName = normalizeName(name);
        long total = mapper.countEntities(kind.name(), normalizedName);
        List<CatalogEntityItem> items = mapper.findEntityPage(
                        kind.name(), normalizedName, (long) page * size, size)
                .stream()
                .map(row -> new CatalogEntityItem(
                        row.getId(),
                        row.getExternalId(),
                        row.getDisplayName(),
                        row.getEntityType(),
                        row.getAchievementCount()))
                .toList();
        return PageResult.of(items, page, size, total);
    }

    @Override
    public Optional<CatalogEntityEvidence> findEntityEvidence(CatalogEntityKind kind, long entityId) {
        if (kind != CatalogEntityKind.AUTHOR && kind != CatalogEntityKind.ORGANIZATION) {
            throw new IllegalArgumentException("仅支持作者或机构的证据查询");
        }
        Long canonicalId = mapper.findEvidenceEntityId(kind.name(), entityId);
        if (canonicalId == null) return Optional.empty();
        var names = kind == CatalogEntityKind.ORGANIZATION ? mapper.findOrganizationNames(canonicalId) : List.<CatalogEvidenceRow>of();
        var affiliations = kind == CatalogEntityKind.AUTHOR ? mapper.findAuthorAffiliations(canonicalId) : List.<CatalogEvidenceRow>of();
        return Optional.of(new CatalogEntityEvidence(canonicalId, kind,
                names.stream().limit(100).map(row -> new CatalogEntityEvidence.OrganizationName(
                        row.getDisplayName(), row.getSourceCode(), row.getFirstObservedAt(), row.getLastObservedAt())).toList(),
                affiliations.stream().limit(100).map(row -> new CatalogEntityEvidence.AffiliationObservation(
                        row.getOrganizationId(), row.getDisplayName(), row.getFirstPublicationYear(), row.getLastPublicationYear(),
                        row.getAchievementCount(), row.getDatedAchievementCount())).toList(),
                names.size() > 100, affiliations.size() > 100));
    }

    private AchievementCatalogItem toItem(
            CatalogRow row, List<String> authors, List<String> topics) {
        return new AchievementCatalogItem(
                row.getId(),
                row.getTitle(),
                row.getDoi(),
                row.getAchievementType(),
                row.getPublicationDate(),
                row.getPrimaryVenue(),
                authors,
                topics);
    }

    private Map<Long, List<String>> namesByAchievement(List<CatalogRow> rows) {
        Map<Long, List<String>> names = new LinkedHashMap<>();
        for (CatalogRow row : rows) {
            names.computeIfAbsent(row.getAchievementId(), ignored -> new ArrayList<>())
                    .add(row.getDisplayName());
        }
        return names;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("名称检索条件长度超出限制");
        }
        return normalized;
    }
}
