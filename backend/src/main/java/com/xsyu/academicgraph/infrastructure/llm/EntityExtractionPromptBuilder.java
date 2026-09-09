package com.xsyu.academicgraph.infrastructure.llm;

import org.springframework.stereotype.Component;

/**
 * 实体抽取提示词构造器（从 feature/Du 原样移植）。
 * 系统提示词是抽取质量的关键：明确六类实体、六种关系、只输出 JSON 的硬约束，
 * 并把"证据原文"要求写进规则——关系抽得对不对，靠 evidence 可以回到摘要里核对。
 */
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
