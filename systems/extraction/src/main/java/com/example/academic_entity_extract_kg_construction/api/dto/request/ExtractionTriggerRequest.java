package com.example.academic_entity_extract_kg_construction.api.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ExtractionTriggerRequest {
    private List<Long> paperIds;
    private String keyword;
}
