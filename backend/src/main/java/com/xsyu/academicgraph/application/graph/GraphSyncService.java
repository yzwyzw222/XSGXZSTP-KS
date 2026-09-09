package com.xsyu.academicgraph.application.graph;

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
import com.xsyu.academicgraph.domain.extraction.EntityRelationship;
import com.xsyu.academicgraph.domain.extraction.EntityRelationshipRepository;
import com.xsyu.academicgraph.domain.extraction.ExtractedEntity;
import com.xsyu.academicgraph.domain.extraction.ExtractedEntityRepository;
import com.xsyu.academicgraph.domain.extraction.ResearchEntity;
import com.xsyu.academicgraph.domain.extraction.ResearchEntityRepository;
import com.xsyu.academicgraph.domain.sync.GraphSyncEvent;
import com.xsyu.academicgraph.domain.sync.GraphSyncEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图同步服务 —— 事务性 Outbox 的"投递员"。
 *
 * 工作原理：
 *  1. 业务服务写 MySQL 时，在同一事务里插入 graph_sync_event（PENDING）；
 *  2. 本服务定时（@Scheduled）轮询最早的一批 PENDING 事件；
 *  3. 每条事件独立处理：读 MySQL 最新数据，用 MERGE 幂等投影到 Neo4j；
 *  4. 成功标 PROCESSED，失败标 FAILED 并记录原因（查表即可排障，可手工重投）。
 *
 * 因此 MySQL 是权威数据源，Neo4j 只是"最终一致"的可重建投影：
 * 哪怕 Neo4j 整体删库，只要事件还在（或重新全量同步），图就能恢复。
 * 每个节点的 businessId = MySQL 主键，是 MERGE 的幂等锚点（Neo4j 唯一约束保证）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphSyncService {

    private final GraphSyncEventRepository eventRepository;
    private final Neo4jClient neo4jClient;
    private final PaperRepository paperRepository;
    private final AuthorRepository authorRepository;
    private final InstitutionRepository institutionRepository;
    private final KeywordRepository keywordRepository;
    private final VenueRepository venueRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final PaperReferenceRepository paperReferenceRepository;
    private final ResearchEntityRepository researchEntityRepository;
    private final ExtractedEntityRepository extractedEntityRepository;
    private final EntityRelationshipRepository entityRelationshipRepository;

    /** 定时轮询：应用启动 15 秒后开始，每批之间间隔 5 秒（fixedDelay = 上一批结束再计时） */
    @Scheduled(fixedDelay = 5000, initialDelay = 15000)
    public void scheduledSync() {
        processBatch();
    }

    /** 管理接口手动触发：连续处理直到没有待办事件（上限 50 批，防止异常情况下死循环） */
    public int syncNow() {
        int total = 0;
        for (int i = 0; i < 50; i++) {
            int processed = processBatch();
            total += processed;
            if (processed == 0) {
                break;
            }
        }
        // 手动同步额外重算一次合作关系派生边：
        // 即使没有待办事件（例如直接用 SQL 灌了样例数据、或 Neo4j 里还没有 COAUTHOR_WITH），
        // 管理员点一次"同步图谱"就能把作者两两合作边补齐
        recomputeCoauthorEdges();
        return total;
    }

    /** 处理一批事件（最多 20 条）：每条独立 try/catch，单条失败只标 FAILED 不阻塞后续 */
    private int processBatch() {
        List<GraphSyncEvent> events = eventRepository.findTop20ByStatusOrderByIdAsc(GraphSyncEvent.STATUS_PENDING);
        boolean paperTouched = false;
        for (GraphSyncEvent event : events) {
            try {
                if (GraphSyncEvent.EVENT_UPSERT.equals(event.getEventType())) {
                    projectUpsert(event);
                } else {
                    projectDelete(event);
                }
                eventRepository.markProcessed(event.getId(), GraphSyncEvent.STATUS_PROCESSED, LocalDateTime.now());
            } catch (Exception e) {
                log.warn("图同步失败: eventId={} entity={}:{} error={}",
                        event.getId(), event.getEntityType(), event.getEntityId(), e.getMessage());
                eventRepository.markFailed(event.getId(), GraphSyncEvent.STATUS_FAILED, truncate(e.getMessage()));
            }
            // 合作边完全由论文的署名关系派生，因此只要这批里动过论文就要重算
            if (GraphSyncEvent.ENTITY_PAPER.equals(event.getEntityType())) {
                paperTouched = true;
            }
        }
        if (paperTouched) {
            recomputeCoauthorEdges();
        }
        return events.size();
    }

    /**
     * 物化"合作关系"派生边 COAUTHOR_WITH（作者—作者，weight = 共同署名论文数）。
     *
     * 为什么要物化：合作关系是从事实边 AUTHORED 推断出来的一跳关系，
     * 实时算每次都要在 AUTHORED 上做作者自连接再聚合，图一大就慢；
     * 物化成真正的边之后，前端画合作网络、后续做合作社区发现都能一跳直接拿到。
     *
     * 为什么用全量重算而不是增量维护：增量维护要在"论文改了作者列表"时精确回滚旧权重，
     * 分支多且极易权重漂移；而全量重算在课程设计的规模下是毫秒级，且天然自愈——
     * 不管之前投影失败过多少次，重算一遍就和 AUTHORED 完全一致。
     * 幂等：MERGE + SET，重复执行结果一致。
     */
    public void recomputeCoauthorEdges() {
        // 1) 删掉已不成立的旧合作边（论文被删、作者被移出署名后，这对作者可能不再合著）
        neo4jClient.query("""
                        MATCH (a:Author)-[r:COAUTHOR_WITH]->(b:Author)
                        WHERE NOT (a)-[:AUTHORED]->(:Paper)<-[:AUTHORED]-(b)
                        DELETE r
                        """).run();

        // 2) 按当前 AUTHORED 边重算权重；businessId 大小比较保证每对作者只生成一条边（无向关系）
        neo4jClient.query("""
                        MATCH (a:Author)-[:AUTHORED]->(p:Paper)<-[:AUTHORED]-(b:Author)
                        WHERE a.businessId < b.businessId
                        WITH a, b, count(p) AS weight
                        MERGE (a)-[r:COAUTHOR_WITH]->(b)
                        SET r.weight = weight, r.computedAt = datetime()
                        """).run();
    }

    private void projectUpsert(GraphSyncEvent event) {
        switch (event.getEntityType()) {
            case GraphSyncEvent.ENTITY_PAPER -> projectPaper(event.getEntityId());
            case GraphSyncEvent.ENTITY_AUTHOR -> projectAuthor(event.getEntityId());
            case GraphSyncEvent.ENTITY_INSTITUTION -> projectInstitution(event.getEntityId());
            case GraphSyncEvent.ENTITY_VENUE -> projectVenue(event.getEntityId());
            case GraphSyncEvent.ENTITY_KEYWORD -> projectKeyword(event.getEntityId());
            case GraphSyncEvent.ENTITY_RESEARCH_ENTITY -> projectResearchEntity(event.getEntityId());
            default -> throw new IllegalArgumentException("未知实体类型: " + event.getEntityType());
        }
    }

    // ------------------------------------------------------------------
    // 论文投影：节点 + 四类边（署名/从属/发表/关键词/引用）整体重建
    // ------------------------------------------------------------------

    /**
     * 论文 UPSERT：MERGE 节点 → 删掉本论文持有的旧边 → 按 MySQL 最新数据重建所有边。
     * 删旧边只删"属于这张论文"的边（出边 + AUTHORED 入边），
     * 不会动其他论文指向它的 CITES 入边（那些边由各自论文的事件负责）。
     */
    private void projectPaper(Long paperId) {
        Paper paper = paperRepository.findById(paperId).orElse(null);
        if (paper == null) {
            // 论文在投影前已被删除：UPSERT 无数据可投影，跳过即可。
            // 删除操作在同一事务里发过 DELETE 事件（事件按 id 顺序处理，随后到达），
            // 该事件会 DETACH DELETE 摘掉节点，所以这里跳过不会留下脏图数据。
            log.info("论文已不存在，跳过 UPSERT 投影: paperId={}", paperId);
            return;
        }

        // 1) 节点 MERGE + SET：MERGE 保证重复投递幂等，SET null 等价于删除该属性
        neo4jClient.query("""
                        MERGE (p:Paper {businessId: $businessId})
                        SET p.title = $title, p.doi = $doi, p.paperType = $paperType,
                            p.publicationDate = $publicationDate, p.publicationYear = $publicationYear,
                            p.citationCount = $citationCount
                        """)
                .bind(paperId).to("businessId")
                .bind(paper.getTitle()).to("title")
                .bind(paper.getDoi()).to("doi")
                .bind(paper.getPaperType()).to("paperType")
                .bind(paper.getPublicationDate()).to("publicationDate")
                .bind(paper.getPublicationYear()).to("publicationYear")
                .bind(paper.getCitationCount()).to("citationCount")
                .run();

        // 2) 清旧边：出边（PUBLISHED_IN/HAS_KEYWORD/CITES）与入边（AUTHORED）
        neo4jClient.query("""
                        MATCH (p:Paper {businessId: $businessId})
                        OPTIONAL MATCH (p)-[r]->()
                        DELETE r
                        """).bind(paperId).to("businessId").run();
        neo4jClient.query("""
                        MATCH (p:Paper {businessId: $businessId})
                        OPTIONAL MATCH (p)<-[r:AUTHORED]-()
                        DELETE r
                        """).bind(paperId).to("businessId").run();

        // 3) 署名边：作者节点顺带 MERGE（保证先于论文到达的事件也能连上边）
        List<PaperAuthor> authorRows = paperAuthorRepository.findByPaperIdOrderByAuthorPositionAsc(paperId);
        Map<Long, Author> authorMap = authorRepository.findByIdIn(
                        authorRows.stream().map(PaperAuthor::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(Author::getId, Function.identity()));
        Map<Long, Institution> instMap = institutionRepository.findByIdIn(
                        authorRows.stream().map(PaperAuthor::getInstitutionId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Institution::getId, Function.identity()));

        for (PaperAuthor row : authorRows) {
            Author author = authorMap.get(row.getAuthorId());
            neo4jClient.query("""
                            MATCH (p:Paper {businessId: $paperId})
                            MERGE (a:Author {businessId: $authorId})
                            SET a.name = $authorName
                            MERGE (a)-[r:AUTHORED]->(p)
                            SET r.position = $position
                            """)
                    .bind(paperId).to("paperId")
                    .bind(row.getAuthorId()).to("authorId")
                    .bind(author == null ? "?" : author.getDisplayName()).to("authorName")
                    .bind(row.getAuthorPosition()).to("position")
                    .run();

            // 4) 署名机构 → AFFILIATED_WITH 边：只 MERGE 不删除（作者可能历任多机构，证据链保留）
            if (row.getInstitutionId() != null) {
                Institution inst = instMap.get(row.getInstitutionId());
                neo4jClient.query("""
                                MATCH (a:Author {businessId: $authorId})
                                MERGE (i:Institution {businessId: $institutionId})
                                SET i.name = $institutionName
                                MERGE (a)-[:AFFILIATED_WITH]->(i)
                                """)
                        .bind(row.getAuthorId()).to("authorId")
                        .bind(row.getInstitutionId()).to("institutionId")
                        .bind(inst == null ? "?" : inst.getDisplayName()).to("institutionName")
                        .run();
            }
        }

        // 5) 发表渠道边
        if (paper.getVenueId() != null) {
            venueRepository.findById(paper.getVenueId()).ifPresent(venue -> neo4jClient.query("""
                            MATCH (p:Paper {businessId: $paperId})
                            MERGE (v:Venue {businessId: $venueId})
                            SET v.name = $venueName
                            MERGE (p)-[:PUBLISHED_IN]->(v)
                            """)
                    .bind(paperId).to("paperId")
                    .bind(venue.getId()).to("venueId")
                    .bind(venue.getDisplayName()).to("venueName")
                    .run());
        }

        // 6) 关键词边
        List<PaperKeyword> keywordRows = paperKeywordRepository.findByPaperIdOrderByKeywordPositionAsc(paperId);
        Map<Long, Keyword> keywordMap = keywordRepository.findByIdIn(
                        keywordRows.stream().map(PaperKeyword::getKeywordId).distinct().toList())
                .stream().collect(Collectors.toMap(Keyword::getId, Function.identity()));
        for (PaperKeyword row : keywordRows) {
            Keyword keyword = keywordMap.get(row.getKeywordId());
            neo4jClient.query("""
                            MATCH (p:Paper {businessId: $paperId})
                            MERGE (k:Keyword {businessId: $keywordId})
                            SET k.name = $keywordName
                            MERGE (p)-[:HAS_KEYWORD]->(k)
                            """)
                    .bind(paperId).to("paperId")
                    .bind(row.getKeywordId()).to("keywordId")
                    .bind(keyword == null ? "?" : keyword.getName()).to("keywordName")
                    .run();
        }

        // 7) 引用边：只有库内引用（cited_paper_id 非空）能投影成 CITES 边；
        //    外部 DOI 引用在库里没有对应论文节点，图里无法连边（保留在 MySQL 供检索）
        List<PaperReference> refRows = paperReferenceRepository.findByCitingPaperId(paperId);
        Map<Long, Paper> citedMap = paperRepository.findAllById(
                        refRows.stream().map(PaperReference::getCitedPaperId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Paper::getId, Function.identity()));
        for (PaperReference ref : refRows) {
            if (ref.getCitedPaperId() == null) {
                continue;
            }
            Paper cited = citedMap.get(ref.getCitedPaperId());
            neo4jClient.query("""
                            MATCH (p:Paper {businessId: $paperId})
                            MERGE (c:Paper {businessId: $citedPaperId})
                            SET c.title = $citedTitle
                            MERGE (p)-[:CITES]->(c)
                            """)
                    .bind(paperId).to("paperId")
                    .bind(ref.getCitedPaperId()).to("citedPaperId")
                    .bind(cited == null ? "?" : cited.getTitle()).to("citedTitle")
                    .run();
        }

        // 8) LLM 抽取边：EXTRACTED_FROM（研究实体/机构 → 论文）+ 六类 LLM 关系边
        projectExtractionEdges(paperId);
    }

    // ------------------------------------------------------------------
    // LLM 抽取边投影：EXTRACTED_FROM 证据边 + 六类关系边（带 evidence/confidence）
    // ------------------------------------------------------------------

    /**
     * 投影一篇论文的 LLM 抽取结果到图。
     * 数据来源是 MySQL 台账（extracted_entities / entity_relationship），
     * 因此本方法与"论文 UPSERT 事件"绑定：抽取服务落库后发 PAPER 事件，这里整体重建。
     * 重跑抽取同样触发本方法，第一步的清理保证旧边不残留。
     */
    private void projectExtractionEdges(Long paperId) {
        // 1) 清旧边：抽取产生的边都带 paperId 属性（其余边没有该属性，不受影响）
        neo4jClient.query("""
                        MATCH ()-[r]->()
                        WHERE r.paperId = $paperId
                        DELETE r
                        """).bind(paperId).to("paperId").run();

        List<ExtractedEntity> ledger = extractedEntityRepository.findByPaperIdOrderByIdAsc(paperId);

        // 2) 研究实体：MERGE 节点 + EXTRACTED_FROM 证据边（方法/数据集/工具是新增节点类型）
        List<Long> researchIds = ledger.stream()
                .filter(e -> ExtractedEntity.RESOLVED_RESEARCH_ENTITY.equals(e.getResolvedEntityType()))
                .map(ExtractedEntity::getResolvedEntityId).distinct().toList();
        Map<Long, ResearchEntity> researchMap = researchEntityRepository.findByIdIn(researchIds).stream()
                .collect(Collectors.toMap(ResearchEntity::getId, Function.identity()));
        for (ResearchEntity entity : researchMap.values()) {
            neo4jClient.query("""
                            MATCH (p:Paper {businessId: $paperId})
                            MERGE (e:ResearchEntity {businessId: $businessId})
                            SET e.name = $name, e.entityType = $entityType
                            MERGE (e)-[r:EXTRACTED_FROM]->(p)
                            SET r.paperId = $paperId
                            """)
                    .bind(paperId).to("paperId")
                    .bind(entity.getId()).to("businessId")
                    .bind(entity.getName()).to("name")
                    .bind(entity.getEntityType()).to("entityType")
                    .run();
        }

        // 3) 机构 EXTRACTED_FROM：LLM 从摘要抽到的机构（论文-机构隶属的证据，
        //    与署名机构 AFFILIATED_WITH 是两条不同来源的证据链）
        List<Long> institutionIds = ledger.stream()
                .filter(e -> ExtractedEntity.RESOLVED_INSTITUTION.equals(e.getResolvedEntityType()))
                .map(ExtractedEntity::getResolvedEntityId).distinct().toList();
        Map<Long, Institution> instMap = institutionRepository.findByIdIn(institutionIds).stream()
                .collect(Collectors.toMap(Institution::getId, Function.identity()));
        for (Institution inst : instMap.values()) {
            neo4jClient.query("""
                            MATCH (p:Paper {businessId: $paperId})
                            MERGE (i:Institution {businessId: $businessId})
                            SET i.name = $name
                            MERGE (i)-[r:EXTRACTED_FROM]->(p)
                            SET r.paperId = $paperId
                            """)
                    .bind(paperId).to("paperId")
                    .bind(inst.getId()).to("businessId")
                    .bind(inst.getDisplayName()).to("name")
                    .run();
        }

        // 4) LLM 关系边：端点按 resolved 类型映射到节点标签（双白名单——标签与关系类型
        //    都不是用户输入，拼接安全；关系类型另有数据库 CHECK 约束兜底）
        Map<Long, ExtractedEntity> ledgerById = ledger.stream()
                .collect(Collectors.toMap(ExtractedEntity::getId, Function.identity()));
        for (EntityRelationship rel : entityRelationshipRepository.findByPaperIdOrderByIdAsc(paperId)) {
            ExtractedEntity source = ledgerById.get(rel.getSourceEntityId());
            ExtractedEntity target = ledgerById.get(rel.getTargetEntityId());
            String sourceLabel = source == null ? null : resolvedLabel(source.getResolvedEntityType());
            String targetLabel = target == null ? null : resolvedLabel(target.getResolvedEntityType());
            if (sourceLabel == null || targetLabel == null) {
                // 端点没归并成功（resolve 失败）的关系画不出来，跳过并留日志
                log.warn("抽取关系端点未归并，跳过投影: paperId={} relationshipId={}",
                        paperId, rel.getId());
                continue;
            }
            neo4jClient.query("""
                            MATCH (s:%s {businessId: $sourceId})
                            MATCH (t:%s {businessId: $targetId})
                            MERGE (s)-[r:%s {paperId: $paperId}]->(t)
                            SET r.evidence = $evidence, r.confidence = $confidence
                            """.formatted(sourceLabel, targetLabel, rel.getRelationshipType()))
                    .bind(source.getResolvedEntityId()).to("sourceId")
                    .bind(target.getResolvedEntityId()).to("targetId")
                    .bind(paperId).to("paperId")
                    .bind(rel.getEvidenceText()).to("evidence")
                    .bind(rel.getConfidence()).to("confidence")
                    .run();
        }
    }

    /** 台账 resolved 类型 → 图节点标签（白名单映射，未知值返回 null 表示不可投影） */
    private static String resolvedLabel(String resolvedEntityType) {
        return switch (resolvedEntityType) {
            case ExtractedEntity.RESOLVED_AUTHOR -> "Author";
            case ExtractedEntity.RESOLVED_INSTITUTION -> "Institution";
            case ExtractedEntity.RESOLVED_KEYWORD -> "Keyword";
            case ExtractedEntity.RESOLVED_RESEARCH_ENTITY -> "ResearchEntity";
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // 单一实体投影：节点 MERGE + SET 属性（幂等）
    // ------------------------------------------------------------------

    private void projectAuthor(Long id) {
        authorRepository.findById(id).ifPresent(author -> neo4jClient.query("""
                        MERGE (a:Author {businessId: $businessId})
                        SET a.name = $name, a.orcid = $orcid
                        """)
                .bind(id).to("businessId")
                .bind(author.getDisplayName()).to("name")
                .bind(author.getOrcid()).to("orcid")
                .run());
    }

    private void projectInstitution(Long id) {
        institutionRepository.findById(id).ifPresent(institution -> neo4jClient.query("""
                        MERGE (i:Institution {businessId: $businessId})
                        SET i.name = $name, i.countryCode = $countryCode
                        """)
                .bind(id).to("businessId")
                .bind(institution.getDisplayName()).to("name")
                .bind(institution.getCountryCode()).to("countryCode")
                .run());
    }

    private void projectVenue(Long id) {
        venueRepository.findById(id).ifPresent(venue -> neo4jClient.query("""
                        MERGE (v:Venue {businessId: $businessId})
                        SET v.name = $name, v.venueType = $venueType, v.issn = $issn
                        """)
                .bind(id).to("businessId")
                .bind(venue.getDisplayName()).to("name")
                .bind(venue.getVenueType()).to("venueType")
                .bind(venue.getIssn()).to("issn")
                .run());
    }

    private void projectKeyword(Long id) {
        keywordRepository.findById(id).ifPresent(keyword -> neo4jClient.query("""
                        MERGE (k:Keyword {businessId: $businessId})
                        SET k.name = $name, k.fieldName = $fieldName
                        """)
                .bind(id).to("businessId")
                .bind(keyword.getName()).to("name")
                .bind(keyword.getFieldName()).to("fieldName")
                .run());
    }

    private void projectResearchEntity(Long id) {
        researchEntityRepository.findById(id).ifPresent(entity -> neo4jClient.query("""
                        MERGE (e:ResearchEntity {businessId: $businessId})
                        SET e.name = $name, e.entityType = $entityType
                        """)
                .bind(id).to("businessId")
                .bind(entity.getName()).to("name")
                .bind(entity.getEntityType()).to("entityType")
                .run());
    }

    // ------------------------------------------------------------------
    // 删除投影：DETACH DELETE 摘掉节点及其所有关系边
    // ------------------------------------------------------------------

    private void projectDelete(GraphSyncEvent event) {
        // 实体类型 → 节点 Label 的白名单映射（Label 不是用户输入，拼接是安全的）
        String label = switch (event.getEntityType()) {
            case GraphSyncEvent.ENTITY_PAPER -> "Paper";
            case GraphSyncEvent.ENTITY_AUTHOR -> "Author";
            case GraphSyncEvent.ENTITY_INSTITUTION -> "Institution";
            case GraphSyncEvent.ENTITY_VENUE -> "Venue";
            case GraphSyncEvent.ENTITY_KEYWORD -> "Keyword";
            case GraphSyncEvent.ENTITY_RESEARCH_ENTITY -> "ResearchEntity";
            default -> throw new IllegalArgumentException("未知实体类型: " + event.getEntityType());
        };
        // 节点不存在时 DETACH DELETE 是空操作，因此重复投递也安全
        neo4jClient.query("MATCH (n:" + label + " {businessId: $businessId}) DETACH DELETE n")
                .bind(event.getEntityId()).to("businessId")
                .run();
    }

    /** last_error 列最长 512 字符，超长错误信息截断 */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
