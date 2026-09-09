package com.example.academic_entity_extract_kg_construction.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExtractionStatusDto {
    private Long paperId;
    private String paperTitle;
    private String status;
    private int extractedEntityCount;
    private int extractedRelationshipCount;
}
