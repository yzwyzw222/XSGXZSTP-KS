package com.example.academic_entity_extract_kg_construction.api.controller;

import com.example.academic_entity_extract_kg_construction.api.dto.response.GraphDataDto;
import com.example.academic_entity_extract_kg_construction.application.service.AuthorService;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/graph")
public class GraphController {

    private final Driver neo4jDriver;
    private final AuthorService authorService;

    public GraphController(Driver neo4jDriver, AuthorService authorService) {
        this.neo4jDriver = neo4jDriver;
        this.authorService = authorService;
    }

    @GetMapping("/paper/{id}")
    public ResponseEntity<GraphDataDto> getPaperGraph(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "2") int depth) {
        List<GraphDataDto.GraphNode> nodes = new ArrayList<>();
        List<GraphDataDto.GraphEdge> edges = new ArrayList<>();
        Set<String> seenNodes = new HashSet<>();

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(
                    "MATCH (p:Paper {paperId: $paperId}) " +
                    "OPTIONAL MATCH (p)<-[:WROTE]-(a:Author) " +
                    "OPTIONAL MATCH (p)-[:CITES]->(cited:Paper) " +
                    "OPTIONAL MATCH (p)-[:PUBLISHED_AT]->(v:Venue) " +
                    "RETURN p, a, cited, v",
                    Map.of("paperId", id));

            while (result.hasNext()) {
                Record record = result.next();

                if (!record.get("p").isNull()) {
                    var paperNode = record.get("p").asNode();
                    String nodeId = "paper_" + paperNode.get("paperId").asString();
                    if (seenNodes.add(nodeId)) {
                        nodes.add(buildNode(nodeId, paperNode.get("title").asString("Unknown"), "PAPER",
                                Map.of()));
                    }
                }

                if (!record.get("a").isNull()) {
                    var authorNode = record.get("a").asNode();
                    String nodeId = "author_" + authorNode.get("authorId").asString();
                    if (seenNodes.add(nodeId)) {
                        nodes.add(buildNode(nodeId, authorNode.get("name").asString("Unknown"), "AUTHOR",
                                Map.of()));
                    }
                    edges.add(buildEdge("e_" + nodeId + "_" + id, nodeId, "paper_" + id, "WROTE"));
                }

                if (!record.get("cited").isNull()) {
                    var citedNode = record.get("cited").asNode();
                    String nodeId = "paper_" + citedNode.get("paperId").asString();
                    if (seenNodes.add(nodeId)) {
                        nodes.add(buildNode(nodeId, citedNode.get("title").asString("Unknown"), "PAPER",
                                Map.of()));
                    }
                    edges.add(buildEdge("e_cite_" + id + "_" + nodeId, "paper_" + id, nodeId, "CITES"));
                }
            }
        } catch (Exception e) {
            return ResponseEntity.ok(authorService.getAuthorGraph(id));
        }

        return ResponseEntity.ok(GraphDataDto.builder().nodes(nodes).edges(edges).build());
    }

    @GetMapping("/author/{id}")
    public ResponseEntity<GraphDataDto> getAuthorGraph(@PathVariable Long id) {
        return ResponseEntity.ok(authorService.getAuthorGraph(id));
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();

        try (Session session = neo4jDriver.session()) {
            Result paperCount = session.run("MATCH (p:Paper) RETURN count(p) as count");
            stats.put("paperCount", paperCount.hasNext() ? paperCount.next().get("count").asLong() : 0);

            Result authorCount = session.run("MATCH (a:Author) RETURN count(a) as count");
            stats.put("authorCount", authorCount.hasNext() ? authorCount.next().get("count").asLong() : 0);

            Result citesCount = session.run("MATCH ()-[r:CITES]->() RETURN count(r) as count");
            stats.put("citationCount", citesCount.hasNext() ? citesCount.next().get("count").asLong() : 0);

            Result entityCount = session.run("MATCH (e:ExtractedEntity) RETURN count(e) as count");
            stats.put("entityCount", entityCount.hasNext() ? entityCount.next().get("count").asLong() : 0);
        } catch (Exception e) {
            stats.put("neo4jAvailable", false);
            stats.put("paperCount", 0);
            stats.put("authorCount", 0);
            stats.put("citationCount", 0);
            stats.put("entityCount", 0);
        }

        return ResponseEntity.ok(stats);
    }

    private GraphDataDto.GraphNode buildNode(String id, String label, String type, Map<String, Object> properties) {
        return GraphDataDto.GraphNode.builder()
                .data(GraphDataDto.NodeData.builder()
                        .id(id)
                        .label(label)
                        .type(type)
                        .properties(properties)
                        .build())
                .build();
    }

    private GraphDataDto.GraphEdge buildEdge(String id, String source, String target, String type) {
        return GraphDataDto.GraphEdge.builder()
                .data(GraphDataDto.EdgeData.builder()
                        .id(id)
                        .source(source)
                        .target(target)
                        .label(type)
                        .type(type)
                        .build())
                .build();
    }
}
