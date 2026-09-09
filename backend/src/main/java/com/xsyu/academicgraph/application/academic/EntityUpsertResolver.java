package com.xsyu.academicgraph.application.academic;

import com.xsyu.academicgraph.domain.academic.Author;
import com.xsyu.academicgraph.domain.academic.AuthorRepository;
import com.xsyu.academicgraph.domain.academic.Institution;
import com.xsyu.academicgraph.domain.academic.InstitutionRepository;
import com.xsyu.academicgraph.domain.academic.Keyword;
import com.xsyu.academicgraph.domain.academic.KeywordRepository;
import com.xsyu.academicgraph.domain.academic.Venue;
import com.xsyu.academicgraph.domain.academic.VenueRepository;
import com.xsyu.academicgraph.domain.extraction.ResearchEntity;
import com.xsyu.academicgraph.domain.extraction.ResearchEntityRepository;
import com.xsyu.academicgraph.domain.sync.GraphSyncEvent;
import com.xsyu.academicgraph.domain.sync.GraphSyncEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 实体"按名称 upsert"解析器——从 DataImportService 上移的公共逻辑。
 *
 * 两个调用方共用同一套去重规则：
 *  - 文件/在线导入（DataImportService）：作者/机构/关键词/渠道
 *  - LLM 抽取归并（ExtractionService）：人名→作者、机构名→机构、主题→关键词、方法/数据集/工具→研究实体
 * 都是"按名称精确查库 → 查不到就新建"，新建时发 Outbox UPSERT 事件让图同步器投影。
 * 返回 Resolved(entity, created)：调用方需要知道"这条是不是新建的"（导入要统计、抽取要发事件）。
 * cache 由调用方传入：一次批处理内同名对象只查一次库，跨调用方不复用（各自语义独立）。
 */
@Service
@RequiredArgsConstructor
public class EntityUpsertResolver {

    private final AuthorRepository authorRepository;
    private final InstitutionRepository institutionRepository;
    private final KeywordRepository keywordRepository;
    private final VenueRepository venueRepository;
    private final ResearchEntityRepository researchEntityRepository;
    private final GraphSyncEventRepository graphSyncEventRepository;

    /** 解析结果：entity 是查库复用或新建后的实体，created 标记这条是否是本次新建 */
    public record Resolved<T>(T entity, boolean created) {
    }

    public Resolved<Author> resolveAuthor(String name, String orcid, Map<String, Author> cache) {
        name = name.trim();
        Author existing = cache.get(name);
        if (existing != null) {
            return new Resolved<>(existing, false);
        }
        existing = authorRepository.findFirstByDisplayNameIgnoreCase(name).orElse(null);
        if (existing == null) {
            existing = new Author();
            existing.setDisplayName(name);
            existing.setOrcid(blankToNull(orcid));
            existing = authorRepository.save(existing);
            emitSyncEvent(GraphSyncEvent.ENTITY_AUTHOR, existing.getId());
            cache.put(name, existing);
            return new Resolved<>(existing, true);
        }
        cache.put(name, existing);
        return new Resolved<>(existing, false);
    }

    public Resolved<Institution> resolveInstitution(String name, String countryCode, Map<String, Institution> cache) {
        name = name.trim();
        Institution existing = cache.get(name);
        if (existing != null) {
            return new Resolved<>(existing, false);
        }
        existing = institutionRepository.findFirstByDisplayNameIgnoreCase(name).orElse(null);
        if (existing == null) {
            existing = new Institution();
            existing.setDisplayName(name);
            existing.setCountryCode(blankToNull(countryCode));
            existing = institutionRepository.save(existing);
            emitSyncEvent(GraphSyncEvent.ENTITY_INSTITUTION, existing.getId());
            cache.put(name, existing);
            return new Resolved<>(existing, true);
        }
        cache.put(name, existing);
        return new Resolved<>(existing, false);
    }

    public Resolved<Keyword> resolveKeyword(String name, String fieldName, Map<String, Keyword> cache) {
        name = name.trim();
        Keyword existing = cache.get(name);
        if (existing != null) {
            return new Resolved<>(existing, false);
        }
        existing = keywordRepository.findByName(name).orElse(null);
        if (existing == null) {
            existing = new Keyword();
            existing.setName(name);
            existing.setFieldName(blankToNull(fieldName));
            existing = keywordRepository.save(existing);
            emitSyncEvent(GraphSyncEvent.ENTITY_KEYWORD, existing.getId());
            cache.put(name, existing);
            return new Resolved<>(existing, true);
        }
        cache.put(name, existing);
        return new Resolved<>(existing, false);
    }

    public Resolved<Venue> resolveVenue(String name, String venueType, String issn, Map<String, Venue> cache) {
        name = name.trim();
        Venue existing = cache.get(name);
        if (existing != null) {
            return new Resolved<>(existing, false);
        }
        existing = venueRepository.findFirstByDisplayNameIgnoreCase(name).orElse(null);
        if (existing == null) {
            existing = new Venue();
            existing.setDisplayName(name);
            existing.setVenueType(blankToNull(venueType));
            existing.setIssn(blankToNull(issn));
            existing = venueRepository.save(existing);
            emitSyncEvent(GraphSyncEvent.ENTITY_VENUE, existing.getId());
            cache.put(name, existing);
            return new Resolved<>(existing, true);
        }
        cache.put(name, existing);
        return new Resolved<>(existing, false);
    }

    /**
     * 研究实体（METHOD/DATASET/TOOL）：同名同类视为同一个实体，同名不同类是两个实体。
     * 例如 "GPT-4" 作为 TOOL 与 "GPT-4" 作为 METHOD 会各建一行——类型本身就是语义的一部分。
     */
    public Resolved<ResearchEntity> resolveResearchEntity(String name, String entityType,
                                                          Map<String, ResearchEntity> cache) {
        name = name.trim();
        String cacheKey = entityType + ":" + name;
        ResearchEntity existing = cache.get(cacheKey);
        if (existing != null) {
            return new Resolved<>(existing, false);
        }
        existing = researchEntityRepository.findFirstByNameAndEntityType(name, entityType).orElse(null);
        if (existing == null) {
            existing = new ResearchEntity();
            existing.setName(name);
            existing.setEntityType(entityType);
            existing = researchEntityRepository.save(existing);
            emitSyncEvent(GraphSyncEvent.ENTITY_RESEARCH_ENTITY, existing.getId());
            cache.put(cacheKey, existing);
            return new Resolved<>(existing, true);
        }
        cache.put(cacheKey, existing);
        return new Resolved<>(existing, false);
    }

    /** 发 Outbox 事件：与业务改动同事务提交，图同步器稍后轮询投影到 Neo4j */
    private void emitSyncEvent(String entityType, Long entityId) {
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setEventType(GraphSyncEvent.EVENT_UPSERT);
        graphSyncEventRepository.save(event);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
