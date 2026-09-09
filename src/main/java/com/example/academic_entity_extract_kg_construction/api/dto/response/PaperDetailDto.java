package com.example.academic_entity_extract_kg_construction.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PaperDetailDto {
    private Long id;
    private String semanticScholarId;
    private String title;
    private String abstractText;
    private Integer year;
    private String doi;
    private Integer citationCount;
    private Integer referenceCount;
    private String venueName;
    private String venueType;
    private String url;
    private String extractionStatus;
    private List<AuthorSummaryDto> authors;
    private List<String> topics;
}
