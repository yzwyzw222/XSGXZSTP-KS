package com.xsyu.academicgraph.application.extraction;

import com.xsyu.academicgraph.api.admin.ExtractionDtos.ExtractionEntityDto;
import com.xsyu.academicgraph.api.admin.ExtractionDtos.ExtractionRelationshipDto;
import com.xsyu.academicgraph.api.admin.ExtractionDtos.ExtractionResultDto;
import com.xsyu.academicgraph.api.admin.ExtractionDtos.ExtractionStatusDto;
import com.xsyu.academicgraph.api.common.GlobalExceptionHandler.EntityNotFoundException;
import com.xsyu.academicgraph.api.extraction.ExtractionGraphDtos.ExtractionGraphData;
import com.xsyu.academicgraph.api.extraction.ExtractionGraphDtos.ExtractionGraphEdge;
import com.xsyu.academicgraph.api.extraction.ExtractionGraphDtos.ExtractionGraphNode;
import com.xsyu.academicgraph.application.academic.EntityUpsertResolver;
import com.xsyu.academicgraph.application.academic.EntityUpsertResolver.Resolved;
import com.xsyu.academicgraph.application.extraction.LlmResultParser.ParsedEntity;
import com.xsyu.academicgraph.application.extraction.LlmResultParser.ParsedExtraction;
import com.xsyu.academicgraph.application.extraction.LlmResultParser.ParsedRelationship;
import com.xsyu.academicgraph.domain.academic.Author;
import com.xsyu.academicgraph.domain.academic.Institution;
import com.xsyu.academicgraph.domain.academic.Keyword;
import com.xsyu.academicgraph.domain.academic.Paper;
import com.xsyu.academicgraph.domain.academic.PaperAuthor;
import com.xsyu.academicgraph.domain.academic.PaperAuthorRepository;
import com.xsyu.academicgraph.domain.academic.PaperKeyword;
import com.xsyu.academicgraph.domain.academic.PaperKeywordRepository;
import com.xsyu.academicgraph.domain.academic.PaperRepository;
import com.xsyu.academicgraph.domain.extraction.EntityRelationship;
import com.xsyu.academicgraph.domain.extraction.EntityRelationshipRepository;
import com.xsyu.academicgraph.domain.extraction.ExtractedEntity;
import com.xsyu.academicgraph.domain.extraction.ExtractedEntityRepository;
import com.xsyu.academicgraph.domain.extraction.PaperResearchEntity;
import com.xsyu.academicgraph.domain.extraction.PaperResearchEntityRepository;
import com.xsyu.academicgraph.domain.extraction.ResearchEntity;
import com.xsyu.academicgraph.domain.sync.GraphSyncEvent;
import com.xsyu.academicgraph.domain.sync.GraphSyncEventRepository;
import com.xsyu.academicgraph.infrastructure.llm.EntityExtractionPromptBuilder;
import com.xsyu.academicgraph.infrastructure.llm.OpenAiCompatibleClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * LLM 实体抽取编排服务（从 feature/Du 的 ExtractionPipelineService 移植并扩展）。
 *
 * 状态机：PENDING → IN_PROGRESS → COMPLETED / FAILED（状态落在 paper.extraction_status 列）。
 * 异步执行：trigger 只做校验并返回任务清单，实际抽取在 extractionExecutor 线程池里跑——
 * LLM 单次要几十秒，同步等会把 HTTP 请求挂死；前端触发后轮询 /status 查看进度。
 *
 * 与 Du 原版的关键差异：
 *  - 抽取结果不再直接写图库，而是按"六类分派"归并进 Ye 的五实体表 + 研究实体表：
 *    PERSON→author、ORGANIZATION→institution、TOPIC→keyword（并追加 paper_author/paper_keyword 行）、
 *    METHOD/DATASET/TOOL→research_entity（并写 paper_research_entity 关联）；
 *    每一条都留 extracted_entities 台账（含 resolved_entity_type/id 审计线索）。
 *  - 关系端点必须命中"本次抽取"的实体台账才落库（Du 的 entityMap 语义），置信度固定 1.0。
 *  - 落库后发 PAPER UPSERT Outbox 事件：图同步器据此重建 EXTRACTED_FROM 与六类 LLM 关系边。
 *  - 重跑覆盖语义：台账/关系/研究实体关联先按论文清空再重建；paper_author/paper_keyword
 *    的追加行不回收（追加幂等无害，清掉反而会误删用户手工维护的署名）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    /** 单篇论文 LLM 调用的最大重试次数（与 Du 一致） */
    private static final int LLM_MAX_RETRIES = 3;

    private final PaperRepository paperRepository;
    private final PaperAuthorRepository paperAuthorRepository;
    private final PaperKeywordRepository paperKeywordRepository;
    private final ExtractedEntityRepository extractedEntityRepository;
    private final EntityRelationshipRepository entityRelationshipRepository;
    private final PaperResearchEntityRepository paperResearchEntityRepository;
    private final GraphSyncEventRepository graphSyncEventRepository;
    private final EntityUpsertResolver entityUpsertResolver;
    private final OpenAiCompatibleClient llmClient;
    private final EntityExtractionPromptBuilder promptBuilder;
    private final LlmResultParser resultParser;
    private final TransactionTemplate transactionTemplate;

    /** 正在抽取中的论文 id 集合：防止双击触发导致同一篇论文两个线程并发抽取互踩 */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 解析本次要抽取的论文清单（同步校验，异常直接反馈给前端）。
     * paperIds 缺省时取最早的 10 篇 PENDING 论文（与 Du 的 extractFromPendingPapers(10) 对齐）。
     */
    public List<Long> resolveTargetPaperIds(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return paperRepository.findTop10ByExtractionStatusOrderByIdAsc(Paper.EXTRACTION_PENDING)
                    .stream().map(Paper::getId).toList();
        }
        List<Long> distinct = paperIds.stream().distinct().toList();
        Set<Long> found = paperRepository.findAllById(distinct).stream()
                .map(Paper::getId).collect(Collectors.toSet());
        List<Long> missing = distinct.stream().filter(id -> !found.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("论文不存在: id=" + missing);
        }
        return distinct;
    }

    /** 异步执行抽取批次（@Async 必须由 Controller 跨 Bean 调用才会走线程池代理） */
    @Async("extractionExecutor")
    public void executeAsync(List<Long> paperIds) {
        for (Long paperId : paperIds) {
            try {
                extractPaper(paperId);
            } catch (Exception e) {
                // 单篇失败不影响同批其他论文；状态已在 extractPaper 内部标记，这里只兜底记日志
                log.error("抽取异常（未在流程内捕获）: paperId={} error={}", paperId, e.getMessage(), e);
                markStatus(paperId, Paper.EXTRACTION_FAILED);
            }
        }
    }

    /**
     * 抽取单篇论文：无摘要直接 FAILED；LLM 调用最多重试 3 次；成功后大事务落库。
     * 全程不抛异常（异步线程没人接），所有失败路径都收敛到"状态=FAILED"。
     */
    void extractPaper(Long paperId) {
        Paper paper = paperRepository.findById(paperId).orElse(null);
        if (paper == null || !inFlight.add(paperId)) {
            return; // 论文不存在，或已有线程在抽这篇
        }
        try {
            // 无摘要：LLM 没有可抽取的输入，直接失败（避免浪费一次 API 调用）
            if (paper.getAbstractText() == null || paper.getAbstractText().isBlank()) {
                log.warn("论文无摘要，跳过抽取: paperId={}", paperId);
                markStatus(paperId, Paper.EXTRACTION_FAILED);
                return;
            }

            markStatus(paperId, Paper.EXTRACTION_IN_PROGRESS);
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(paper.getTitle(), paper.getAbstractText());

            String llmResponse = null;
            for (int attempt = 1; attempt <= LLM_MAX_RETRIES; attempt++) {
                try {
                    llmResponse = llmClient.chat(systemPrompt, userPrompt);
                    break;
                } catch (Exception e) {
                    log.warn("LLM 调用第 {}/{} 次失败: paperId={} error={}",
                            attempt, LLM_MAX_RETRIES, paperId, e.getMessage());
                    if (attempt == LLM_MAX_RETRIES) {
                        markStatus(paperId, Paper.EXTRACTION_FAILED);
                        return;
                    }
                }
            }

            persistResult(paperId, resultParser.parse(llmResponse));
            markStatus(paperId, Paper.EXTRACTION_COMPLETED);
            log.info("抽取完成: paperId={} title={}", paperId, paper.getTitle());
        } finally {
            inFlight.remove(paperId);
        }
    }

    /**
     * 落库大事务：清旧台账 → 六类分派 → 关系 → 发 PAPER 事件。
     * 用 TransactionTemplate 而不是 @Transactional 注解：本方法跑在异步线程里，
     * 且由同 Bean 的 extractPaper 直接调用，注解事务代理两级都拦不到（自调用 + 异步）。
     */
    private void persistResult(Long paperId, ParsedExtraction parsed) {
        transactionTemplate.executeWithoutResult(status -> {
            // 1) 重跑覆盖语义：台账/关系/研究实体关联整表清空重建；
            //    paper_author/paper_keyword 的追加行不回收（见类注释）
            extractedEntityRepository.deleteByPaperId(paperId);
            entityRelationshipRepository.deleteByPaperId(paperId);
            paperResearchEntityRepository.deleteByPaperId(paperId);

            // 2) 六类分派：解析成业务实体 + 写台账
            Map<String, Author> authorsByName = new HashMap<>();
            Map<String, Institution> institutionsByName = new HashMap<>();
            Map<String, Keyword> keywordsByName = new HashMap<>();
            Map<String, ResearchEntity> researchEntitiesByKey = new HashMap<>();
            // 追加行位次从"该论文当前最大位次 + 1"继续编号，满足 position > 0 检查约束与唯一约束
            int authorPosition = paperAuthorRepository.findTop1ByPaperIdOrderByAuthorPositionDesc(paperId)
                    .map(PaperAuthor::getAuthorPosition).orElse(0) + 1;
            int keywordPosition = paperKeywordRepository.findTop1ByPaperIdOrderByKeywordPositionDesc(paperId)
                    .map(PaperKeyword::getKeywordPosition).orElse(0) + 1;
            // 同一篇论文内同名实体去重：LLM 可能重复抽取同一个人/主题
            Set<Long> appendedAuthorIds = new HashSet<>();
            Set<Long> appendedKeywordIds = new HashSet<>();

            Map<String, ExtractedEntity> entityMap = new HashMap<>();
            for (ParsedEntity parsedEntity : parsed.entities()) {
                ExtractedEntity ledger = new ExtractedEntity();
                ledger.setPaperId(paperId);
                ledger.setEntityName(parsedEntity.name());
                ledger.setEntityType(parsedEntity.type());
                ledger.setProperties(parsedEntity.properties());

                switch (parsedEntity.type()) {
                    case ExtractedEntity.TYPE_PERSON -> {
                        Resolved<Author> resolved = entityUpsertResolver.resolveAuthor(
                                parsedEntity.name(), null, authorsByName);
                        ledger.setResolvedEntityType(ExtractedEntity.RESOLVED_AUTHOR);
                        ledger.setResolvedEntityId(resolved.entity().getId());
                        if (appendedAuthorIds.add(resolved.entity().getId())) {
                            PaperAuthor row = new PaperAuthor();
                            row.setPaperId(paperId);
                            row.setAuthorId(resolved.entity().getId());
                            row.setAuthorPosition(authorPosition++);
                            paperAuthorRepository.save(row);
                        }
                    }
                    case ExtractedEntity.TYPE_ORGANIZATION -> {
                        Resolved<Institution> resolved = entityUpsertResolver.resolveInstitution(
                                parsedEntity.name(), null, institutionsByName);
                        ledger.setResolvedEntityType(ExtractedEntity.RESOLVED_INSTITUTION);
                        ledger.setResolvedEntityId(resolved.entity().getId());
                        // 只 upsert 机构本身，不在 MySQL 建论文-机构链接：
                        // 机构隶属证据在 Neo4j 用 EXTRACTED_FROM 边表达（阶段 D 投影）
                    }
                    case ExtractedEntity.TYPE_TOPIC -> {
                        Resolved<Keyword> resolved = entityUpsertResolver.resolveKeyword(
                                parsedEntity.name(), null, keywordsByName);
                        ledger.setResolvedEntityType(ExtractedEntity.RESOLVED_KEYWORD);
                        ledger.setResolvedEntityId(resolved.entity().getId());
                        if (appendedKeywordIds.add(resolved.entity().getId())) {
                            PaperKeyword row = new PaperKeyword();
                            row.setPaperId(paperId);
                            row.setKeywordId(resolved.entity().getId());
                            row.setKeywordPosition(keywordPosition++);
                            paperKeywordRepository.save(row);
                        }
                    }
                    default -> { // METHOD / DATASET / TOOL（解析器已保证类型合法）
                        Resolved<ResearchEntity> resolved = entityUpsertResolver.resolveResearchEntity(
                                parsedEntity.name(), parsedEntity.type(), researchEntitiesByKey);
                        ledger.setResolvedEntityType(ExtractedEntity.RESOLVED_RESEARCH_ENTITY);
                        ledger.setResolvedEntityId(resolved.entity().getId());
                        if (!paperResearchEntityRepository.existsByPaperIdAndResearchEntityId(
                                paperId, resolved.entity().getId())) {
                            PaperResearchEntity row = new PaperResearchEntity();
                            row.setPaperId(paperId);
                            row.setResearchEntityId(resolved.entity().getId());
                            paperResearchEntityRepository.save(row);
                        }
                    }
                }
                ledger = extractedEntityRepository.save(ledger);
                // 关系端点按名称引用：同名实体后者覆盖前者（与 Du 的 entityMap 语义一致）
                entityMap.put(parsedEntity.name(), ledger);
            }

            // 3) 关系落库：端点必须命中本次抽取的台账，否则丢弃（跨轮次的名称对不上）
            for (ParsedRelationship parsedRel : parsed.relationships()) {
                ExtractedEntity source = entityMap.get(parsedRel.source());
                ExtractedEntity target = entityMap.get(parsedRel.target());
                if (source == null || target == null) {
                    continue;
                }
                EntityRelationship rel = new EntityRelationship();
                rel.setPaperId(paperId);
                rel.setSourceEntityId(source.getId());
                rel.setTargetEntityId(target.getId());
                rel.setRelationshipType(parsedRel.type());
                rel.setEvidenceText(parsedRel.evidence());
                rel.setConfidence(1.0);
                entityRelationshipRepository.save(rel);
            }

            // 4) 发 PAPER UPSERT 事件：图同步器据此重建该论文的 EXTRACTED_FROM 边与 LLM 关系边。
            //    哪怕本轮抽取结果为空也要发——图里残留的旧抽取边需要借这次投影清掉
            GraphSyncEvent event = new GraphSyncEvent();
            event.setEntityType(GraphSyncEvent.ENTITY_PAPER);
            event.setEntityId(paperId);
            event.setEventType(GraphSyncEvent.EVENT_UPSERT);
            graphSyncEventRepository.save(event);
        });
    }

    /** 更新抽取状态（重新加载后再保存，避免长 LLM 调用期间 @Version 冲突） */
    private void markStatus(Long paperId, String status) {
        paperRepository.findById(paperId).ifPresent(paper -> {
            paper.setExtractionStatus(status);
            paperRepository.save(paper);
        });
    }

    /** 单篇抽取状态（前端轮询用） */
    public ExtractionStatusDto status(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new EntityNotFoundException("论文不存在: id=" + paperId));
        return new ExtractionStatusDto(paperId, paper.getTitle(), paper.getExtractionStatus(),
                extractedEntityRepository.countByPaperId(paperId),
                entityRelationshipRepository.countByPaperId(paperId));
    }

    /** 单篇抽取明细（台账 + 关系，前端详情面板展示） */
    public ExtractionResultDto result(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new EntityNotFoundException("论文不存在: id=" + paperId));
        List<ExtractedEntity> entities = extractedEntityRepository.findByPaperIdOrderByIdAsc(paperId);
        List<EntityRelationship> relationships = entityRelationshipRepository.findByPaperIdOrderByIdAsc(paperId);

        Map<Long, String> nameById = entities.stream()
                .collect(Collectors.toMap(ExtractedEntity::getId, ExtractedEntity::getEntityName));
        List<ExtractionEntityDto> entityDtos = entities.stream()
                .map(e -> new ExtractionEntityDto(e.getId(), e.getEntityName(), e.getEntityType(),
                        e.getResolvedEntityType(), e.getResolvedEntityId()))
                .toList();
        List<ExtractionRelationshipDto> relationshipDtos = relationships.stream()
                .map(r -> new ExtractionRelationshipDto(r.getId(),
                        nameById.getOrDefault(r.getSourceEntityId(), "?"),
                        nameById.getOrDefault(r.getTargetEntityId(), "?"),
                        r.getRelationshipType(), r.getEvidenceText(), r.getConfidence()))
                .toList();
        return new ExtractionResultDto(paperId, paper.getTitle(), paper.getExtractionStatus(),
                entityDtos, relationshipDtos);
    }

    /**
     * 抽取图谱数据（只读，登录即可）：从 MySQL 台账聚合，只返回已归并（resolve）的端点。
     * paperIds 缺省取全部已抽取论文；端点没 resolve 的关系画不出边，一并过滤。
     * 前端 GraphView 把本结果叠加到主图谱上：EXTRACTED_FROM 边连到 paper_* 节点，
     * LLM 关系边连在实体节点之间。
     */
    public ExtractionGraphData graphData(List<Long> paperIds) {
        boolean filtered = paperIds != null && !paperIds.isEmpty();
        List<ExtractedEntity> entities = filtered
                ? extractedEntityRepository.findByPaperIdInAndResolvedEntityTypeNotNull(paperIds)
                : extractedEntityRepository.findByResolvedEntityTypeNotNull();
        List<EntityRelationship> relationships = filtered
                ? entityRelationshipRepository.findByPaperIdIn(paperIds)
                : entityRelationshipRepository.findAll();

        // 台账 id → 图上节点 id（前缀与 GraphView 既有节点命名规则一致：author_/institution_/keyword_/research_）
        Map<Long, String> nodeIdByLedger = new HashMap<>();
        Map<String, ExtractionGraphNode> nodeById = new HashMap<>();
        List<ExtractionGraphNode> nodes = new ArrayList<>();
        for (ExtractedEntity e : entities) {
            String prefix = switch (e.getResolvedEntityType()) {
                case ExtractedEntity.RESOLVED_AUTHOR -> "author_";
                case ExtractedEntity.RESOLVED_INSTITUTION -> "institution_";
                case ExtractedEntity.RESOLVED_KEYWORD -> "keyword_";
                case ExtractedEntity.RESOLVED_RESEARCH_ENTITY -> "research_";
                default -> null;
            };
            if (prefix == null) {
                continue;
            }
            String nodeId = prefix + e.getResolvedEntityId();
            nodeIdByLedger.put(e.getId(), nodeId);
            // 同一业务实体可能被多篇论文抽到：图上只保留一个节点
            nodeById.computeIfAbsent(nodeId, id -> {
                ExtractionGraphNode node = new ExtractionGraphNode(id, e.getEntityName(),
                        id.substring(0, id.indexOf('_')),
                        ExtractedEntity.RESOLVED_RESEARCH_ENTITY.equals(e.getResolvedEntityType())
                                ? e.getEntityType() : null);
                nodes.add(node);
                return node;
            });
        }

        List<ExtractionGraphEdge> edges = new ArrayList<>();
        for (ExtractedEntity e : entities) {
            String nodeId = nodeIdByLedger.get(e.getId());
            if (nodeId == null) {
                continue;
            }
            // EXTRACTED_FROM：实体 ← 来源论文（每篇论文一条，多篇论文抽到同一实体会有多条）
            edges.add(new ExtractionGraphEdge("ext_" + e.getId(), nodeId, "paper_" + e.getPaperId(),
                    "extracted", "EXTRACTED_FROM", null, null));
        }
        for (EntityRelationship r : relationships) {
            String source = nodeIdByLedger.get(r.getSourceEntityId());
            String target = nodeIdByLedger.get(r.getTargetEntityId());
            if (source == null || target == null) {
                continue; // 端点未归并成功的关系不上图
            }
            edges.add(new ExtractionGraphEdge("llm_" + r.getId(), source, target,
                    "llm", r.getRelationshipType(), r.getEvidenceText(), r.getConfidence()));
        }
        return new ExtractionGraphData(nodes, edges);
    }
}
