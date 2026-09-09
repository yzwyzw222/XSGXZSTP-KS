package com.aacv.application.analytics;

import com.aacv.api.analytics.AnalyticsDtos.CitationStats;
import com.aacv.api.analytics.AnalyticsDtos.Link;
import com.aacv.api.analytics.AnalyticsDtos.NameCount;
import com.aacv.api.analytics.AnalyticsDtos.NetworkGraph;
import com.aacv.api.analytics.AnalyticsDtos.Node;
import com.aacv.api.analytics.AnalyticsDtos.TopicEvolutionResponse;
import com.aacv.api.analytics.AnalyticsDtos.TopicSeries;
import com.aacv.api.analytics.AnalyticsDtos.YearCount;
import com.aacv.api.analytics.AnalyticsDtos.YearRoleCount;
import com.aacv.domain.scholar.Author;
import com.aacv.domain.scholar.AuthorRepository;
import com.aacv.domain.scholar.Authorship;
import com.aacv.domain.scholar.AuthorshipRepository;
import com.aacv.domain.scholar.InstitutionRepository;
import com.aacv.domain.scholar.Paper;
import com.aacv.domain.scholar.PaperReferenceRepository;
import com.aacv.domain.scholar.PaperRepository;
import com.aacv.domain.scholar.PaperTopic;
import com.aacv.domain.scholar.PaperTopicRepository;
import com.aacv.domain.scholar.Topic;
import com.aacv.domain.scholar.TopicRepository;
import com.aacv.domain.scholar.Venue;
import com.aacv.domain.scholar.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 科研分析服务：发表趋势、主题演化、关键词共现、合作网络、期刊/类型分布、被引统计、论文相似网络。
 * 所有方法支持可选 authorId：传入时仅聚合该学者关联的论文数据。
 */
@Service
public class AnalyticsService {

    private final PaperRepository paperRepository;
    private final PaperTopicRepository paperTopicRepository;
    private final AuthorshipRepository authorshipRepository;
    private final PaperReferenceRepository paperReferenceRepository;
    private final TopicRepository topics;
    private final AuthorRepository authors;
    private final VenueRepository venues;
    private final InstitutionRepository institutions;

    public AnalyticsService(PaperRepository paperRepository,
                            PaperTopicRepository paperTopicRepository,
                            AuthorshipRepository authorshipRepository,
                            PaperReferenceRepository paperReferenceRepository,
                            TopicRepository topicRepository,
                            AuthorRepository authorRepository,
                            VenueRepository venueRepository,
                            InstitutionRepository institutionRepository) {
        this.paperRepository = paperRepository;
        this.paperTopicRepository = paperTopicRepository;
        this.authorshipRepository = authorshipRepository;
        this.paperReferenceRepository = paperReferenceRepository;
        this.topics = topicRepository;
        this.authors = authorRepository;
        this.venues = venueRepository;
        this.institutions = institutionRepository;
    }

    // ---------- 1. 发表时间趋势 ----------

    @Transactional(readOnly = true)
    public List<YearCount> publicationTrend(String authorId) {
        List<Paper> scope = scopedPapers(authorId);
        if (scope != null) {
            Map<Integer, Long> byYear = new TreeMap<>();
            for (Paper p : scope) {
                if (p.getYear() != null) {
                    byYear.merge(p.getYear(), 1L, Long::sum);
                }
            }
            return byYear.entrySet().stream()
                    .map(e -> new YearCount(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        }
        return paperRepository.countByYear().stream()
                .map(row -> new YearCount((Integer) row[0], (Long) row[1]))
                .collect(Collectors.toList());
    }

    // ---------- 1b. 年份-作者角色统计（一作/通讯） ----------

    @Transactional(readOnly = true)
    public List<YearRoleCount> authorRoleTrend(String authorId) {
        Map<String, Integer> paperYear = new HashMap<>();
        for (Paper p : paperRepository.findAll()) {
            if (p.getYear() != null) {
                paperYear.put(p.getId(), p.getYear());
            }
        }

        // year -> [total, firstAuthor, corresponding]
        Map<Integer, long[]> byYear = new TreeMap<>();
        for (Map.Entry<String, Integer> e : paperYear.entrySet()) {
            byYear.computeIfAbsent(e.getValue(), k -> new long[3])[0]++;
        }
        for (Authorship a : authorshipRepository.findAll()) {
            Integer year = paperYear.get(a.getPaperId());
            if (year == null) {
                continue;
            }
            // 指定学者时只统计该学者的署名
            if (authorId != null && !authorId.isBlank() && !authorId.equals(a.getAuthorId())) {
                continue;
            }
            long[] slot = byYear.computeIfAbsent(year, k -> new long[3]);
            if (a.getAuthorOrder() != null && a.getAuthorOrder() == 1) {
                slot[1]++;
            }
            if (a.isCorresponding()) {
                slot[2]++;
            }
        }

        return byYear.entrySet().stream()
                .map(e -> new YearRoleCount(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .collect(Collectors.toList());
    }

    // ---------- 1c. 研究方向（主题）分布 ----------

    @Transactional(readOnly = true)
    public List<NameCount> topicDistribution(int topN, String authorId) {
        Map<String, String> topicNames = topics.findAll().stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName, (a, b) -> a));

        List<PaperTopic> pts = scopedPaperTopics(authorId);
        if (pts != null) {
            Map<String, Long> count = new LinkedHashMap<>();
            for (PaperTopic pt : pts) {
                count.merge(pt.getTopicId(), 1L, Long::sum);
            }
            return toRanked(count, topN)
                    .stream()
                    .map(e -> new NameCount(topicNames.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                    .collect(Collectors.toList());
        }
        return paperTopicRepository.countByTopic().stream()
                .limit(topN)
                .map(row -> new NameCount(
                        topicNames.getOrDefault((String) row[0], (String) row[0]),
                        (Long) row[1]))
                .collect(Collectors.toList());
    }

    // ---------- 2. 研究主题演化 ----------

    @Transactional(readOnly = true)
    public TopicEvolutionResponse topicEvolution(int topN, String authorId) {
        List<PaperTopic> all = scopedPaperTopics(authorId);
        if (all == null) {
            all = paperTopicRepository.findAll();
        }
        Map<String, Integer> paperYear = new HashMap<>();
        for (Paper p : paperRepository.findAll()) {
            if (p.getYear() != null) {
                paperYear.put(p.getId(), p.getYear());
            }
        }
        Map<String, String> topicNames = topics.findAll().stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName, (a, b) -> a));

        // topicId -> (year -> count)
        Map<String, Map<Integer, Long>> matrix = new LinkedHashMap<>();
        for (PaperTopic pt : all) {
            Integer year = paperYear.get(pt.getPaperId());
            if (year == null) {
                continue;
            }
            matrix.computeIfAbsent(pt.getTopicId(), k -> new HashMap<>())
                    .merge(year, 1L, Long::sum);
        }

        List<Integer> years = matrix.values().stream()
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<TopicSeries> series = matrix.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Map<Integer, Long>> e) ->
                        e.getValue().values().stream().mapToLong(Long::longValue).sum()).reversed())
                .limit(topN)
                .map(e -> new TopicSeries(
                        topicNames.getOrDefault(e.getKey(), e.getKey()),
                        years.stream().map(y -> e.getValue().getOrDefault(y, 0L)).collect(Collectors.toList())))
                .collect(Collectors.toList());

        return new TopicEvolutionResponse(years, series);
    }

    // ---------- 3. 关键词（主题）共现网络 ----------

    @Transactional(readOnly = true)
    public NetworkGraph keywordCooccurrence(int topN, long minWeight, String authorId) {
        List<PaperTopic> all = scopedPaperTopics(authorId);
        if (all == null) {
            all = paperTopicRepository.findAll();
        }
        Map<String, String> topicNames = topics.findAll().stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName, (a, b) -> a));

        // paperId -> Set<topicId>
        Map<String, Set<String>> paperTopics = new HashMap<>();
        Map<String, Long> topicCount = new HashMap<>();
        for (PaperTopic pt : all) {
            paperTopics.computeIfAbsent(pt.getPaperId(), k -> new HashSet<>()).add(pt.getTopicId());
            topicCount.merge(pt.getTopicId(), 1L, Long::sum);
        }

        Map<String, Long> pairCount = countPairs(paperTopics);
        return buildNetwork(id -> topicNames.getOrDefault(id, id), topicCount, pairCount, "主题", topN, minWeight);
    }

    // ---------- 5. 合作作者网络 ----------

    @Transactional(readOnly = true)
    public NetworkGraph coauthorNetwork(int topN, long minWeight, String authorId) {
        List<Authorship> all = scopedAuthorships(authorId);
        if (all == null) {
            all = authorshipRepository.findAll();
        }
        Map<String, String> authorNames = authors.findAll().stream()
                .collect(Collectors.toMap(Author::getId, Author::getName, (a, b) -> a));

        Map<String, Set<String>> paperAuthors = new HashMap<>();
        Map<String, Long> authorCount = new HashMap<>();
        for (Authorship a : all) {
            paperAuthors.computeIfAbsent(a.getPaperId(), k -> new HashSet<>()).add(a.getAuthorId());
            authorCount.merge(a.getAuthorId(), 1L, Long::sum);
        }

        Map<String, Long> pairCount = countPairs(paperAuthors);
        return buildNetwork(id -> authorNames.getOrDefault(id, id), authorCount, pairCount, "作者", topN, minWeight);
    }

    // ---------- 6. 机构合作网络（从署名解析） ----------

    @Transactional(readOnly = true)
    public NetworkGraph institutionNetwork(int topN, long minWeight, String authorId) {
        List<Authorship> all = scopedAuthorships(authorId);
        if (all == null) {
            all = authorshipRepository.findAll();
        }

        // paperId -> Set<机构名>（来自 raw_affiliation 解析）
        Map<String, Set<String>> paperInstitutions = new HashMap<>();
        for (Authorship a : all) {
            String raw = a.getRawAffiliation();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Set<String> insts = paperInstitutions.computeIfAbsent(a.getPaperId(), k -> new HashSet<>());
            for (String name : raw.split("[;；]")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    insts.add(trimmed);
                }
            }
        }

        Map<String, Long> instCount = new HashMap<>();
        paperInstitutions.values().forEach(s -> s.forEach(n -> instCount.merge(n, 1L, Long::sum)));

        Map<String, Long> pairCount = countPairs(paperInstitutions);
        return buildNetwork(Function.identity(), instCount, pairCount, "机构", topN, minWeight);
    }

    // ---------- 7. 期刊分布 ----------

    @Transactional(readOnly = true)
    public List<NameCount> venueDistribution(int topN, String authorId) {
        Map<String, String> venueNames = venues.findAll().stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName, (a, b) -> a));

        List<Paper> scope = scopedPapers(authorId);
        if (scope != null) {
            Map<String, Long> count = new LinkedHashMap<>();
            for (Paper p : scope) {
                if (p.getVenueId() != null) {
                    count.merge(p.getVenueId(), 1L, Long::sum);
                }
            }
            return toRanked(count, topN).stream()
                    .map(e -> new NameCount(venueNames.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                    .collect(Collectors.toList());
        }
        return paperRepository.countByVenue().stream()
                .limit(topN)
                .map(row -> new NameCount(
                        venueNames.getOrDefault((String) row[0], (String) row[0]),
                        (Long) row[1]))
                .collect(Collectors.toList());
    }

    // ---------- 9. 论文类型分布 ----------

    @Transactional(readOnly = true)
    public List<NameCount> paperTypeDistribution(String authorId) {
        List<Paper> scope = scopedPapers(authorId);
        if (scope != null) {
            Map<String, Long> count = new LinkedHashMap<>();
            for (Paper p : scope) {
                if (p.getPaperType() != null) {
                    count.merge(p.getPaperType(), 1L, Long::sum);
                }
            }
            return toRanked(count, Integer.MAX_VALUE).stream()
                    .map(e -> new NameCount(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        }
        return paperRepository.countByType().stream()
                .map(row -> new NameCount((String) row[0], (Long) row[1]))
                .collect(Collectors.toList());
    }

    // ---------- 8. 被引统计 ----------

    @Transactional(readOnly = true)
    public CitationStats citationStats(int topN, String authorId) {
        List<Object[]> rows = paperReferenceRepository.countByCited();

        // 指定学者时只统计引用其论文的数据
        List<String> scope = scopePaperIds(authorId);
        if (scope != null) {
            Set<String> scopeSet = new HashSet<>(scope);
            rows = rows.stream()
                    .filter(r -> scopeSet.contains((String) r[0]))
                    .collect(Collectors.toList());
        }

        long total = rows.stream().mapToLong(r -> (Long) r[1]).sum();
        long citedPapers = rows.size();

        Map<String, Paper> paperMap = paperRepository.findAll().stream()
                .collect(Collectors.toMap(Paper::getId, p -> p, (a, b) -> a));
        List<NameCount> topCited = rows.stream()
                .limit(topN)
                .map(row -> {
                    Paper p = paperMap.get((String) row[0]);
                    String title = p != null ? p.getTitle() : (String) row[0];
                    // 标题过长截断
                    if (title != null && title.length() > 40) {
                        title = title.substring(0, 40) + "…";
                    }
                    return new NameCount(title, (Long) row[1]);
                })
                .collect(Collectors.toList());

        long denominator = scope != null ? scope.size() : paperRepository.count();
        double avg = denominator > 0 ? (double) total / denominator : 0;
        return new CitationStats(total, Math.round(avg * 100) / 100.0, citedPapers, topCited);
    }

    // ---------- 4. 论文相似网络（共享主题 >= 2 视为相似） ----------

    @Transactional(readOnly = true)
    public NetworkGraph paperSimilarityNetwork(int topN, long minShared, String authorId) {
        List<PaperTopic> all = scopedPaperTopics(authorId);
        if (all == null) {
            all = paperTopicRepository.findAll();
        }
        Map<String, Set<String>> paperTopics = new HashMap<>();
        for (PaperTopic pt : all) {
            paperTopics.computeIfAbsent(pt.getPaperId(), k -> new HashSet<>()).add(pt.getTopicId());
        }

        // 取主题数最多的 topN 篇论文参与构图
        List<String> selected = paperTopics.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Set<String>> e) ->
                        e.getValue().size()).reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<String, Paper> paperMap = paperRepository.findAllById(selected).stream()
                .collect(Collectors.toMap(Paper::getId, p -> p, (a, b) -> a));

        Map<String, Long> nodeValue = new LinkedHashMap<>();
        for (String id : selected) {
            nodeValue.put(id, (long) paperTopics.get(id).size());
        }

        // 两两求共享主题数
        Map<String, Long> pairCount = new HashMap<>();
        for (int i = 0; i < selected.size(); i++) {
            for (int j = i + 1; j < selected.size(); j++) {
                Set<String> a = paperTopics.get(selected.get(i));
                Set<String> b = paperTopics.get(selected.get(j));
                long shared = a.stream().filter(b::contains).count();
                if (shared >= minShared) {
                    pairCount.merge(pairKey(selected.get(i), selected.get(j)), shared, Long::sum);
                }
            }
        }

        List<Node> nodes = selected.stream()
                .map(id -> {
                    Paper p = paperMap.get(id);
                    String label = p != null && p.getTitle() != null ? p.getTitle() : id;
                    if (label.length() > 24) {
                        label = label.substring(0, 24) + "…";
                    }
                    return new Node(id, label, nodeValue.getOrDefault(id, 0L), "论文");
                })
                .collect(Collectors.toList());

        List<Link> links = pairCount.entrySet().stream()
                .map(e -> {
                    String[] parts = e.getKey().split("\\|");
                    return new Link(parts[0], parts[1], e.getValue());
                })
                .collect(Collectors.toList());

        return new NetworkGraph(nodes, links);
    }

    // ---------- 学者范围辅助 ----------

    /** 指定学者时返回该学者的论文列表（含年份/类型/期刊字段），否则返回 null 表示全库。 */
    private List<Paper> scopedPapers(String authorId) {
        List<String> ids = scopePaperIds(authorId);
        if (ids == null) {
            return null;
        }
        return paperRepository.findAllById(ids);
    }

    /** 指定学者时返回该学者的论文 ID 集合，否则返回 null 表示全库。 */
    private List<String> scopePaperIds(String authorId) {
        if (authorId == null || authorId.isBlank()) {
            return null;
        }
        return authorshipRepository.findByAuthorId(authorId).stream()
                .map(Authorship::getPaperId)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 指定学者时返回该学者论文的主题关联，否则返回 null 表示全库。 */
    private List<PaperTopic> scopedPaperTopics(String authorId) {
        List<String> ids = scopePaperIds(authorId);
        if (ids == null) {
            return null;
        }
        return ids.isEmpty() ? List.of() : paperTopicRepository.findByPaperIdIn(ids);
    }

    /** 指定学者时返回该学者论文的全部署名（含合作者），否则返回 null 表示全库。 */
    private List<Authorship> scopedAuthorships(String authorId) {
        List<String> ids = scopePaperIds(authorId);
        if (ids == null) {
            return null;
        }
        return ids.isEmpty() ? List.of() : authorshipRepository.findByPaperIdIn(ids);
    }

    /** 计数降序排序并截取 topN。 */
    private List<Map.Entry<String, Long>> toRanked(Map<String, Long> countMap, int topN) {
        return countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toList());
    }

    // ---------- 公共辅助 ----------

    /** 对 paperId -> Set<成员> 的分组结构统计两两共现次数。 */
    private <T> Map<String, Long> countPairs(Map<String, Set<T>> groups) {
        Map<String, Long> pairs = new HashMap<>();
        for (Set<T> members : groups.values()) {
            List<T> list = new ArrayList<>(members);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    String key = pairKey(String.valueOf(list.get(i)), String.valueOf(list.get(j)));
                    pairs.merge(key, 1L, Long::sum);
                }
            }
        }
        return pairs;
    }

    private String pairKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /** 由节点计数 + 共现计数构建网络图，取计数前 topN 的节点，边权重 >= minWeight。 */
    private NetworkGraph buildNetwork(Function<String, String> nameResolver, Map<String, Long> nodeCount,
                                      Map<String, Long> pairCount, String category,
                                      int topN, long minWeight) {
        List<Node> nodes = nodeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .map(e -> new Node(e.getKey(),
                        nameResolver.apply(e.getKey()),
                        e.getValue(), category))
                .collect(Collectors.toList());
        Set<String> kept = nodes.stream().map(Node::id).collect(Collectors.toSet());

        List<Link> links = pairCount.entrySet().stream()
                .filter(e -> e.getValue() >= minWeight)
                .map(e -> {
                    String[] parts = e.getKey().split("\\|");
                    return new Link(parts[0], parts[1], e.getValue());
                })
                .filter(l -> kept.contains(l.source()) && kept.contains(l.target()))
                .sorted(Comparator.comparingLong(Link::weight).reversed())
                .collect(Collectors.toList());

        return new NetworkGraph(nodes, links);
    }
}
