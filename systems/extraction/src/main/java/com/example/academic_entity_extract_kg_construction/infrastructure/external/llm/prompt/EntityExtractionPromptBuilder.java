package com.example.academic_entity_extract_kg_construction.infrastructure.external.llm.prompt;

import org.springframework.stereotype.Component;

@Component
public class EntityExtractionPromptBuilder {

    public String buildSystemPrompt() {
        return """
                You are an academic entity extraction system. Given a paper abstract, extract:
                1. Entities: Research topics, methods, datasets, tools, organizations mentioned
                2. Relationships between entities

                Return ONLY valid JSON in this exact format (no markdown, no explanation):
                {
                  "entities": [
                    {"name": "entity name", "type": "TOPIC|METHOD|DATASET|TOOL|ORGANIZATION|PERSON", "properties": {}}
                  ],
                  "relationships": [
                    {"source": "entity_name_1", "target": "entity_name_2", "type": "USES|EXTENDS|EVALUATES_ON|APPLIED_TO|PROPOSED_BY|COMPARED_WITH", "evidence": "relevant sentence fragment from the abstract"}
                  ]
                }

                Rules:
                - Entity names should be concise (2-5 words)
                - Use UPPERCASE for entity types
                - Only extract relationships that are explicitly stated or strongly implied
                - Include the evidence text (a short quote from the abstract) for each relationship
                - If no entities or relationships are found, return empty arrays
                - Return ONLY the JSON object, nothing else
                """;
    }

    public String buildUserPrompt(String title, String abstractText) {
        return String.format("""
                Paper Title: %s

                Abstract:
                %s

                Extract entities and relationships from this paper abstract.
                """, title, abstractText != null ? abstractText : "No abstract available.");
    }
}
