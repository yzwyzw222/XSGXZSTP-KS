package com.example.academic_entity_extract_kg_construction.api.controller;

import com.example.academic_entity_extract_kg_construction.api.dto.request.ExtractionTriggerRequest;
import com.example.academic_entity_extract_kg_construction.api.dto.response.ExtractionStatusDto;
import com.example.academic_entity_extract_kg_construction.application.service.ExtractionPipelineService;
import com.example.academic_entity_extract_kg_construction.domain.model.Paper;
import com.example.academic_entity_extract_kg_construction.domain.repository.ExtractedEntityRepository;
import com.example.academic_entity_extract_kg_construction.domain.repository.EntityRelationshipRepository;
import com.example.academic_entity_extract_kg_construction.domain.repository.PaperRepository;
import com.example.academic_entity_extract_kg_construction.infrastructure.exception.PaperNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/extraction")
public class ExtractionController {

    private final ExtractionPipelineService extractionPipelineService;
    private final PaperRepository paperRepository;
    private final ExtractedEntityRepository extractedEntityRepository;
    private final EntityRelationshipRepository entityRelationshipRepository;

    public ExtractionController(ExtractionPipelineService extractionPipelineService,
                                 PaperRepository paperRepository,
                                 ExtractedEntityRepository extractedEntityRepository,
                                 EntityRelationshipRepository entityRelationshipRepository) {
        this.extractionPipelineService = extractionPipelineService;
        this.paperRepository = paperRepository;
        this.extractedEntityRepository = extractedEntityRepository;
        this.entityRelationshipRepository = entityRelationshipRepository;
    }

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerExtraction(@RequestBody ExtractionTriggerRequest request) {
        int processed = 0;

        if (request.getPaperIds() != null && !request.getPaperIds().isEmpty()) {
            for (Long paperId : request.getPaperIds()) {
                try {
                    extractionPipelineService.extractFromPaper(paperId);
                    processed++;
                } catch (Exception e) {
                    // continue with next paper
                }
            }
        } else {
            processed = extractionPipelineService.extractFromPendingPapers(10);
        }

        return ResponseEntity.accepted().body(Map.of(
                "message", "Extraction triggered",
                "processedCount", processed
        ));
    }

    @GetMapping("/status/{paperId}")
    public ResponseEntity<ExtractionStatusDto> getExtractionStatus(@PathVariable Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new PaperNotFoundException(paperId));

        int entityCount = extractedEntityRepository.findByPaperId(paperId).size();
        int relCount = entityRelationshipRepository.findByPaperId(paperId).size();

        ExtractionStatusDto dto = ExtractionStatusDto.builder()
                .paperId(paperId)
                .paperTitle(paper.getTitle())
                .status(paper.getExtractionStatus().name())
                .extractedEntityCount(entityCount)
                .extractedRelationshipCount(relCount)
                .build();

        return ResponseEntity.ok(dto);
    }
}
