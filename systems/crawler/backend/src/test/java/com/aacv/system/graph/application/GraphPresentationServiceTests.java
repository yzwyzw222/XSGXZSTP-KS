package com.aacv.system.graph.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.graph.domain.GraphView;
import com.aacv.system.graph.domain.GraphView.Edge;
import com.aacv.system.graph.domain.GraphView.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphPresentationServiceTests {
    @Test
    void derivesOnePairWithDistinctSharedWorksAndIgnoresInvalidEndpoints() {
        var graph = graph(List.of(node("a", GraphNodeType.AUTHOR), node("b", GraphNodeType.AUTHOR),
                node("w1", GraphNodeType.ACHIEVEMENT), node("w2", GraphNodeType.ACHIEVEMENT)),
                List.of(edge("a", "w1"), edge("b", "w1"), edge("a", "w1"),
                        edge("a", "w2"), edge("b", "w2"), edge("missing", "w1"), edge("w1", "a")));
        var result = GraphPresentationService.coauthors(graph);
        assertFalse(result.truncated());
        assertEquals(1, result.edges().size());
        Edge pair = result.edges().getFirst();
        assertEquals("a", pair.source());
        assertEquals("b", pair.target());
        assertEquals(List.of("w1", "w2"), pair.properties().get("sharedWorkIds"));
        assertEquals(2, pair.properties().get("sharedWorkCount"));
        assertEquals(true, pair.properties().get("derived"));
    }

    @Test
    void boundsDenseCoauthorshipAndSupportsEmptyGraphs() {
        assertTrue(GraphPresentationService.coauthors(graph(List.of(), List.of())).edges().isEmpty());
        List<Node> nodes = new ArrayList<>(List.of(node("w", GraphNodeType.ACHIEVEMENT)));
        List<Edge> edges = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            String id = "a" + index;
            nodes.add(node(id, GraphNodeType.AUTHOR));
            edges.add(edge(id, "w"));
        }
        var result = GraphPresentationService.coauthors(graph(nodes, edges));
        assertEquals(1000, result.edges().size());
        assertTrue(result.truncated());
        assertEquals(1000, result.edges().stream().map(Edge::id).distinct().count());
    }

    @Test
    void missingMysqlConfigurationFailsExplicitly() {
        var types = mock(GraphTypeService.class);
        when(types.list()).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> new GraphPresentationService(types)
                .present(graph(List.of(node("a", GraphNodeType.AUTHOR)), List.of()), true));
    }

    private Node node(String id, GraphNodeType type) { return new Node(id, id, type, id, Map.of()); }
    private Edge edge(String source, String target) { return new Edge(source + target, "AUTHORED", source, target, Map.of()); }
    private GraphView graph(List<Node> nodes, List<Edge> edges) {
        return new GraphView(nodes, edges, "w1", false, null, new GraphView.AppliedLimits(2, 300, 0),
                null, null, "test", List.of());
    }
}
