package com.aacv.system.graph.application;

import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.graph.domain.GraphTypeDefinition;
import com.aacv.system.graph.domain.GraphView;
import com.aacv.system.graph.domain.GraphView.Edge;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GraphPresentationService {
    static final int COAUTHOR_LIMIT = 1000;
    private final GraphTypeService typeService;

    public GraphPresentationService(GraphTypeService typeService) { this.typeService = typeService; }

    /** 拓扑只取自本次 Neo4j 结果，样式与类型审核状态每次从 MySQL 批量读取。 */
    public GraphView present(GraphView graph, boolean includeCoauthors) {
        List<GraphTypeDefinition> definitions = typeService.list();
        definitions.forEach(GraphTypeService::validate);
        Set<String> configured = definitions.stream().map(value -> value.kind() + ":" + value.code())
                .collect(Collectors.toSet());
        Derived derived = includeCoauthors ? coauthors(graph) : new Derived(List.of(), false);
        List<Edge> edges = new ArrayList<>(graph.edges());
        edges.addAll(derived.edges());
        if (graph.nodes().stream().anyMatch(node -> !configured.contains("NODE:" + node.type()))
                || edges.stream().anyMatch(edge -> !configured.contains("RELATIONSHIP:" + edge.type()))) {
            throw new IllegalStateException("MySQL 图谱类型配置缺失");
        }
        String suggestion = graph.narrowingSuggestion();
        if (derived.truncated()) {
            suggestion = (suggestion == null ? "" : suggestion + "；") + "合作关系达到1000条上限，请缩小查询范围";
        }
        return new GraphView(graph.nodes(), edges, graph.rootNodeId(), graph.truncated() || derived.truncated(),
                suggestion, graph.appliedLimits(), graph.syncedAt(), graph.projectionLagSeconds(),
                graph.traceId(), definitions);
    }

    /** 共同作品是当前受限子图内的证据，不推断全库合作数量，不写回 Neo4j。 */
    static Derived coauthors(GraphView graph) {
        Map<String, GraphNodeType> nodeTypes = graph.nodes().stream()
                .collect(Collectors.toMap(GraphView.Node::id, GraphView.Node::type));
        Map<String, Set<String>> authorsByWork = new LinkedHashMap<>();
        for (Edge edge : graph.edges()) {
            if (edge.type().equals("AUTHORED") && nodeTypes.get(edge.source()) == GraphNodeType.AUTHOR
                    && nodeTypes.get(edge.target()) == GraphNodeType.ACHIEVEMENT) {
                authorsByWork.computeIfAbsent(edge.target(), ignored -> new TreeSet<>()).add(edge.source());
            }
        }
        Map<Pair, Set<String>> worksByPair = new LinkedHashMap<>();
        boolean truncated = false;
        for (var entry : authorsByWork.entrySet()) {
            List<String> authors = new ArrayList<>(entry.getValue());
            for (int left = 0; left < authors.size(); left++) {
                for (int right = left + 1; right < authors.size(); right++) {
                    Pair pair = new Pair(authors.get(left), authors.get(right));
                    if (!worksByPair.containsKey(pair) && worksByPair.size() >= COAUTHOR_LIMIT) {
                        truncated = true;
                        continue;
                    }
                    worksByPair.computeIfAbsent(pair, ignored -> new LinkedHashSet<>()).add(entry.getKey());
                }
            }
        }
        List<Edge> edges = worksByPair.entrySet().stream().map(entry -> {
            Pair pair = entry.getKey();
            return new Edge("COAUTHORED:" + pair.source() + ":" + pair.target(), "COAUTHORED",
                    pair.source(), pair.target(), Map.of("derived", true,
                    "evidenceScope", "CURRENT_SUBGRAPH", "sharedWorkIds", List.copyOf(entry.getValue()),
                    "sharedWorkCount", entry.getValue().size()));
        }).toList();
        return new Derived(edges, truncated);
    }

    private record Pair(String source, String target) { }
    record Derived(List<Edge> edges, boolean truncated) { }
}
