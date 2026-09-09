package com.example.academic_entity_extract_kg_construction.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PaperSummaryDto {
    private Long id;
    private String semanticScholarId;
    private String title;
    private Integer year;
    private Integer citationCount;
    private String venueName;
    private List<String> authorNames;
    private String extractionStatus;
}
