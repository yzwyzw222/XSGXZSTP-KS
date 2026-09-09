package com.example.academic_entity_extract_kg_construction.infrastructure.external.semantic;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.semantic-scholar")
@Data
public class SemanticScholarProperties {
    private String baseUrl = "https://api.semanticscholar.org/graph/v1";
    private String apiKey = "";
    private int rateLimitPerSecond = 1;
}
