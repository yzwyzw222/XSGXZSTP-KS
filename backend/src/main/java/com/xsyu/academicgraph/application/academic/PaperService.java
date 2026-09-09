package com.xsyu.academicgraph.application.academic;

import com.xsyu.academicgraph.api.common.GlobalExceptionHandler.EntityNotFoundException;
import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.api.papers.PaperDtos.AuthorItem;
import com.xsyu.academicgraph.api.papers.PaperDtos.AuthorRef;
import com.xsyu.academicgraph.api.papers.PaperDtos.KeywordRef;
import com.xsyu.academicgraph.api.papers.PaperDtos.PaperResponse;
import com.xsyu.academicgraph.api.papers.PaperDtos.PaperUpsertRequest;
import com.xsyu.academicgraph.api.papers.PaperDtos.ReferenceItem;
import com.xsyu.academicgraph.api.papers.PaperDtos.ReferenceRef;
import com.xsyu.academicgraph.api.papers.PaperDtos.VenueRef;
import com.xsyu.academicgraph.domain.academic.Author;
import com.xsyu.academicgraph.domain.academic.AuthorRepository;
import com.xsyu.academicgraph.domain.academic.Institution;
import com.xsyu.academicgraph.domain.academic.InstitutionRepository;
import com.xsyu.academicgraph.domain.academic.Keyword;
import com.xsyu.academicgraph.domain.academic.KeywordRepository;
import com.xsyu.academicgraph.domain.academic.Paper;
import com.xsyu.academicgraph.domain.academic.PaperAuthor;
import com.xsyu.academicgraph.domain.academic.PaperAuthorRepository;
import com.xsyu.academicgraph.domain.academic.PaperKeyword;
import com.xsyu.academicgraph.domain.academic.PaperKeywordRepository;
import com.xsyu.academicgraph.domain.academic.PaperReference;
import com.xsyu.academicgraph.domain.academic.PaperReferenceRepository;
import com.xsyu.academicgraph.domain.academic.PaperRepository;
import com.xsyu.academicgraph.domain.academic.Venue;
import com.xsyu.academicgraph.domain.academic.VenueRepository;
import com.xsyu.academicgraph.domain.sync.GraphSyncEvent;
import com.xsyu.academicgraph.domain.sync.GraphSyncEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 论文应用服务：论文主数据 + 三条关系（署名/关键词/引用）的编排。
 * 关键设计——事务性 Outbox：任何写操作在同一个数据库事务里插入 graph_sync_event 行，
 * 后台 GraphSyncService 轮询这些事件投影到 Neo4j。MySQL 提交成功 ⇒ 事件必在，
 * 图数据因此"最终一致"且可重建。
 */
@Service
@RequiredArgsConstructor
public class PaperService {

    /** 论文类型白名单：与 docs/sql/mysql-schema.sql 的 ck_paper_type 检查约束保持一致 */
    private static final Set<String> VALID_PAPER_TYPES = Set.of(
            Paper.TYPE_JOURNAL, Paper.TYPE_CONFERENCE, Paper.TYPE_PATENT, Paper.TYPE_OTHER);

    private final PaperRepository paperRepository;
    private final AuthorRepository authorRepository;
    private final InstitutionRepository institutionRepository;
    private final KeywordRepository keywordRepository;
    private final VenueRepository venueRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PaperReferenceRepository paperReferenceRepository;
    private final GraphSyncEventRepository graphSyncEventRepository;

    /** 分页列表：标题模糊 + 类型 + 年份三个可选过滤条件 */
    @Transactional(readOnly = true)
    public PageResponse<PaperResponse> list(String keyword, String paperType, Short year, Pageable pageable) {
        Page<Paper> page = paperRepository.findByFilters(keyword, paperType, year, pageable);
        // 一次批量组装整页数据：关系行用 IN 查询拉取，避免逐条查询的 N+1 问题
        List<PaperResponse> responses = toResponses(page.getContent());
        return new PageResponse<>(responses, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PaperResponse get(Long id) {
        return toResponses(List.of(mustFind(id))).get(0);
    }

    /** 创建论文：主表 + 三张关系表 + 图同步事件，一个事务全有或全无 */
    @Transactional
    public PaperResponse create(PaperUpsertRequest request, Long createdBy) {
        checkDoiUnique(request.doi(), null);

        Paper paper = new Paper();
        applyFields(paper, request);
        paper.setCreatedBy(createdBy);
        paper = paperRepository.save(paper); // save 后拿到自增 id，才能建关系行

        replaceRelations(paper.getId(), request);
        emitSyncEvent(GraphSyncEvent.ENTITY_PAPER, paper.getId(), GraphSyncEvent.EVENT_UPSERT);
        return toResponses(List.of(paper)).get(0);
    }

    /** 更新论文：先校验乐观锁版本，再整体替换关系（全量替换比 diff 简单且幂等） */
    @Transactional
    public PaperResponse update(Long id, PaperUpsertRequest request) {
        Paper paper = mustFind(id);
        // 乐观锁：请求带的 version 与库里的不一致 ⇒ 数据已被他人改过，拒绝覆盖
        if (request.version() == null || !Objects.equals(paper.getVersion(), request.version())) {
            throw new OptimisticLockingFailureException(
                    "版本冲突: 当前 version=" + paper.getVersion() + ", 请求携带=" + request.version());
        }
        checkDoiUnique(request.doi(), id);

        applyFields(paper, request);
        paperRepository.save(paper);
        replaceRelations(paper.getId(), request);
        emitSyncEvent(GraphSyncEvent.ENTITY_PAPER, paper.getId(), GraphSyncEvent.EVENT_UPSERT);
        return toResponses(List.of(paper)).get(0);
    }

    /** 删除论文：关系行随主表级联删除（数据库外键 CASCADE），图里由 DELETE 事件摘除节点 */
    @Transactional
    public void delete(Long id) {
        Paper paper = mustFind(id);
        paperRepository.delete(paper);
        emitSyncEvent(GraphSyncEvent.ENTITY_PAPER, id, GraphSyncEvent.EVENT_DELETE);
    }

    // ------------------------------------------------------------------
    // 私有辅助方法
    // ------------------------------------------------------------------

    private void applyFields(Paper paper, PaperUpsertRequest request) {
        paper.setTitle(request.title().trim());
        paper.setDoi(blankToNull(request.doi()));
        String type = request.paperType() == null || request.paperType().isBlank()
                ? Paper.TYPE_JOURNAL : request.paperType().trim();
        // 数据库有 ck_paper_type 检查约束，这里提前校验把非法值挡成 400，而不是穿透到 DB 层的 500
        if (!VALID_PAPER_TYPES.contains(type)) {
            throw new IllegalArgumentException("论文类型不合法: " + type + "，可选值: " + String.join("/", VALID_PAPER_TYPES));
        }
        paper.setPaperType(type);
        paper.setLanguage(blankToNull(request.language()));
        paper.setPublicationDate(request.publicationDate());
        paper.setAbstractText(blankToNull(request.abstractText()));
        paper.setCitationCount(request.citationCount() == null ? 0 : request.citationCount());
        if (request.venueId() != null) {
            venueRepository.findById(request.venueId())
                    .orElseThrow(() -> new EntityNotFoundException("发表渠道不存在: id=" + request.venueId()));
        }
        paper.setVenueId(request.venueId());
        // 知网导入扩展字段：未提供时统一落 NULL（blankToNull 把空串也归一成 NULL）
        paper.setVolume(blankToNull(request.volume()));
        paper.setPeriod(blankToNull(request.period()));
        paper.setPageCount(blankToNull(request.pageCount()));
        paper.setClcNumber(blankToNull(request.clcNumber()));
        paper.setUrl(blankToNull(request.url()));
    }

    /** 整体替换三条关系：先删旧行再按请求重建；请求为 null 时视为清空 */
    private void replaceRelations(Long paperId, PaperUpsertRequest request) {
        paperAuthorRepository.deleteByPaperId(paperId);
        paperKeywordRepository.deleteByPaperId(paperId);
        paperReferenceRepository.deleteByCitingPaperId(paperId);

        List<AuthorItem> authors = request.authors() == null ? List.of() : request.authors();
        for (AuthorItem item : authors) {
            authorRepository.findById(item.authorId())
                    .orElseThrow(() -> new EntityNotFoundException("作者不存在: id=" + item.authorId()));
            if (item.institutionId() != null) {
                institutionRepository.findById(item.institutionId())
                        .orElseThrow(() -> new EntityNotFoundException("机构不存在: id=" + item.institutionId()));
            }
            PaperAuthor pa = new PaperAuthor();
            pa.setPaperId(paperId);
            pa.setAuthorId(item.authorId());
            pa.setAuthorPosition(item.position());
            pa.setInstitutionId(item.institutionId());
            paperAuthorRepository.save(pa);
        }

        List<Long> keywordIds = request.keywordIds() == null ? List.of() : request.keywordIds();
        int pos = 1;
        for (Long keywordId : keywordIds) {
            keywordRepository.findById(keywordId)
                    .orElseThrow(() -> new EntityNotFoundException("关键词不存在: id=" + keywordId));
            PaperKeyword pk = new PaperKeyword();
            pk.setPaperId(paperId);
            pk.setKeywordId(keywordId);
            pk.setKeywordPosition(pos++);
            paperKeywordRepository.save(pk);
        }

        List<ReferenceItem> refs = request.references() == null ? List.of() : request.references();
        for (ReferenceItem item : refs) {
            // 应用层校验（数据库因外键 SET NULL 无法加 CHECK）：两个字段至少填一个
            if (item.citedPaperId() == null && (item.externalCitedDoi() == null || item.externalCitedDoi().isBlank())) {
                throw new IllegalArgumentException("引用条目必须提供 citedPaperId 或 externalCitedDoi 之一");
            }
            if (item.citedPaperId() != null) {
                if (Objects.equals(item.citedPaperId(), paperId)) {
                    throw new IllegalArgumentException("论文不能引用自己");
                }
                paperRepository.findById(item.citedPaperId())
                        .orElseThrow(() -> new EntityNotFoundException("被引论文不存在: id=" + item.citedPaperId()));
            }
            PaperReference pr = new PaperReference();
            pr.setCitingPaperId(paperId);
            pr.setCitedPaperId(item.citedPaperId());
            pr.setExternalCitedDoi(blankToNull(item.externalCitedDoi()));
            paperReferenceRepository.save(pr);
        }
    }

    /** DOI 唯一性预检：数据库唯一索引是最后防线，这里提前抛出可读性更好的错误 */
    private void checkDoiUnique(String doi, Long excludeId) {
        if (doi == null || doi.isBlank()) {
            return;
        }
        paperRepository.findByDoi(doi).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), excludeId)) {
                throw new IllegalArgumentException("DOI 已被论文《" + existing.getTitle() + "》使用");
            }
        });
    }

    /**
     * 批量组装响应 DTO：一次 IN 查询拉出全部论文的作者/关键词/引用关系行，
     * 在内存里做关联，避免逐条查询导致的 N+1 性能问题。
     */
    private List<PaperResponse> toResponses(List<Paper> papers) {
        List<Long> paperIds = papers.stream().map(Paper::getId).toList();
        if (paperIds.isEmpty()) {
            return List.of();
        }

        List<PaperAuthor> authorRows = paperAuthorRepository.findByPaperIdIn(paperIds);
        List<PaperKeyword> keywordRows = paperKeywordRepository.findByPaperIdIn(paperIds);
        List<PaperReference> refRows = paperReferenceRepository.findByCitingPaperIdIn(paperIds);

        // 一次查出所有涉及的名字表，拼 Map 做内存关联
        Map<Long, Author> authorMap = authorRepository.findByIdIn(
                        authorRows.stream().map(PaperAuthor::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(Author::getId, Function.identity()));
        Map<Long, Keyword> keywordMap = keywordRepository.findByIdIn(
                        keywordRows.stream().map(PaperKeyword::getKeywordId).distinct().toList())
                .stream().collect(Collectors.toMap(Keyword::getId, Function.identity()));
        Map<Long, Institution> instMap = institutionRepository.findByIdIn(
                        authorRows.stream().map(PaperAuthor::getInstitutionId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Institution::getId, Function.identity()));
        Map<Long, Venue> venueMap = venueRepository.findByIdIn(
                        papers.stream().map(Paper::getVenueId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Venue::getId, Function.identity()));
        Map<Long, Paper> citedPaperMap = paperRepository.findAllById(
                        refRows.stream().map(PaperReference::getCitedPaperId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Paper::getId, Function.identity()));

        Map<Long, List<PaperAuthor>> authorsByPaper = authorRows.stream()
                .collect(Collectors.groupingBy(PaperAuthor::getPaperId));
        Map<Long, List<PaperKeyword>> keywordsByPaper = keywordRows.stream()
                .collect(Collectors.groupingBy(PaperKeyword::getPaperId));
        Map<Long, List<PaperReference>> refsByPaper = refRows.stream()
                .collect(Collectors.groupingBy(PaperReference::getCitingPaperId));

        List<PaperResponse> result = new ArrayList<>();
        for (Paper paper : papers) {
            List<AuthorRef> authorRefs = authorsByPaper.getOrDefault(paper.getId(), List.of()).stream()
                    .sorted((x, y) -> Integer.compare(x.getAuthorPosition(), y.getAuthorPosition()))
                    .map(row -> {
                        Author a = authorMap.get(row.getAuthorId());
                        Institution inst = row.getInstitutionId() == null ? null : instMap.get(row.getInstitutionId());
                        return new AuthorRef(row.getAuthorId(),
                                a == null ? "?" : a.getDisplayName(),
                                row.getAuthorPosition(), row.getInstitutionId(),
                                inst == null ? null : inst.getDisplayName());
                    })
                    .toList();
            List<KeywordRef> keywordRefs = keywordsByPaper.getOrDefault(paper.getId(), List.of()).stream()
                    .sorted((x, y) -> Integer.compare(x.getKeywordPosition(), y.getKeywordPosition()))
                    .map(row -> {
                        Keyword k = keywordMap.get(row.getKeywordId());
                        return new KeywordRef(row.getKeywordId(), k == null ? "?" : k.getName());
                    })
                    .toList();
            List<ReferenceRef> referenceRefs = refsByPaper.getOrDefault(paper.getId(), List.of()).stream()
                    .map(row -> {
                        Paper cited = row.getCitedPaperId() == null ? null : citedPaperMap.get(row.getCitedPaperId());
                        return new ReferenceRef(row.getId(), row.getCitedPaperId(),
                                cited == null ? null : cited.getTitle(), row.getExternalCitedDoi());
                    })
                    .toList();
            Venue venue = paper.getVenueId() == null ? null : venueMap.get(paper.getVenueId());

            result.add(new PaperResponse(paper.getId(), paper.getTitle(), paper.getDoi(), paper.getPaperType(),
                    paper.getLanguage(), paper.getPublicationDate(), paper.getPublicationYear(),
                    paper.getAbstractText(), paper.getCitationCount(), paper.getExtractionStatus(),
                    venue == null ? null : new VenueRef(venue.getId(), venue.getDisplayName()),
                    paper.getVolume(), paper.getPeriod(), paper.getPageCount(), paper.getClcNumber(), paper.getUrl(),
                    authorRefs, keywordRefs, referenceRefs,
                    paper.getVersion(), paper.getCreatedAt(), paper.getUpdatedAt()));
        }
        return result;
    }

    /** 发 Outbox 事件：与业务改动同事务提交，投影消费者稍后轮询处理 */
    private void emitSyncEvent(String entityType, Long entityId, String eventType) {
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setEventType(eventType);
        graphSyncEventRepository.save(event);
    }

    private Paper mustFind(Long id) {
        return paperRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("论文不存在: id=" + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
