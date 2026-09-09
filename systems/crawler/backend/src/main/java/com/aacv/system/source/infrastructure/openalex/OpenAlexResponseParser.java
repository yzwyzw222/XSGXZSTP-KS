package com.aacv.system.source.infrastructure.openalex;

import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceType;
import com.aacv.system.source.domain.SourceWork;
import com.aacv.system.source.domain.SourceWork.SourceAuthorship;
import com.aacv.system.source.domain.SourceWork.SourceOrganization;
import com.aacv.system.source.domain.SourceWork.SourceTopic;
import com.aacv.system.source.domain.SourceWork.SourceVenue;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class OpenAlexResponseParser {

    private static final int MAX_ABSTRACT_TOKENS = 5_000;
    private static final int MAX_ABSTRACT_POSITIONS_PER_TOKEN = 64;
    private static final int MAX_ABSTRACT_POSITION = 20_000;
    private static final int MAX_ABSTRACT_LENGTH = 50_000;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    OpenAlexResponseParser(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    ParsedPage parsePage(byte[] body, Map<String, String> transportMetadata) {
        try {
            JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) {
                throw parseFailure("OpenAlex分页响应缺少results数组", null);
            }
            List<RawSourceRecord> records = new ArrayList<>();
            for (JsonNode result : results) {
                String externalId = requiredOpenAlexId(result.get("id"), 'W');
                records.add(new RawSourceRecord(
                        SourceType.OPENALEX,
                        externalId,
                        URI.create(externalId),
                        objectMapper.writeValueAsString(result),
                        clock.instant()));
            }
            JsonNode meta = root.get("meta");
            OpaqueCursor nextCursor = null;
            Map<String, String> metadata = new LinkedHashMap<>(transportMetadata);
            if (meta != null && meta.isObject()) {
                JsonNode cursorNode = meta.get("next_cursor");
                if (cursorNode != null && !cursorNode.isNull()) {
                    if (!cursorNode.isTextual() || cursorNode.asString().isBlank()) {
                        throw parseFailure("OpenAlex返回了无效的next_cursor", null);
                    }
                    nextCursor = new OpaqueCursor(cursorNode.asString());
                }
                copyMeta(meta, metadata, "count");
                copyMeta(meta, metadata, "cost_usd");
                copyMeta(meta, metadata, "per_page");
            }
            return new ParsedPage(records, nextCursor, metadata);
        } catch (JacksonException exception) {
            throw parseFailure("OpenAlex分页响应不是有效JSON", exception);
        }
    }

    SourceWork parseWork(RawSourceRecord rawRecord) {
        if (rawRecord.sourceType() != SourceType.OPENALEX) {
            throw new IllegalArgumentException("原始记录不属于OpenAlex来源");
        }
        try {
            JsonNode root = objectMapper.readTree(rawRecord.payload());
            String externalId = requiredOpenAlexId(root.get("id"), 'W');
            if (!externalId.equals(rawRecord.externalRecordId())) {
                throw parseFailure("原始记录ID与Payload不一致", null);
            }
            List<String> warnings = new ArrayList<>();
            LocalDate publicationDate = parseDate(text(root, "publication_date"), warnings);
            SourceVenue venue = parseVenue(root.get("primary_location"), warnings);
            List<SourceAuthorship> authorships = parseAuthorships(root.get("authorships"), warnings);
            boolean authorshipsMayBeIncomplete = root.get("authorships") != null
                    && root.get("authorships").isArray()
                    && root.get("authorships").size() >= 100;
            if (authorshipsMayBeIncomplete) {
                warnings.add("authorships达到OpenAlex返回上限，作者列表可能不完整");
            }
            List<SourceTopic> topics = parseTopics(root.get("topics"), warnings);
            List<String> references = parseReferences(root.get("referenced_works"), warnings);
            String abstractText = reconstructAbstract(root.get("abstract_inverted_index"), warnings);
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
                    authorshipsMayBeIncomplete,
                    warnings,
                    publicationDate == null ? null : SourceWork.SourceDatePrecision.DAY,
                    null,
                    com.aacv.system.source.infrastructure.ScholarlyMetadataParser.openAlex(root, rawRecord.fetchedAt(), warnings));
        } catch (JacksonException exception) {
            throw parseFailure("OpenAlex成果Payload不是有效JSON", exception);
        }
    }

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
            warnings.add("primary_location.source.id无效，已忽略主要载体");
            return null;
        }
        return new SourceVenue(
                id,
                text(source, "display_name"),
                text(source, "issn_l"),
                text(source, "type"));
    }

    private List<SourceAuthorship> parseAuthorships(JsonNode node, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("authorships不是数组，已忽略作者列表");
            return List.of();
        }
        List<SourceAuthorship> authorships = new ArrayList<>();
        int position = 0;
        for (JsonNode authorship : node) {
            position++;
            JsonNode author = authorship.get("author");
            String authorId = author == null ? null : optionalOpenAlexId(author.get("id"), 'A');
            if (authorId == null) {
                warnings.add("第" + position + "项authorship缺少有效作者ID，已忽略");
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

    private List<SourceOrganization> parseOrganizations(
            JsonNode node, int authorshipPosition, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("第" + authorshipPosition + "项authorship的institutions不是数组");
            return List.of();
        }
        List<SourceOrganization> organizations = new ArrayList<>();
        for (JsonNode institution : node) {
            String id = optionalOpenAlexId(institution.get("id"), 'I');
            if (id == null) {
                warnings.add("authorship机构缺少有效OpenAlex ID，已忽略");
                continue;
            }
            organizations.add(new SourceOrganization(
                    id,
                    text(institution, "display_name"),
                    text(institution, "country_code"),
                    text(institution, "type"),
                    text(institution, "ror")));
        }
        return organizations;
    }

    private List<SourceTopic> parseTopics(JsonNode node, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("topics不是数组，已忽略主题列表");
            return List.of();
        }
        List<SourceTopic> topics = new ArrayList<>();
        for (JsonNode topic : node) {
            String id = optionalOpenAlexId(topic.get("id"), 'T');
            if (id == null) {
                warnings.add("topic缺少有效OpenAlex ID，已忽略");
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

    private List<String> parseReferences(JsonNode node, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("referenced_works不是数组，已忽略参考文献列表");
            return List.of();
        }
        List<String> references = new ArrayList<>();
        for (JsonNode reference : node) {
            String id = optionalOpenAlexId(reference, 'W');
            if (id == null) {
                warnings.add("referenced_works包含无效OpenAlex ID，已忽略该项");
            } else {
                references.add(id);
            }
        }
        return references;
    }

    private String reconstructAbstract(JsonNode node, List<String> warnings) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject() || node.size() > MAX_ABSTRACT_TOKENS) {
            warnings.add("abstract_inverted_index结构或词数超出限制，未重建摘要");
            return null;
        }
        TreeMap<Integer, String> wordsByPosition = new TreeMap<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String word = entry.getKey();
            JsonNode positions = entry.getValue();
            if (word.isBlank() || word.length() > 256 || !positions.isArray()
                    || positions.size() > MAX_ABSTRACT_POSITIONS_PER_TOKEN) {
                warnings.add("abstract_inverted_index包含畸形词项，未重建摘要");
                return null;
            }
            for (JsonNode positionNode : positions) {
                if (!positionNode.isIntegralNumber()) {
                    warnings.add("abstract_inverted_index包含非整数位置，未重建摘要");
                    return null;
                }
                int position = positionNode.intValue();
                if (position < 0 || position > MAX_ABSTRACT_POSITION
                        || wordsByPosition.putIfAbsent(position, word) != null) {
                    warnings.add("abstract_inverted_index包含越界或冲突位置，未重建摘要");
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
                warnings.add("abstract_inverted_index位置不连续，未猜测缺失文本");
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

    private LocalDate parseDate(String value, List<String> warnings) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            warnings.add("publication_date格式无效，已保留为空");
            return null;
        }
    }

    private String requiredOpenAlexId(JsonNode node, char entityPrefix) {
        String id = optionalOpenAlexId(node, entityPrefix);
        if (id == null) {
            throw parseFailure("OpenAlex记录缺少有效" + entityPrefix + "类ID", null);
        }
        return id;
    }

    private String optionalOpenAlexId(JsonNode node, char entityPrefix) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asString();
        return value.matches("https://openalex\\.org/" + entityPrefix + "\\d+") ? value : null;
    }

    private String text(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode value = parent.get(field);
        return value != null && value.isTextual() ? value.asString() : null;
    }

    private String nestedText(JsonNode parent, String objectField, String valueField) {
        JsonNode nested = parent.get(objectField);
        return nested == null ? null : text(nested, valueField);
    }

    private void copyMeta(JsonNode meta, Map<String, String> target, String field) {
        JsonNode value = meta.get(field);
        if (value != null && value.isValueNode()) {
            String text = value.asString();
            if (text.length() <= 128) {
                target.put("meta." + field, text);
            }
        }
    }

    private OpenAlexClientException parseFailure(String safeMessage, Throwable cause) {
        return new OpenAlexClientException("PARSE", false, null, safeMessage, cause);
    }

    record ParsedPage(
            List<RawSourceRecord> records,
            OpaqueCursor nextCursor,
            Map<String, String> responseMetadata) {

        ParsedPage {
            records = List.copyOf(records);
            responseMetadata = Map.copyOf(responseMetadata);
        }
    }
}
