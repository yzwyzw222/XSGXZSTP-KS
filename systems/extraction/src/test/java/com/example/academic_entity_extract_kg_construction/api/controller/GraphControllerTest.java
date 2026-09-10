package com.example.academic_entity_extract_kg_construction.api.controller;

import com.example.academic_entity_extract_kg_construction.application.service.AuthorService;
import com.example.academic_entity_extract_kg_construction.application.service.PaperService;
import com.example.academic_entity_extract_kg_construction.infrastructure.exception.GlobalExceptionHandler;
import com.example.academic_entity_extract_kg_construction.infrastructure.exception.PaperNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.types.Node;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GraphControllerTest {
    private final Driver driver = mock(Driver.class);
    private final Session session = mock(Session.class);
    private final AuthorService authors = mock(AuthorService.class);
    private final PaperService papers = mock(PaperService.class);
    private final GraphController controller = new GraphController(driver, authors, papers);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(driver.session()).thenReturn(session);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void readsNumericIdsAndDeduplicatesJoinedNodesAndEdges() {
        Value paper = node(Map.of("paperId", 120L, "title", "论文"));
        Value firstAuthor = node(Map.of("authorId", 1L, "name", "作者甲"));
        Value secondAuthor = node(Map.of("authorId", 2L, "name", "作者乙"));
        Value cited = node(Map.of("paperId", 104L, "title", "被引论文"));
        Value venue = node(Map.of("venueId", 3L, "name", "测试期刊"));
        Result joined = result(List.of(
                record(Map.of("p", paper, "a", firstAuthor, "cited", cited, "v", venue)),
                record(Map.of("p", paper, "a", secondAuthor, "cited", cited, "v", venue)),
                record(Map.of("p", paper, "a", firstAuthor, "cited", cited, "v", venue))));
        when(session.run(anyString(), anyMap())).thenReturn(joined);

        var graph = controller.getPaperGraph(120L, 2).getBody();

        assertNotNull(graph);
        assertEquals(5, graph.getNodes().size());
        assertEquals(4, graph.getEdges().size());
        assertEquals(4, graph.getEdges().stream().map(edge -> edge.getData().getId()).distinct().count());
        assertTrue(graph.getNodes().stream().anyMatch(node -> node.getData().getId().equals("paper_120")));
        assertTrue(graph.getEdges().stream().anyMatch(edge -> edge.getData().getType().equals("PUBLISHED_AT")));
        assertTrue(graph.getEdges().stream().filter(edge -> edge.getData().getType().equals("WROTE"))
                .allMatch(edge -> edge.getData().getTarget().equals("paper_120")));
        verifyNoInteractions(authors);
        verify(session).close();
    }

    @Test
    void returnsAnIsolatedPaperWhenOptionalRelationshipsAreAbsent() {
        Result isolated = result(List.of(
                record(Map.of("p", node(Map.of("paperId", 9007199254740993L, "title", "独立论文"))))));
        when(session.run(anyString(), anyMap())).thenReturn(isolated);

        var graph = controller.getPaperGraph(9007199254740993L, 2).getBody();

        assertNotNull(graph);
        assertEquals("paper_9007199254740993", graph.getNodes().getFirst().getData().getId());
        assertTrue(graph.getEdges().isEmpty());
        verifyNoInteractions(authors);
    }

    @Test
    void returnsPaperNotFoundWithoutQueryingAnUnrelatedAuthor() throws Exception {
        doThrow(new PaperNotFoundException(120L)).when(papers).requirePaperExists(120L);

        mvc.perform(get("/api/v1/graph/paper/120"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAPER_NOT_FOUND"));

        verifyNoInteractions(driver, authors);
    }

    @Test
    void returnsUnavailableWhenAnExistingPaperHasNoProjection() throws Exception {
        Result empty = result(List.of());
        when(session.run(anyString(), anyMap())).thenReturn(empty);

        mvc.perform(get("/api/v1/graph/paper/120"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("GRAPH_UNAVAILABLE"));

        verifyNoInteractions(authors);
        verify(session).close();
    }

    @Test
    void reportsGraphOutageWithoutLeakingDriverDetailsOrChangingTheResourceType() throws Exception {
        when(session.run(anyString(), anyMap())).thenThrow(new ServiceUnavailableException("内部连接诊断信息"));

        mvc.perform(get("/api/v1/graph/paper/120"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("GRAPH_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("图谱服务暂不可用，请稍后重试"));

        verifyNoInteractions(authors);
        verify(session).close();
    }

    @Test
    void doesNotReplaceUnavailableStatisticsWithZeroCounts() throws Exception {
        when(session.run(anyString())).thenThrow(new ServiceUnavailableException("内部连接诊断信息"));

        mvc.perform(get("/api/v1/graph/statistics"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("GRAPH_UNAVAILABLE"))
                .andExpect(jsonPath("$.paperCount").doesNotExist());

        verify(session).close();
    }

    @Test
    void preservesRealEmptyStatisticsForAnAvailableDatabase() {
        when(session.run(anyString())).thenAnswer(call -> {
            String query = call.getArgument(0);
            return query.contains("RETURN count")
                    ? result(List.of(record(Map.of("count", Values.value(0L))))) : result(List.of());
        });

        var statistics = controller.getStatistics().getBody();

        assertNotNull(statistics);
        assertEquals(0L, statistics.get("paperCount"));
        assertEquals(0L, statistics.get("relationshipCount"));
        assertEquals(List.of(), statistics.get("yearDistribution"));
        assertEquals(List.of(), statistics.get("topCitedPapers"));
        verify(session).close();
    }

    private Value node(Map<String, Object> properties) {
        Node node = mock(Node.class);
        when(node.get(anyString())).thenAnswer(call -> Values.value(properties.get(call.getArgument(0))));
        Value value = mock(Value.class);
        when(value.asNode()).thenReturn(node);
        return value;
    }

    private Record record(Map<String, Value> values) {
        Record record = mock(Record.class);
        when(record.get(anyString())).thenAnswer(call -> values.getOrDefault(call.getArgument(0), Values.NULL));
        return record;
    }

    private Result result(List<Record> records) {
        Result result = mock(Result.class);
        var iterator = records.iterator();
        when(result.hasNext()).thenAnswer(call -> iterator.hasNext());
        when(result.next()).thenAnswer(call -> iterator.next());
        return result;
    }
}
