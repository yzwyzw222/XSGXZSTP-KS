package com.example.academic_entity_extract_kg_construction.application.service;

import com.example.academic_entity_extract_kg_construction.domain.model.*;
import com.example.academic_entity_extract_kg_construction.domain.model.enums.EntityType;
import com.example.academic_entity_extract_kg_construction.domain.model.enums.ExtractionStatus;
import com.example.academic_entity_extract_kg_construction.domain.model.enums.OutboxEventType;
import com.example.academic_entity_extract_kg_construction.domain.repository.*;
import com.example.academic_entity_extract_kg_construction.infrastructure.exception.ExtractionException;
import com.example.academic_entity_extract_kg_construction.infrastructure.external.llm.OpenAiCompatibleClient;
import com.example.academic_entity_extract_kg_construction.infrastructure.external.llm.prompt.EntityExtractionPromptBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExtractionPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionPipelineService.class);
    private static final int MAX_RETRIES = 3;

    private final PaperRepository paperRepository;
    private final ExtractedEntityRepository extractedEntityRepository;
    private final EntityRelationshipRepository entityRelationshipRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OpenAiCompatibleClient llmClient;
    private final EntityExtractionPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public ExtractionPipelineService(PaperRepository paperRepository,
                                      ExtractedEntityRepository extractedEntityRepository,
                                      EntityRelationshipRepository entityRelationshipRepository,
                                      OutboxEventRepository outboxEventRepository,
                                      OpenAiCompatibleClient llmClient,
                                      EntityExtractionPromptBuilder promptBuilder,
                                      ObjectMapper objectMapper) {
        this.paperRepository = paperRepository;
        this.extractedEntityRepository = extractedEntityRepository;
        this.entityRelationshipRepository = entityRelationshipRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void extractFromPaper(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ExtractionException("Paper not found: " + paperId));

        if (paper.getAbstractText() == null || paper.getAbstractText().isBlank()) {
            log.warn("Paper {} has no abstract, skipping extraction", paperId);
            paper.setExtractionStatus(ExtractionStatus.FAILED);
            paperRepository.save(paper);
            return;
        }

        paper.setExtractionStatus(ExtractionStatus.IN_PROGRESS);
        paperRepository.save(paper);

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(paper.getTitle(), paper.getAbstractText());

        String llmResponse = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                llmResponse = llmClient.chat(systemPrompt, userPrompt);
                break;
            } catch (Exception e) {
                log.warn("LLM call attempt {} failed for paper {}: {}", attempt, paperId, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    paper.setExtractionStatus(ExtractionStatus.FAILED);
                    paperRepository.save(paper);
                    throw new ExtractionException("LLM extraction failed after " + MAX_RETRIES + " attempts", e);
                }
            }
        }

        try {
            parseAndSave(paper, llmResponse);
            paper.setExtractionStatus(ExtractionStatus.COMPLETED);
            paperRepository.save(paper);
            log.info("Extraction completed for paper {}: {}", paperId, paper.getTitle());
        } catch (Exception e) {
            log.error("Failed to parse LLM response for paper {}: {}", paperId, e.getMessage());
            paper.setExtractionStatus(ExtractionStatus.FAILED);
            paperRepository.save(paper);
            throw new ExtractionException("Failed to parse LLM extraction result", e);
        }
    }

    @Transactional
    public int extractFromPendingPapers(int limit) {
        List<Paper> pendingPapers = paperRepository.findByExtractionStatus(ExtractionStatus.PENDING);
        int count = 0;
        for (Paper paper : pendingPapers) {
            if (count >= limit) break;
            try {
                extractFromPaper(paper.getId());
                count++;
            } catch (Exception e) {
                log.error("Extraction failed for paper {}: {}", paper.getId(), e.getMessage());
            }
        }
        return count;
    }

    private void parseAndSave(Paper paper, String llmResponse) {
        String cleaned = llmResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (JacksonException e) {
            throw new ExtractionException("Invalid JSON from LLM: " + e.getMessage());
        }

        java.util.Map<String, ExtractedEntity> entityMap = new java.util.HashMap<>();

        if (root.has("entities") && root.get("entities").isArray()) {
            for (JsonNode entityNode : root.get("entities")) {
                String name = entityNode.has("name") ? entityNode.get("name").asText() : null;
                String typeStr = entityNode.has("type") ? entityNode.get("type").asText() : "TOPIC";

                if (name == null || name.isBlank()) continue;

                EntityType entityType;
                try {
                    entityType = EntityType.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    entityType = EntityType.TOPIC;
                }

                ExtractedEntity entity = new ExtractedEntity();
                entity.setPaper(paper);
                entity.setEntityName(name);
                entity.setEntityType(entityType);
                entity.setProperties(entityNode.has("properties") ? entityNode.get("properties").toString() : "{}");

                entity = extractedEntityRepository.save(entity);
                entityMap.put(name, entity);

                writeOutboxEvent(OutboxEventType.ENTITY_CREATED, "ENTITY", entity.getId(),
                        "{\"id\":" + entity.getId() + ",\"name\":\"" + name + "\",\"type\":\"" + entityType + "\",\"paperId\":" + paper.getId() + "}");
            }
        }

        if (root.has("relationships") && root.get("relationships").isArray()) {
            for (JsonNode relNode : root.get("relationships")) {
                String sourceName = relNode.has("source") ? relNode.get("source").asText() : null;
                String targetName = relNode.has("target") ? relNode.get("target").asText() : null;
                String relType = relNode.has("type") ? relNode.get("type").asText() : "RELATES_TO";
                String evidence = relNode.has("evidence") ? relNode.get("evidence").asText() : null;

                if (sourceName == null || targetName == null) continue;

                ExtractedEntity sourceEntity = entityMap.get(sourceName);
                ExtractedEntity targetEntity = entityMap.get(targetName);

                if (sourceEntity == null || targetEntity == null) continue;

                EntityRelationship relationship = new EntityRelationship();
                relationship.setPaper(paper);
                relationship.setSourceEntity(sourceEntity);
                relationship.setTargetEntity(targetEntity);
                relationship.setRelationshipType(relType);
                relationship.setEvidenceText(evidence);
                relationship.setConfidence(1.0);

                relationship = entityRelationshipRepository.save(relationship);

                writeOutboxEvent(OutboxEventType.RELATIONSHIP_CREATED, "RELATIONSHIP", relationship.getId(),
                        "{\"id\":" + relationship.getId() + ",\"sourceId\":" + sourceEntity.getId() +
                                ",\"targetId\":" + targetEntity.getId() + ",\"relType\":\"" + relType + "\"}");
            }
        }
    }

    private void writeOutboxEvent(OutboxEventType eventType, String aggregateType, Long aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(eventType.name());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setPayload(payload);
        outboxEventRepository.save(event);
    }
}
