package com.example.academic_entity_extract_kg_construction.infrastructure.sync;

import com.example.academic_entity_extract_kg_construction.domain.model.OutboxEvent;
import com.example.academic_entity_extract_kg_construction.domain.repository.OutboxEventRepository;
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
import java.util.List;
import java.util.Map;

@Component
public class OutboxPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollingScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final Driver neo4jDriver;
    private final ObjectMapper objectMapper;

    public OutboxPollingScheduler(OutboxEventRepository outboxEventRepository,
                                   Driver neo4jDriver, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.neo4jDriver = neo4jDriver;
        this.objectMapper = objectMapper;
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
        JsonNode payload = parsePayload(event.getPayload());
        Long paperId = payload.has("id") ? payload.get("id").asLong() : event.getAggregateId();
        String title = payload.has("title") ? payload.get("title").asText() : "";

        session.executeWrite(tx -> {
            tx.run("MERGE (p:Paper {paperId: $paperId}) " +
                   "SET p.title = $title, p.updatedAt = datetime()",
                    Map.of("paperId", paperId, "title", title));
            return null;
        });
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
