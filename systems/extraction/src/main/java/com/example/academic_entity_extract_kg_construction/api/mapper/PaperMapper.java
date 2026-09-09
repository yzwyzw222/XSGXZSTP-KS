package com.example.academic_entity_extract_kg_construction.api.mapper;

import com.example.academic_entity_extract_kg_construction.api.dto.response.AuthorSummaryDto;
import com.example.academic_entity_extract_kg_construction.api.dto.response.PaperDetailDto;
import com.example.academic_entity_extract_kg_construction.api.dto.response.PaperSummaryDto;
import com.example.academic_entity_extract_kg_construction.domain.model.Paper;

import java.util.List;

public class PaperMapper {

    private PaperMapper() {}

    public static PaperSummaryDto toSummary(Paper paper) {
        List<String> authorNames = paper.getAuthors() != null
                ? paper.getAuthors().stream().map(a -> a.getName()).toList()
                : List.of();

        return PaperSummaryDto.builder()
                .id(paper.getId())
                .semanticScholarId(paper.getSemanticScholarId())
                .title(paper.getTitle())
                .year(paper.getYear())
                .citationCount(paper.getCitationCount())
                .venueName(paper.getVenue() != null ? paper.getVenue().getName() : null)
                .authorNames(authorNames)
                .extractionStatus(paper.getExtractionStatus().name())
                .build();
    }

    public static PaperDetailDto toDetail(Paper paper) {
        List<AuthorSummaryDto> authors = paper.getAuthors() != null
                ? paper.getAuthors().stream().map(AuthorMapper::toSummary).toList()
                : List.of();

        return PaperDetailDto.builder()
                .id(paper.getId())
                .semanticScholarId(paper.getSemanticScholarId())
                .title(paper.getTitle())
                .abstractText(paper.getAbstractText())
                .year(paper.getYear())
                .doi(paper.getDoi())
                .citationCount(paper.getCitationCount())
                .referenceCount(paper.getReferenceCount())
                .venueName(paper.getVenue() != null ? paper.getVenue().getName() : null)
                .venueType(paper.getVenue() != null ? paper.getVenue().getType().name() : null)
                .url(paper.getUrl())
                .extractionStatus(paper.getExtractionStatus().name())
                .authors(authors)
                .topics(List.of())
                .build();
    }
}
