package com.example.academic_entity_extract_kg_construction.infrastructure.sync;

import com.example.academic_entity_extract_kg_construction.domain.model.OutboxEvent;
import com.example.academic_entity_extract_kg_construction.domain.model.Paper;
import com.example.academic_entity_extract_kg_construction.domain.repository.OutboxEventRepository;
import com.example.academic_entity_extract_kg_construction.domain.repository.PaperRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OutboxPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollingScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final Driver neo4jDriver;
    private final ObjectMapper objectMapper;
    private final PaperRepository paperRepository;

    public OutboxPollingScheduler(OutboxEventRepository outboxEventRepository,
                                   Driver neo4jDriver, ObjectMapper objectMapper, PaperRepository paperRepository) {
        this.outboxEventRepository = outboxEventRepository;
        this.neo4jDriver = neo4jDriver;
        this.objectMapper = objectMapper;
        this.paperRepository = paperRepository;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void pollAndSync() {
        List<OutboxEvent> events = outboxEventRepository.findUnprocessedEvents(PageRequest.of(0, 50));

        if (events.isEmpty()) {
            return;
        }

        log.debug("Processing {} outbox events", events.size());

        for (OutboxEvent event : events) {
            try {
                syncToNeo4j(event);
                event.setProcessed(true);
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to sync outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
    }

    private void syncToNeo4j(OutboxEvent event) {
        try (Session session = neo4jDriver.session()) {
            switch (event.getEventType()) {
                case "PAPER_CREATED", "PAPER_UPDATED" -> syncPaper(session, event);
                case "AUTHOR_CREATED", "AUTHOR_UPDATED" -> syncAuthor(session, event);
                case "ENTITY_CREATED" -> syncEntity(session, event);
                case "RELATIONSHIP_CREATED" -> syncRelationship(session, event);
                case "CITATION_CREATED" -> syncCitation(session, event);
                default -> log.warn("Unknown outbox event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Neo4j sync failed for event {}: {}", event.getId(), e.getMessage());
            throw e;
        }
    }

    private void syncPaper(Session session, OutboxEvent event) {
        // 读取当前持久化状态，兼容旧的仅含标题的事件，也避免旧事件覆盖较新的论文数据。
        Paper paper = paperRepository.findById(event.getAggregateId()).orElse(null);
        if (paper == null) {
            log.info("论文已不存在，跳过图投影：paperId={}", event.getAggregateId());
            return;
        }
        Long paperId = paper.getId();
        List<Map<String, Object>> authors = paper.getAuthors().stream()
                .map(author -> Map.<String, Object>of("id", author.getId(), "name", author.getName()))
                .toList();
        List<Map<String, Object>> references = paper.getReferences().stream()
                .map(this::paperProperties).toList();

        session.executeWrite(tx -> {
            tx.run("MERGE (p:Paper {paperId: $id}) " +
                   "SET p.title = $title, p.year = $year, p.citationCount = $citationCount, p.updatedAt = datetime()",
                    paperProperties(paper)).consume();

            // 只重建当前论文拥有的派生边；节点及其他论文指向它的引用保持不变。
            tx.run("MATCH (p:Paper {paperId: $paperId}) OPTIONAL MATCH (p)<-[r:WROTE]-(:Author) DELETE r",
                    Map.of("paperId", paperId)).consume();
            tx.run("MATCH (p:Paper {paperId: $paperId}) OPTIONAL MATCH (p)-[r:PUBLISHED_AT|CITES]->() DELETE r",
                    Map.of("paperId", paperId)).consume();
            tx.run("MATCH (p:Paper {paperId: $paperId}) UNWIND $authors AS author " +
                   "MERGE (a:Author {authorId: author.id}) SET a.name = author.name " +
                   "MERGE (a)-[:WROTE]->(p)", Map.of("paperId", paperId, "authors", authors)).consume();
            if (paper.getVenue() != null) {
                tx.run("MATCH (p:Paper {paperId: $paperId}) MERGE (v:Venue {venueId: $venueId}) " +
                       "SET v.name = $name, v.type = $type MERGE (p)-[:PUBLISHED_AT]->(v)",
                        Map.of("paperId", paperId, "venueId", paper.getVenue().getId(),
                                "name", paper.getVenue().getName(), "type", paper.getVenue().getType().name())).consume();
            }
            tx.run("MATCH (p:Paper {paperId: $paperId}) UNWIND $references AS reference " +
                   "MERGE (cited:Paper {paperId: reference.id}) " +
                   "SET cited.title = reference.title, cited.year = reference.year, cited.citationCount = reference.citationCount " +
                   "MERGE (p)-[:CITES]->(cited)", Map.of("paperId", paperId, "references", references)).consume();
            return null;
        });
    }

    private Map<String, Object> paperProperties(Paper paper) {
        // 年份或被引次数允许缺失；传入 null 可移除旧属性，不能伪造为零或使 Map.of 抛异常。
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", paper.getId());
        properties.put("title", paper.getTitle());
        properties.put("year", paper.getYear());
        properties.put("citationCount", paper.getCitationCount());
        return properties;
    }

    private void syncAuthor(Session session, OutboxEvent event) {
        JsonNode payload = parsePayload(event.getPayload());
        Long authorId = payload.has("id") ? payload.get("id").asLong() : event.getAggregateId();
        String name = payload.has("name") ? payload.get("name").asText() : "";

        session.executeWrite(tx -> {
            tx.run("MERGE (a:Author {authorId: $authorId}) " +
                   "SET a.name = $name, a.updatedAt = datetime()",
                    Map.of("authorId", authorId, "name", name));
            return null;
        });
    }

    private void syncEntity(Session session, OutboxEvent event) {
        JsonNode payload = parsePayload(event.getPayload());
        Long entityId = payload.has("id") ? payload.get("id").asLong() : event.getAggregateId();
        String entityName = payload.has("name") ? payload.get("name").asText() : "";
        String entityType = payload.has("type") ? payload.get("type").asText() : "UNKNOWN";
        Long paperId = payload.has("paperId") ? payload.get("paperId").asLong() : null;

        session.executeWrite(tx -> {
            tx.run("MERGE (e:ExtractedEntity {entityId: $entityId}) " +
                   "SET e.name = $name, e.entityType = $entityType",
                    Map.of("entityId", entityId, "name", entityName, "entityType", entityType));

            if (paperId != null) {
                tx.run("MATCH (e:ExtractedEntity {entityId: $entityId}), (p:Paper {paperId: $paperId}) " +
                       "MERGE (e)-[:EXTRACTED_FROM]->(p)",
                        Map.of("entityId", entityId, "paperId", paperId));
            }
            return null;
        });
    }

    private void syncRelationship(Session session, OutboxEvent event) {
        JsonNode payload = parsePayload(event.getPayload());
        Long sourceId = payload.has("sourceId") ? payload.get("sourceId").asLong() : null;
        Long targetId = payload.has("targetId") ? payload.get("targetId").asLong() : null;
        String relType = payload.has("relType") ? payload.get("relType").asText() : "RELATES_TO";

        if (sourceId != null && targetId != null) {
            session.executeWrite(tx -> {
                tx.run("MATCH (a:ExtractedEntity {entityId: $sourceId}), (b:ExtractedEntity {entityId: $targetId}) " +
                       "MERGE (a)-[r:" + relType + "]->(b)",
                        Map.of("sourceId", sourceId, "targetId", targetId));
                return null;
            });
        }
    }

    private void syncCitation(Session session, OutboxEvent event) {
        JsonNode payload = parsePayload(event.getPayload());
        Long citingId = payload.has("citingId") ? payload.get("citingId").asLong() : null;
        Long citedId = payload.has("citedId") ? payload.get("citedId").asLong() : null;

        if (citingId != null && citedId != null) {
            session.executeWrite(tx -> {
                tx.run("MATCH (a:Paper {paperId: $citingId}), (b:Paper {paperId: $citedId}) " +
                       "MERGE (a)-[:CITES]->(b)",
                        Map.of("citingId", citingId, "citedId", citedId));
                return null;
            });
        }
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("Failed to parse outbox payload: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }
}
