package com.xsyu.academicgraph.application.extraction;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsyu.academicgraph.domain.extraction.EntityRelationship;
import com.xsyu.academicgraph.domain.extraction.ExtractedEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 返回内容解析器（从 feature/Du 移植，tools.jackson 改回 com.fasterxml）。
 * 职责：把 LLM 的原始回复变成干净的 ParsedExtraction，所有防御性过滤都在这里完成：
 *  - 剥掉 ```json 围栏（模型偶尔不听话，提示词说了"只输出 JSON"也照样包上围栏）
 *  - 实体类型白名单校验：非法类型兜底成 TOPIC（与 Du 行为一致，宁收勿丢）
 *  - 关系类型白名单校验：非法类型直接丢弃（画不出来且无业务含义）
 *  - 空名称/缺端点等残缺条目跳过，不因一条脏数据毁掉整批抽取
 */
@Component
public class LlmResultParser {

    private final ObjectMapper objectMapper;

    public LlmResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 解析 LLM 回复。JSON 结构非法时抛 ExtractionException（由调用方标记 FAILED）。 */
    public ParsedExtraction parse(String llmResponse) {
        String cleaned = llmResponse == null ? "" : llmResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (JacksonException e) {
            throw new ExtractionException("LLM 返回的不是合法 JSON: " + e.getMessage(), e);
        }
        // 防御：Jackson 对空串/纯空白返回 MissingNode（不抛异常），"null"、数组、标量也能解析成功——
        // 但抽取结果必须是 JSON 对象，其余形态一律视为失败
        if (root == null || root.isMissingNode() || !root.isObject()) {
            String preview = cleaned.length() > 200 ? cleaned.substring(0, 200) + "..." : cleaned;
            throw new ExtractionException("LLM 返回的顶层不是 JSON 对象: " + preview);
        }

        List<ParsedEntity> entities = new ArrayList<>();
        if (root.has("entities") && root.get("entities").isArray()) {
            for (JsonNode node : root.get("entities")) {
                String name = text(node, "name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                String type = text(node, "type");
                type = type == null ? "" : type.trim().toUpperCase();
                // 兜底：非法类型统一归入 TOPIC，避免 LLM 生造类型把整行作废
                if (!ExtractedEntity.VALID_TYPES.contains(type)) {
                    type = ExtractedEntity.TYPE_TOPIC;
                }
                String properties = node.path("properties").toString();
                if (properties == null || properties.isBlank() || "null".equals(properties)) {
                    properties = "{}";
                }
                entities.add(new ParsedEntity(name.trim(), type, properties));
            }
        }

        List<ParsedRelationship> relationships = new ArrayList<>();
        if (root.has("relationships") && root.get("relationships").isArray()) {
            for (JsonNode node : root.get("relationships")) {
                String source = text(node, "source");
                String target = text(node, "target");
                if (source == null || target == null || source.isBlank() || target.isBlank()) {
                    continue;
                }
                String type = text(node, "type");
                type = type == null ? "" : type.trim().toUpperCase();
                // 非法关系类型直接丢弃（与实体兜底不同：关系没有"默认类型"可落）
                if (!EntityRelationship.VALID_TYPES.contains(type)) {
                    continue;
                }
                relationships.add(new ParsedRelationship(
                        source.trim(), target.trim(), type, text(node, "evidence")));
            }
        }

        return new ParsedExtraction(entities, relationships);
    }

    /** 取字段文本：字段缺失或为 null 时返回 null（比 asText 直接调更安全，防止 NPE） */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 解析后的实体条目：properties 保留 LLM 原文 JSON（写进台账的 JSON 列） */
    public record ParsedEntity(String name, String type, String properties) {
    }

    /** 解析后的关系条目：端点用实体名称引用（与 LLM 输出格式一致，落库时再换成台账 id） */
    public record ParsedRelationship(String source, String target, String type, String evidence) {
    }

    public record ParsedExtraction(List<ParsedEntity> entities, List<ParsedRelationship> relationships) {
    }
}
