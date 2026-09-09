package com.example.academic_entity_extract_kg_construction.api.mapper;

import com.example.academic_entity_extract_kg_construction.api.dto.response.AuthorSummaryDto;
import com.example.academic_entity_extract_kg_construction.domain.model.Author;

public class AuthorMapper {

    private AuthorMapper() {}

    public static AuthorSummaryDto toSummary(Author author) {
        return AuthorSummaryDto.builder()
                .id(author.getId())
                .semanticScholarId(author.getSemanticScholarId())
                .name(author.getName())
                .affiliation(author.getAffiliation())
                .hIndex(author.getHIndex())
                .paperCount(author.getPaperCount())
                .citationCount(author.getCitationCount())
                .build();
    }
}
