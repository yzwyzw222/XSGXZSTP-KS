package com.xsyu.academicgraph.infrastructure.openalex;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsyu.academicgraph.application.crawl.SourceWork;
import com.xsyu.academicgraph.application.crawl.SourceWork.SourceAuthorship;
import com.xsyu.academicgraph.application.crawl.SourceWork.SourceOrganization;
import com.xsyu.academicgraph.application.crawl.SourceWork.SourceTopic;
import com.xsyu.academicgraph.application.crawl.SourceWork.SourceVenue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * OpenAlex JSON 响应解析器（从 feature/Luo 移植）。
 * 原版是"先解析成原始记录、再逐条解析成 SourceWork"的两段式（为支持落盘重放）；
 * 本平台同步直读，简化成一段式：parseWorks(字节体) → List&lt;SourceWork&gt;。
 *
 * 保留原版的全部防御性解析：
 *  - optionalOpenAlexId 正则校验（id 必须是 https://openalex.org/X数字 格式）
 *  - reconstructAbstract 完整移植：abstract_inverted_index 是"词 → 出现位置数组"的倒排索引，
 *    按位置重建成原文，词数/位置数/位置值/连续性与总长度都有上限，任何一处畸形都放弃摘要
 *    （宁可不要摘要，也不返回乱序文本）
 *  - 日期、数组结构等宽松解析：字段缺失或类型不对时置空并记 warning，不整页失败
 *
 * 注意：Jackson 版本差异——feature/Luo 用 Spring Boot 4 的 tools.jackson，
 * 这里改回 Boot 3.5 自带的 com.fasterxml。
 */
@Component
public class OpenAlexResponseParser {

    private static final Logger log = LoggerFactory.getLogger(OpenAlexResponseParser.class);

    // ---- reconstructAbstract 的防御上限（照 Luo 原版逐字保留） ----
    // 包级可见：同包测试用它构造超限 fixture
    static final int MAX_ABSTRACT_TOKENS = 5_000;
    private static final int MAX_ABSTRACT_POSITIONS_PER_TOKEN = 64;
    private static final int MAX_ABSTRACT_POSITION = 20_000;
    private static final int MAX_ABSTRACT_LENGTH = 50_000;

    private final ObjectMapper objectMapper;

    public OpenAlexResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析一页搜索响应（/works?search=...）为 SourceWork 列表。
     * 整页的 JSON 结构错误（缺 results 数组）直接抛异常；
     * 单条成果畸形（缺 id 等）只跳过该条并记日志，不让一粒老鼠屎坏了一锅粥。
     */
    public List<SourceWork> parseWorks(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
        } catch (JacksonException exception) {
            throw parseFailure("OpenAlex 响应不是有效 JSON", exception);
        }
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            throw parseFailure("OpenAlex 响应缺少 results 数组", null);
        }
        List<SourceWork> works = new ArrayList<>();
        for (JsonNode result : results) {
            try {
                works.add(parseWork(result));
            } catch (OpenAlexClientException exception) {
                log.warn("跳过一条无法解析的 OpenAlex 成果：{}", exception.getSafeMessage());
            }
        }
        return works;
    }

    /** 解析单条成果（/works/results[i]） */
    private SourceWork parseWork(JsonNode root) {
        String externalId = requiredOpenAlexId(root.get("id"), 'W');
        List<String> warnings = new ArrayList<>();
        LocalDate publicationDate = parseDate(text(root, "publication_date"), warnings);
        SourceVenue venue = parseVenue(root.get("primary_location"), warnings);
        List<SourceAuthorship> authorships = parseAuthorships(root.get("authorships"), warnings);
        List<SourceTopic> topics = parseTopics(root.get("topics"), warnings);
        List<String> references = parseReferences(root.get("referenced_works"), warnings);
        String abstractText = reconstructAbstract(root.get("abstract_inverted_index"), warnings);
        Integer citedByCount = intValue(root, "cited_by_count", warnings);
        if (!warnings.isEmpty()) {
            log.debug("解析 {} 时的字段告警: {}", externalId, warnings);
        }
        return new SourceWork(
                externalId,
                text(root, "doi"),
                text(root, "title"),
                text(root, "type"),
                text(root, "language"),
                publicationDate,
                venue,
                authorships,
                topics,
                references,
                abstractText,
                citedByCount);
    }

    /** 从 primary_location.source 解析发表渠道；没有有效 source 时返回 null 并告警 */
    private SourceVenue parseVenue(JsonNode location, List<String> warnings) {
        if (location == null || location.isNull()) {
            return null;
        }
        JsonNode source = location.get("source");
        if (source == null || source.isNull()) {
            return null;
        }
        String id = optionalOpenAlexId(source.get("id"), 'S');
        if (id == null) {
            warnings.add("primary_location.source.id 无效，已忽略发表渠道");
            return null;
        }
        return new SourceVenue(
                id,
                text(source, "display_name"),
                text(source, "issn_l"),
                text(source, "type"));
    }

    /** 解析作者列表：位次按数组顺序 1 起编；缺作者 id 的项跳过并告警 */
    private List<SourceAuthorship> parseAuthorships(JsonNode node, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("authorships 不是数组，已忽略作者列表");
            return List.of();
        }
        List<SourceAuthorship> authorships = new ArrayList<>();
        int position = 0;
        for (JsonNode authorship : node) {
            position++;
            JsonNode author = authorship.get("author");
            String authorId = author == null ? null : optionalOpenAlexId(author.get("id"), 'A');
            if (authorId == null) {
                warnings.add("第" + position + "项 authorship 缺少有效作者 id，已忽略");
                continue;
            }
            List<SourceOrganization> organizations = parseOrganizations(
                    authorship.get("institutions"), position, warnings);
            authorships.add(new SourceAuthorship(
                    position,
                    authorId,
                    text(author, "display_name"),
                    text(author, "orcid"),
                    organizations));
        }
        return authorships;
    }

    /** 解析某位作者的署名机构列表；缺机构 id 的项跳过并告警 */
    private List<SourceOrganization> parseOrganizations(
            JsonNode node, int authorshipPosition, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("第" + authorshipPosition + "项 authorship 的 institutions 不是数组");
            return List.of();
        }
        List<SourceOrganization> organizations = new ArrayList<>();
        for (JsonNode institution : node) {
            String id = optionalOpenAlexId(institution.get("id"), 'I');
            if (id == null) {
                warnings.add("署名机构缺少有效 OpenAlex id，已忽略");
                continue;
            }
            organizations.add(new SourceOrganization(
                    id,
                    text(institution, "display_name"),
                    text(institution, "country_code"),
                    text(institution, "type")));
        }
        return organizations;
    }

    /** 解析主题列表（topics → 平台关键词）：display_name 作词名，field.display_name 作研究领域 */
    private List<SourceTopic> parseTopics(JsonNode node, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("topics 不是数组，已忽略主题列表");
            return List.of();
        }
        List<SourceTopic> topics = new ArrayList<>();
        for (JsonNode topic : node) {
            String id = optionalOpenAlexId(topic.get("id"), 'T');
            if (id == null) {
                warnings.add("topic 缺少有效 OpenAlex id，已忽略");
                continue;
            }
            topics.add(new SourceTopic(
                    id,
                    text(topic, "display_name"),
                    nestedText(topic, "subfield", "display_name"),
                    nestedText(topic, "field", "display_name")));
        }
        return topics;
    }

    /** 解析参考文献列表：只保留格式合法的 OpenAlex id（这些 URL 之后存进外部引用线索列） */
    private List<String> parseReferences(JsonNode node, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("referenced_works 不是数组，已忽略参考文献列表");
            return List.of();
        }
        List<String> references = new ArrayList<>();
        for (JsonNode reference : node) {
            String id = optionalOpenAlexId(reference, 'W');
            if (id == null) {
                warnings.add("referenced_works 包含无效 OpenAlex id，已忽略该项");
            } else {
                references.add(id);
            }
        }
        return references;
    }

    /**
     * 从 abstract_inverted_index（倒排索引）重建摘要原文。
     * 数据结构形如 {"Despite":[0],"growing":[1],...}：词 → 它在摘要中出现的位置数组。
     * 用 TreeMap 按位置排序拼回原文。防御策略与 Luo 原版一致：
     * 词数/单词位置数/位置值/位置连续性/总长度任一项超限或畸形 → 放弃摘要返回 null。
     */
    private String reconstructAbstract(JsonNode node, List<String> warnings) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject() || node.size() > MAX_ABSTRACT_TOKENS) {
            warnings.add("abstract_inverted_index 结构或词数超出限制，未重建摘要");
            return null;
        }
        TreeMap<Integer, String> wordsByPosition = new TreeMap<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String word = entry.getKey();
            JsonNode positions = entry.getValue();
            if (word.isBlank() || word.length() > 256 || !positions.isArray()
                    || positions.size() > MAX_ABSTRACT_POSITIONS_PER_TOKEN) {
                warnings.add("abstract_inverted_index 包含畸形词项，未重建摘要");
                return null;
            }
            for (JsonNode positionNode : positions) {
                if (!positionNode.isIntegralNumber()) {
                    warnings.add("abstract_inverted_index 包含非整数位置，未重建摘要");
                    return null;
                }
                int position = positionNode.intValue();
                if (position < 0 || position > MAX_ABSTRACT_POSITION
                        || wordsByPosition.putIfAbsent(position, word) != null) {
                    warnings.add("abstract_inverted_index 包含越界或冲突位置，未重建摘要");
                    return null;
                }
            }
        }
        if (wordsByPosition.isEmpty()) {
            return null;
        }
        int expected = 0;
        StringBuilder abstractText = new StringBuilder();
        for (Map.Entry<Integer, String> entry : wordsByPosition.entrySet()) {
            if (entry.getKey() != expected++) {
                warnings.add("abstract_inverted_index 位置不连续，未重建摘要");
                return null;
            }
            if (!abstractText.isEmpty()) {
                abstractText.append(' ');
            }
            abstractText.append(entry.getValue());
            if (abstractText.length() > MAX_ABSTRACT_LENGTH) {
                warnings.add("重建摘要超过长度上限，未保留摘要");
                return null;
            }
        }
        return abstractText.toString();
    }

    /** 宽松解析 ISO 日期：格式不对置空并告警（不因一条坏日期丢弃整篇论文） */
    private LocalDate parseDate(String value, List<String> warnings) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            warnings.add("publication_date 格式无效，已保留为空");
            return null;
        }
    }

    /** 必填 id 校验：格式不合法直接抛解析异常（没有 id 的成果无法进库） */
    private String requiredOpenAlexId(JsonNode node, char entityPrefix) {
        String id = optionalOpenAlexId(node, entityPrefix);
        if (id == null) {
            throw parseFailure("OpenAlex 记录缺少有效 " + entityPrefix + " 类 id", null);
        }
        return id;
    }

    /** 可选 id 校验：只接受 https://openalex.org/前缀 + 实体字母 + 纯数字 的格式 */
    private String optionalOpenAlexId(JsonNode node, char entityPrefix) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value.matches("https://openalex\\.org/" + entityPrefix + "\\d+") ? value : null;
    }

    /** 取字符串字段：父节点为空或字段不是文本时返回 null */
    private String text(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode value = parent.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    /** 取嵌套对象里的字符串字段（如 topics[].field.display_name） */
    private String nestedText(JsonNode parent, String objectField, String valueField) {
        JsonNode nested = parent.get(objectField);
        return nested == null ? null : text(nested, valueField);
    }

    /** 取整数字段：缺失或不是整数时返回 null（citationCount 允许为空，入库时按 0 处理） */
    private Integer intValue(JsonNode parent, String field, List<String> warnings) {
        if (parent == null) {
            return null;
        }
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            warnings.add(field + " 不是整数，已保留为空");
            return null;
        }
        return value.intValue();
    }

    /** 统一构造解析类异常（不可重试：重试也还是同样的坏数据） */
    private OpenAlexClientException parseFailure(String safeMessage, Throwable cause) {
        return new OpenAlexClientException("PARSE", false, null, safeMessage, cause);
    }
}
