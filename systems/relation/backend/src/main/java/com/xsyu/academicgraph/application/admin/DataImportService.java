package com.xsyu.academicgraph.application.admin;

import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportAuthorItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportInstitutionItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportKeywordItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportPaperItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportReferenceItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportRequest;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportResultRow;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportSummary;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportVenueItem;
import com.xsyu.academicgraph.application.academic.EntityUpsertResolver;
import com.xsyu.academicgraph.domain.academic.Author;
import com.xsyu.academicgraph.domain.academic.Institution;
import com.xsyu.academicgraph.domain.academic.Keyword;
import com.xsyu.academicgraph.domain.academic.Paper;
import com.xsyu.academicgraph.domain.academic.PaperAuthor;
import com.xsyu.academicgraph.domain.academic.PaperAuthorRepository;
import com.xsyu.academicgraph.domain.academic.PaperKeyword;
import com.xsyu.academicgraph.domain.academic.PaperKeywordRepository;
import com.xsyu.academicgraph.domain.academic.PaperReference;
import com.xsyu.academicgraph.domain.academic.PaperReferenceRepository;
import com.xsyu.academicgraph.domain.academic.PaperRepository;
import com.xsyu.academicgraph.domain.academic.Venue;
import com.xsyu.academicgraph.domain.sync.GraphSyncEvent;
import com.xsyu.academicgraph.domain.sync.GraphSyncEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 数据导入应用服务（管理员功能）：把「论文为中心」的嵌套 JSON 批量写进 MySQL。
 *
 * 设计要点：
 *  - 按名称去重复用：作者/机构/关键词/渠道先用名称精确查库，查不到才新建；
 *    同一文件内重复出现的名称用内存缓存合并，避免重复查询与重复创建。
 *  - 论文判重：DOI 或标题已存在 ⇒ 该条记为 SKIPPED（幂等，同一文件可重复导入）。
 *  - 逐条隔离：单篇失败（标题为空/类型非法/数据库冲突）只记 FAILED，不影响其他篇。
 *  - 事务性 Outbox：每新建一篇论文/一个实体都发 UPSERT 事件，图投影由
 *    GraphSyncService 稍后自动同步（与 PaperService 的写路径同构）。
 */
@Service
@RequiredArgsConstructor
public class DataImportService {

    /** 论文类型白名单：与数据库 ck_paper_type 检查约束保持一致 */
    private static final Set<String> VALID_PAPER_TYPES = Set.of(
            Paper.TYPE_JOURNAL, Paper.TYPE_CONFERENCE, Paper.TYPE_PATENT, Paper.TYPE_OTHER);

    private final PaperRepository paperRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PaperReferenceRepository paperReferenceRepository;
    private final GraphSyncEventRepository graphSyncEventRepository;
    /** 作者/机构/关键词/渠道的按名称 upsert 逻辑（与 LLM 抽取归并共用，见 EntityUpsertResolver） */
    private final EntityUpsertResolver entityUpsertResolver;

    /** 本次导入新建的关联对象计数（汇总给前端展示） */
    private int createdAuthors;
    private int createdInstitutions;
    private int createdKeywords;
    private int createdVenues;

    /**
     * 执行批量导入。整体一个事务：全部成功一起提交，单条失败只在该条结果行里体现。
     * 注意：异常在本方法内部被逐条捕获，不会穿过本 Bean 的事务代理，因此
     * 不会触发"标记回滚"导致整批失败。
     */
    @Transactional
    public ImportSummary importPapers(ImportRequest request) {
        List<ImportPaperItem> items = request.papers();
        createdAuthors = 0;
        createdInstitutions = 0;
        createdKeywords = 0;
        createdVenues = 0;

        // 名称 → 实体缓存：同一文件内同名对象只查一次库、只建一次
        Map<String, Author> authorsByName = new HashMap<>();
        Map<String, Institution> institutionsByName = new HashMap<>();
        Map<String, Keyword> keywordsByName = new HashMap<>();
        Map<String, Venue> venuesByName = new HashMap<>();

        List<ImportResultRow> rows = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        int failed = 0;

        for (int i = 0; i < items.size(); i++) {
            ImportPaperItem item = items.get(i);
            try {
                String title = item.title() == null ? "" : item.title().trim();
                if (title.isEmpty()) {
                    rows.add(new ImportResultRow(i + 1, "FAILED", "论文标题不能为空", null, null));
                    failed++;
                    continue;
                }
                // 判重：DOI 优先，其次标题（忽略大小写）——已存在则跳过，保证重复导入幂等
                Optional<Paper> duplicate = item.doi() != null && !item.doi().isBlank()
                        ? paperRepository.findByDoi(item.doi().trim())
                        : Optional.empty();
                if (duplicate.isEmpty()) {
                    duplicate = paperRepository.findFirstByTitleIgnoreCase(title);
                }
                if (duplicate.isPresent()) {
                    rows.add(new ImportResultRow(i + 1, "SKIPPED",
                            "已存在同 DOI/同标题论文《" + duplicate.get().getTitle() + "》",
                            duplicate.get().getId(), title));
                    skipped++;
                    continue;
                }

                // 渠道：按名称复用或新建，拿 id 挂到论文上
                Long venueId = null;
                if (item.venue() != null && hasText(item.venue().name())) {
                    EntityUpsertResolver.Resolved<Venue> resolved = entityUpsertResolver.resolveVenue(
                            item.venue().name(), item.venue().venueType(), item.venue().issn(), venuesByName);
                    if (resolved.created()) {
                        createdVenues++;
                    }
                    venueId = resolved.entity().getId();
                }
                // 关键词：逐个按名称复用或新建
                List<Long> keywordIds = new ArrayList<>();
                for (ImportKeywordItem k : item.keywords() == null ? List.<ImportKeywordItem>of() : item.keywords()) {
                    if (!hasText(k.name())) {
                        continue;
                    }
                    EntityUpsertResolver.Resolved<Keyword> resolved = entityUpsertResolver.resolveKeyword(
                            k.name(), k.fieldName(), keywordsByName);
                    if (resolved.created()) {
                        createdKeywords++;
                    }
                    keywordIds.add(resolved.entity().getId());
                }
                // 作者：逐个复用或新建，署名机构一并解析；位次按数组顺序 1 起编
                List<ImportAuthorItem> authors = item.authors();
                if (authors == null || authors.isEmpty()) {
                    rows.add(new ImportResultRow(i + 1, "FAILED", "论文必须至少有一位署名作者", null, title));
                    failed++;
                    continue;
                }
                List<PaperAuthor> authorRows = new ArrayList<>();
                int position = 1;
                for (ImportAuthorItem a : authors) {
                    if (!hasText(a.name())) {
                        continue;
                    }
                    Long institutionId = null;
                    if (a.institution() != null && hasText(a.institution().name())) {
                        EntityUpsertResolver.Resolved<Institution> resolvedInst = entityUpsertResolver.resolveInstitution(
                                a.institution().name(), a.institution().countryCode(), institutionsByName);
                        if (resolvedInst.created()) {
                            createdInstitutions++;
                        }
                        institutionId = resolvedInst.entity().getId();
                    }
                    EntityUpsertResolver.Resolved<Author> resolvedAuthor = entityUpsertResolver.resolveAuthor(
                            a.name(), a.orcid(), authorsByName);
                    if (resolvedAuthor.created()) {
                        createdAuthors++;
                    }
                    Author author = resolvedAuthor.entity();
                    PaperAuthor pa = new PaperAuthor();
                    pa.setAuthorId(author.getId());
                    pa.setAuthorPosition(position++);
                    pa.setInstitutionId(institutionId);
                    authorRows.add(pa);
                }

                // 论文主表 + 两条关系 + Outbox 事件（与 PaperService.create 同构）
                Paper paper = new Paper();
                paper.setTitle(title);
                paper.setDoi(blankToNull(item.doi()));
                String type = item.paperType() == null || item.paperType().isBlank()
                        ? Paper.TYPE_JOURNAL : item.paperType().trim();
                // 提前校验类型白名单：非法值挡成该条 FAILED，而不是穿透到数据库检查约束
                if (!VALID_PAPER_TYPES.contains(type)) {
                    throw new IllegalArgumentException("论文类型不合法: " + type
                            + "，可选值: " + String.join("/", VALID_PAPER_TYPES));
                }
                paper.setPaperType(type);
                paper.setLanguage(blankToNull(item.language()));
                paper.setPublicationDate(item.publicationDate());
                paper.setAbstractText(blankToNull(item.abstractText()));
                paper.setCitationCount(item.citationCount() == null ? 0 : item.citationCount());
                paper.setVenueId(venueId);
                paper.setVolume(blankToNull(item.volume()));
                paper.setPeriod(blankToNull(item.period()));
                paper.setPageCount(blankToNull(item.pageCount()));
                paper.setClcNumber(blankToNull(item.clcNumber()));
                paper.setUrl(blankToNull(item.url()));
                paper = paperRepository.save(paper);

                for (PaperAuthor pa : authorRows) {
                    pa.setPaperId(paper.getId());
                    paperAuthorRepository.save(pa);
                }
                int keywordPosition = 1;
                for (Long keywordId : keywordIds) {
                    PaperKeyword pk = new PaperKeyword();
                    pk.setPaperId(paper.getId());
                    pk.setKeywordId(keywordId);
                    pk.setKeywordPosition(keywordPosition++);
                    paperKeywordRepository.save(pk);
                }
                // 外部引用线索（爬虫的 OpenAlex URL / 导入文件的 DOI）：被引文献不在库内时
                // cited_paper_id 留空，只记 external_cited_doi，图谱投影据此画"外部引用"边。
                // 同一篇论文内按线索去重，避免撞上 uk_paper_reference 唯一约束导致整条 FAILED
                if (item.references() != null) {
                    Set<String> seenDois = new HashSet<>();
                    for (ImportReferenceItem ref : item.references()) {
                        if (!hasText(ref.externalDoi()) || !seenDois.add(ref.externalDoi().trim())) {
                            continue;
                        }
                        PaperReference pr = new PaperReference();
                        pr.setCitingPaperId(paper.getId());
                        pr.setCitedPaperId(null);
                        pr.setExternalCitedDoi(ref.externalDoi().trim());
                        paperReferenceRepository.save(pr);
                    }
                }
                emitSyncEvent(GraphSyncEvent.ENTITY_PAPER, paper.getId());

                rows.add(new ImportResultRow(i + 1, "IMPORTED", null, paper.getId(), title));
                imported++;
            } catch (Exception e) {
                // 单条失败不影响其他篇；原因写进结果行，管理员可据此修正文件后重导
                rows.add(new ImportResultRow(i + 1, "FAILED",
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                        null, item.title()));
                failed++;
            }
        }

        return new ImportSummary(items.size(), imported, skipped, failed,
                createdAuthors, createdInstitutions, createdKeywords, createdVenues, rows);
    }

    // ------------------------------------------------------------------
    // 名称解析（作者/机构/关键词/渠道）已上移到 EntityUpsertResolver，
    // 供文件导入与 LLM 抽取归并共用同一套"按名称查库复用 or 新建"逻辑。
    // ------------------------------------------------------------------

    /** 发 Outbox 事件：与业务改动同事务提交，图同步器稍后轮询投影到 Neo4j */
    private void emitSyncEvent(String entityType, Long entityId) {
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setEventType(GraphSyncEvent.EVENT_UPSERT);
        graphSyncEventRepository.save(event);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
