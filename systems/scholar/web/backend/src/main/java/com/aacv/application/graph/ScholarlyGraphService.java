package com.aacv.application.graph;

import com.aacv.api.graph.GraphResponse;
import com.aacv.api.graph.GraphStatsResponse;
import com.aacv.domain.scholar.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学术成果知识图谱服务：
 * 将 paper/author/authorship/institution/venue/topic 组装为图结构，
 * 支持初始子图加载与按节点动态扩展。
 */
@Service
public class ScholarlyGraphService {

    private static final String NODE_PAPER = "paper";
    private static final String NODE_AUTHOR = "author";
    private static final String NODE_INST = "institution";
    private static final String NODE_TOPIC = "topic";
    private static final String NODE_VENUE = "venue";
    private static final int MAX_NODES = 400;

    private final PaperRepository paperRepository;
    private final AuthorRepository authorRepository;
    private final AuthorshipRepository authorshipRepository;
    private final InstitutionRepository institutionRepository;
    private final VenueRepository venueRepository;
    private final TopicRepository topicRepository;
    private final PaperTopicRepository paperTopicRepository;
    private final PaperReferenceRepository paperReferenceRepository;

    public ScholarlyGraphService(
            PaperRepository paperRepository,
            AuthorRepository authorRepository,
            AuthorshipRepository authorshipRepository,
            InstitutionRepository institutionRepository,
            VenueRepository venueRepository,
            TopicRepository topicRepository,
            PaperTopicRepository paperTopicRepository,
            PaperReferenceRepository paperReferenceRepository) {
        this.paperRepository = paperRepository;
        this.authorRepository = authorRepository;
        this.authorshipRepository = authorshipRepository;
        this.institutionRepository = institutionRepository;
        this.venueRepository = venueRepository;
        this.topicRepository = topicRepository;
        this.paperTopicRepository = paperTopicRepository;
        this.paperReferenceRepository = paperReferenceRepository;
    }

    @Transactional(readOnly = true)
    public GraphStatsResponse loadStats() {
        GraphStatsResponse stats = new GraphStatsResponse();
        stats.setTotalPapers(paperRepository.count());
        stats.setTotalAuthors(authorRepository.count());
        stats.setTotalInstitutions(institutionRepository.count());
        stats.setTotalVenues(venueRepository.count());
        stats.setTotalTopics(topicRepository.count());

        final int topN = 10;
        stats.setTopAuthors(toRank(authorshipRepository.countByAuthor(),
                id -> authorRepository.findById(id).map(Author::getName).orElse("未知"), topN, new java.util.HashSet<>()));
        stats.setTopTopics(toRank(paperTopicRepository.countByTopic(),
                id -> topicRepository.findById(id).map(Topic::getName).orElse("未知"), topN, new java.util.HashSet<>()));
        stats.setTopVenues(toRank(paperRepository.countByVenue(),
                id -> venueRepository.findById(id).map(Venue::getName).orElse("未知"), topN, new java.util.HashSet<>()));
        stats.setTopInstitutions(toRank(authorshipRepository.countByInstitution(),
                id -> institutionRepository.findById(id).map(Institution::getName).orElse("未知"), topN, new java.util.HashSet<>()));
        return stats;
    }

    /**
     * 将分组统计结果 `[id, count]` 映射为 RankItem（过滤 null id，限量 topN）。
     */
    private List<GraphStatsResponse.RankItem> toRank(List<Object[]> rows,
                                                     java.util.function.Function<String, String> nameResolver,
                                                     int topN, java.util.Set<String> seen) {
        List<GraphStatsResponse.RankItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            if (result.size() >= topN) break;
            Object idObj = row[0];
            if (idObj == null) continue;
            String id = idObj.toString();
            if (!seen.add(id)) continue;
            long count = ((Number) row[1]).longValue();
            String name = nameResolver.apply(id);
            if (name == null || name.isBlank()) continue;
            result.add(new GraphStatsResponse.RankItem(name, count));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public GraphResponse loadInitial() {
        Set<String> paperIds = new LinkedHashSet<>();
        paperRepository.findTop10ByOrderByPublicationDateDesc()
                .forEach(p -> paperIds.add(p.getId()));
        // 收集作者（不做二次展开，按需扩容可交给前端点击展开）
        Set<String> authorIds = new LinkedHashSet<>();
        for (Authorship a : authorshipRepository.findByPaperIdIn(new ArrayList<>(paperIds))) {
            authorIds.add(a.getAuthorId());
        }
        return buildGraph(paperIds, authorIds);
    }

    @Transactional(readOnly = true)
    public GraphResponse expand(String nodeId) {
        if (nodeId == null) {
            return empty();
        }
        int idx = nodeId.indexOf('_');
        if (idx <= 0) {
            return empty();
        }
        String type = nodeId.substring(0, idx);
        String bareId = nodeId.substring(idx + 1);

        switch (type) {
            case NODE_PAPER:
                return expandPaper(bareId);
            case NODE_AUTHOR:
                return expandAuthor(bareId);
            case NODE_TOPIC:
                return expandTopic(bareId);
            default:
                return empty();
        }
    }

    private GraphResponse expandPaper(String paperId) {
        Set<String> paperIds = new LinkedHashSet<>();
        paperIds.add(paperId);
        Set<String> authorIds = new LinkedHashSet<>();
        for (Authorship a : authorshipRepository.findByPaperId(paperId)) {
            authorIds.add(a.getAuthorId());
        }
        // 被引文献也纳入
        paperReferenceRepository.findByCitingPaperId(paperId)
                .forEach(ref -> paperIds.add(ref.getCitedPaperId()));
        return buildGraph(paperIds, authorIds);
    }

    private GraphResponse expandAuthor(String authorId) {
        Set<String> authorIds = new LinkedHashSet<>();
        authorIds.add(authorId);
        Set<String> paperIds = new LinkedHashSet<>();
        List<Authorship> mine = authorshipRepository.findByAuthorId(authorId);
        for (Authorship a : mine) {
            paperIds.add(a.getPaperId());
        }
        return buildGraph(paperIds, authorIds);
    }

    private GraphResponse expandTopic(String topicId) {
        Set<String> paperIds = new LinkedHashSet<>();
        paperTopicRepository.findByTopicId(topicId)
                .forEach(pt -> paperIds.add(pt.getPaperId()));
        return buildGraph(paperIds, new LinkedHashSet<>());
    }

    private GraphResponse buildGraph(Set<String> paperIds, Set<String> authorIds) {
        List<Paper> papers = paperRepository.findAllById(paperIds);
        List<Authorship> authorships = authorshipRepository.findByPaperIdIn(new ArrayList<>(paperIds));
        List<PaperTopic> paperTopics = paperTopicRepository.findByPaperIdIn(new ArrayList<>(paperIds));

        // 收集关联 id
        Set<String> instIds = new LinkedHashSet<>();
        Set<String> venueIds = new LinkedHashSet<>();
        Set<String> topicIds = new LinkedHashSet<>();
        for (Authorship a : authorships) {
            if (a.getInstitutionId() != null) instIds.add(a.getInstitutionId());
            authorIds.add(a.getAuthorId());
        }
        for (Paper p : papers) {
            if (p.getVenueId() != null) venueIds.add(p.getVenueId());
        }
        for (PaperTopic pt : paperTopics) {
            topicIds.add(pt.getTopicId());
        }

        Map<String, Author> authors = authorRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Author::getId, a -> a, (x, y) -> x));
        Map<String, Institution> institutions = institutionRepository.findAllById(instIds).stream()
                .collect(Collectors.toMap(Institution::getId, i -> i, (x, y) -> x));
        Map<String, Venue> venues = venueRepository.findAllById(venueIds).stream()
                .collect(Collectors.toMap(Venue::getId, v -> v, (x, y) -> x));
        Map<String, Topic> topics = topicRepository.findAllById(topicIds).stream()
                .collect(Collectors.toMap(Topic::getId, t -> t, (x, y) -> x));

        // 节点 id 集合
        Set<String> nodeIds = new LinkedHashSet<>();
        List<GraphResponse.GraphNode> nodes = new ArrayList<>();

        for (Paper p : papers) {
            String nid = nodeId(NODE_PAPER, p.getId());
            if (nodeIds.add(nid)) nodes.add(new GraphResponse.GraphNode(nid, p.getTitle(), NODE_PAPER, 0));
        }
        for (Author a : authors.values()) {
            String nid = nodeId(NODE_AUTHOR, a.getId());
            if (nodeIds.add(nid)) nodes.add(new GraphResponse.GraphNode(nid, a.getName(), NODE_AUTHOR, 0));
        }
        for (Institution i : institutions.values()) {
            String nid = nodeId(NODE_INST, i.getId());
            if (nodeIds.add(nid)) nodes.add(new GraphResponse.GraphNode(nid, i.getName(), NODE_INST, 0));
        }
        for (Venue v : venues.values()) {
            String nid = nodeId(NODE_VENUE, v.getId());
            if (nodeIds.add(nid)) nodes.add(new GraphResponse.GraphNode(nid, v.getName(), NODE_VENUE, 0));
        }
        for (Topic t : topics.values()) {
            String nid = nodeId(NODE_TOPIC, t.getId());
            if (nodeIds.add(nid)) nodes.add(new GraphResponse.GraphNode(nid, t.getName(), NODE_TOPIC, 0));
        }

        // 限量裁剪
        if (nodes.size() > MAX_NODES) {
            List<GraphResponse.GraphNode> kept = new ArrayList<>(nodes.subList(0, MAX_NODES));
            nodeIds.clear();
            kept.forEach(n -> nodeIds.add(n.getId()));
            nodes = kept;
        }

        // 边
        List<GraphResponse.GraphEdge> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();
        long seq = 0;

        for (Authorship a : authorships) {
            String pn = nodeId(NODE_PAPER, a.getPaperId());
            String an = nodeId(NODE_AUTHOR, a.getAuthorId());
            if (nodeIds.contains(pn) && nodeIds.contains(an)) {
                seq = addEdge(edges, edgeKeys, seq, pn, an, "authoredBy");
            }
            if (a.getInstitutionId() != null) {
                String inn = nodeId(NODE_INST, a.getInstitutionId());
                if (nodeIds.contains(an) && nodeIds.contains(inn)) {
                    seq = addEdge(edges, edgeKeys, seq, an, inn, "affiliatedTo");
                }
            }
        }
        for (Paper p : papers) {
            if (p.getVenueId() != null) {
                String pn = nodeId(NODE_PAPER, p.getId());
                String vn = nodeId(NODE_VENUE, p.getVenueId());
                if (nodeIds.contains(pn) && nodeIds.contains(vn)) {
                    seq = addEdge(edges, edgeKeys, seq, pn, vn, "publishedIn");
                }
            }
        }
        for (PaperTopic pt : paperTopics) {
            String pn = nodeId(NODE_PAPER, pt.getPaperId());
            String tn = nodeId(NODE_TOPIC, pt.getTopicId());
            if (nodeIds.contains(pn) && nodeIds.contains(tn)) {
                seq = addEdge(edges, edgeKeys, seq, pn, tn, "hasTopic");
            }
        }

        // 回填 degree
        Map<String, Integer> degree = new HashMap<>();
        for (GraphResponse.GraphEdge e : edges) {
            degree.merge(e.getSource(), 1, Integer::sum);
            degree.merge(e.getTarget(), 1, Integer::sum);
        }
        List<GraphResponse.GraphNode> finalNodes = new ArrayList<>();
        for (GraphResponse.GraphNode n : nodes) {
            finalNodes.add(new GraphResponse.GraphNode(
                    n.getId(), n.getLabel(), n.getType(), degree.getOrDefault(n.getId(), 0)));
        }

        long totalNodes = paperRepository.count() + authorRepository.count()
                + institutionRepository.count() + venueRepository.count() + topicRepository.count();
        long totalEdges = authorshipRepository.count() + paperTopicRepository.count()
                + paperReferenceRepository.count() + paperRepository.count();

        return new GraphResponse(finalNodes, edges, totalNodes, totalEdges);
    }

    private long addEdge(List<GraphResponse.GraphEdge> edges, Set<String> edgeKeys,
                         long seq, String src, String tgt, String type) {
        String key = src + "|" + tgt + "|" + type;
        if (edgeKeys.add(key)) {
            edges.add(new GraphResponse.GraphEdge("e" + seq++, src, tgt, type));
        }
        return seq;
    }

    private static String nodeId(String type, String bareId) {
        return type + "_" + bareId;
    }

    private static GraphResponse empty() {
        return new GraphResponse(Collections.emptyList(), Collections.emptyList(), 0, 0);
    }
}