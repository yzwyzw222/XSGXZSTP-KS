package com.example.academic_entity_extract_kg_construction.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthorDetailDto {
    private Long id;
    private String semanticScholarId;
    private String name;
    private String affiliation;
    private Integer hIndex;
    private Integer paperCount;
    private Integer citationCount;
    private String url;
    private List<PaperSummaryDto> papers;
    private List<AuthorSummaryDto> coAuthors;
}
