package com.example.academic_entity_extract_kg_construction.api.controller;

import com.example.academic_entity_extract_kg_construction.api.dto.response.GraphDataDto;
import com.example.academic_entity_extract_kg_construction.application.service.AuthorService;
import com.example.academic_entity_extract_kg_construction.application.service.PaperService;
import com.example.academic_entity_extract_kg_construction.infrastructure.exception.GraphUnavailableException;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/graph")
public class GraphController {

    private final Driver neo4jDriver;
    private final AuthorService authorService;
    private final PaperService paperService;

    public GraphController(Driver neo4jDriver, AuthorService authorService, PaperService paperService) {
        this.neo4jDriver = neo4jDriver;
        this.authorService = authorService;
        this.paperService = paperService;
    }

    @GetMapping("/paper/{id}")
    public ResponseEntity<GraphDataDto> getPaperGraph(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "2") int depth) {
        paperService.requirePaperExists(id);
        List<GraphDataDto.GraphNode> nodes = new ArrayList<>();
        Map<String, GraphDataDto.GraphEdge> edges = new LinkedHashMap<>();
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
                    String nodeId = "paper_" + paperNode.get("paperId").asLong();
                    if (seenNodes.add(nodeId)) {
                        nodes.add(buildNode(nodeId, paperNode.get("title").asString("Unknown"), "PAPER",
                                Map.of()));
                    }
                }

                if (!record.get("a").isNull()) {
                    var authorNode = record.get("a").asNode();
                    String nodeId = "author_" + authorNode.get("authorId").asLong();
                    if (seenNodes.add(nodeId)) {
                        nodes.add(buildNode(nodeId, authorNode.get("name").asString("Unknown"), "AUTHOR",
                                Map.of()));
                    }
                    String edgeId = "e_" + nodeId + "_" + id;
                    edges.putIfAbsent(edgeId, buildEdge(edgeId, nodeId, "paper_" + id, "WROTE"));
                }

                if (!record.get("cited").isNull()) {
                    var citedNode = record.get("cited").asNode();
                    String nodeId = "paper_" + citedNode.get("paperId").asLong();
                    if (seenNodes.add(nodeId)) {
                        nodes.add(buildNode(nodeId, citedNode.get("title").asString("Unknown"), "PAPER",
                                Map.of()));
                    }
                    String edgeId = "e_cite_" + id + "_" + nodeId;
                    edges.putIfAbsent(edgeId, buildEdge(edgeId, "paper_" + id, nodeId, "CITES"));
                }

                if (!record.get("v").isNull()) {
                    var venueNode = record.get("v").asNode();
                    String nodeId = "venue_" + venueNode.get("venueId").asLong();
                    if (seenNodes.add(nodeId)) {
                        nodes.add(buildNode(nodeId, venueNode.get("name").asString("Unknown"), "VENUE", Map.of()));
                    }
                    String edgeId = "e_venue_" + id + "_" + nodeId;
                    edges.putIfAbsent(edgeId, buildEdge(edgeId, "paper_" + id, nodeId, "PUBLISHED_AT"));
                }
            }
        } catch (Neo4jException e) {
            throw new GraphUnavailableException("图谱服务暂不可用，请稍后重试", e);
        }

        // MySQL 中存在论文而投影尚未生成时，不能误报论文不存在或返回同编号作者。
        if (nodes.isEmpty()) {
            throw new GraphUnavailableException("论文图谱尚未同步，请稍后重试");
        }
        return ResponseEntity.ok(GraphDataDto.builder().nodes(nodes).edges(new ArrayList<>(edges.values())).build());
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

            Result relationshipCount = session.run("MATCH ()-[r]->() RETURN count(r) as count");
            stats.put("relationshipCount", relationshipCount.hasNext() ? relationshipCount.next().get("count").asLong() : 0);

            Result entityCount = session.run("MATCH (e:ExtractedEntity) RETURN count(e) as count");
            stats.put("entityCount", entityCount.hasNext() ? entityCount.next().get("count").asLong() : 0);

            Result yearDist = session.run(
                    "MATCH (p:Paper) WHERE p.year IS NOT NULL RETURN p.year AS year, count(p) AS count ORDER BY year");
            List<Map<String, Object>> yearDistribution = new ArrayList<>();
            while (yearDist.hasNext()) {
                Record r = yearDist.next();
                yearDistribution.add(Map.of("year", r.get("year").asInt(), "count", r.get("count").asLong()));
            }
            stats.put("yearDistribution", yearDistribution);

            Result topCited = session.run(
                    "MATCH (p:Paper) WHERE p.citationCount IS NOT NULL RETURN p.title AS title, p.citationCount AS citationCount ORDER BY citationCount DESC LIMIT 10");
            List<Map<String, Object>> topCitedPapers = new ArrayList<>();
            while (topCited.hasNext()) {
                Record r = topCited.next();
                topCitedPapers.add(Map.of("title", r.get("title").asString(""), "citationCount", r.get("citationCount").asLong()));
            }
            stats.put("topCitedPapers", topCitedPapers);
        } catch (Neo4jException e) {
            throw new GraphUnavailableException("图谱统计暂不可用，请稍后重试", e);
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
