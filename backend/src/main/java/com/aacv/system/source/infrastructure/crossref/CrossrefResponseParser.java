package com.aacv.system.source.infrastructure.crossref;

import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceType;
import com.aacv.system.source.domain.SourceWork;
import com.aacv.system.source.domain.SourceWork.SourceAuthorship;
import com.aacv.system.source.domain.SourceWork.SourceDatePrecision;
import com.aacv.system.source.domain.SourceWork.SourceOrganization;
import com.aacv.system.source.domain.SourceWork.SourceTopic;
import com.aacv.system.source.domain.SourceWork.SourceVenue;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class CrossrefResponseParser {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    CrossrefResponseParser(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    ParsedPage parsePage(byte[] body, Map<String, String> transportMetadata) {
        try {
            JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode message = root.get("message");
            JsonNode items = message == null ? null : message.get("items");
            if (items == null || !items.isArray()) {
                throw parseFailure("Crossref分页响应缺少message.items数组", null);
            }
            List<RawSourceRecord> records = new ArrayList<>();
            for (JsonNode item : items) {
                String doi = requiredDoi(item.get("DOI"));
                records.add(new RawSourceRecord(
                        SourceType.CROSSREF,
                        doi,
                        sourceUri(doi),
                        objectMapper.writeValueAsString(item),
                        clock.instant()));
            }
            Map<String, String> metadata = new LinkedHashMap<>(transportMetadata);
            copyNumericMeta(message, metadata, "total-results");
            copyTextMeta(message, metadata, "query", "search-terms");
            OpaqueCursor nextCursor = null;
            JsonNode cursorNode = message.get("next-cursor");
            if (!records.isEmpty() && cursorNode != null && !cursorNode.isNull()) {
                if (!cursorNode.isTextual() || cursorNode.asString().isBlank()) {
                    throw parseFailure("Crossref返回了无效的next-cursor", null);
                }
                nextCursor = new OpaqueCursor(cursorNode.asString());
            }
            return new ParsedPage(records, nextCursor, metadata);
        } catch (JacksonException exception) {
            throw parseFailure("Crossref分页响应不是有效JSON", exception);
        }
    }

    SourceWork parseWork(RawSourceRecord rawRecord) {
        if (rawRecord.sourceType() != SourceType.CROSSREF) {
            throw new IllegalArgumentException("原始记录不属于Crossref来源");
        }
        try {
            JsonNode root = objectMapper.readTree(rawRecord.payload());
            String doi = requiredDoi(root.get("DOI"));
            if (!doi.equals(rawRecord.externalRecordId())) {
                throw parseFailure("原始记录DOI与Payload不一致", null);
            }
            List<String> warnings = new ArrayList<>();
            ParsedDate date = parseDate(firstPresent(root, "published", "issued"), warnings);
            return new SourceWork(
                    doi,
                    doi,
                    firstText(root.get("title")),
                    text(root, "type"),
                    text(root, "language"),
                    date.value(),
                    parseVenue(root, warnings),
                    parseAuthorships(root.get("author"), doi, warnings),
                    parseSubjects(root.get("subject"), warnings),
                    parseReferences(root.get("reference"), warnings),
                    null,
                    false,
                    warnings,
                    date.precision(),
                    parseIndexedAt(root.get("indexed"), warnings),
                    com.aacv.system.source.infrastructure.ScholarlyMetadataParser.crossref(root, rawRecord.fetchedAt(), warnings));
        } catch (JacksonException exception) {
            throw parseFailure("Crossref成果Payload不是有效JSON", exception);
        }
    }

    private SourceVenue parseVenue(JsonNode root, List<String> warnings) {
        String displayName = firstText(root.get("container-title"));
        List<String> issns = textList(root.get("ISSN"), "ISSN", warnings);
        String preferred = preferredIssn(root.get("issn-type"), issns);
        if (displayName == null && issns.isEmpty()) {
            return null;
        }
        return new SourceVenue(preferred, displayName, preferred, text(root, "type"), issns);
    }

    private String preferredIssn(JsonNode node, List<String> fallback) {
        if (node != null && node.isArray()) {
            for (JsonNode value : node) {
                if ("electronic".equalsIgnoreCase(text(value, "type"))) {
                    String issn = text(value, "value");
                    if (issn != null) {
                        return issn;
                    }
                }
            }
        }
        return fallback.isEmpty() ? null : fallback.getFirst();
    }

    private List<SourceAuthorship> parseAuthorships(
            JsonNode node, String doi, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("author不是数组，已忽略作者列表");
            return List.of();
        }
        List<SourceAuthorship> authorships = new ArrayList<>();
        int position = 0;
        for (JsonNode author : node) {
            position++;
            String orcid = text(author, "ORCID");
            String authorId = orcid == null ? doi + "#author:" + position : orcid;
            String displayName = joinName(text(author, "given"), text(author, "family"), text(author, "name"));
            authorships.add(new SourceAuthorship(
                    position,
                    authorId,
                    displayName,
                    orcid,
                    parseAffiliations(author.get("affiliation"), position, warnings)));
        }
        return authorships;
    }

    private List<SourceOrganization> parseAffiliations(
            JsonNode node, int authorPosition, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("第" + authorPosition + "位作者的affiliation不是数组");
            return List.of();
        }
        List<SourceOrganization> organizations = new ArrayList<>();
        for (JsonNode affiliation : node) {
            String name = text(affiliation, "name");
            String ror = parseRor(affiliation.get("id"));
            if (name != null || ror != null) {
                organizations.add(new SourceOrganization(null, name, null, null, ror));
            }
        }
        return organizations;
    }

    private String parseRor(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        for (JsonNode id : node) {
            if ("ROR".equalsIgnoreCase(text(id, "id-type"))) {
                return text(id, "id");
            }
        }
        return null;
    }

    private List<SourceTopic> parseSubjects(JsonNode node, List<String> warnings) {
        List<String> subjects = textList(node, "subject", warnings);
        return subjects.stream()
                .map(subject -> new SourceTopic(
                        "crossref-subject:" + sha256(subject.toLowerCase(Locale.ROOT)),
                        subject,
                        null,
                        null))
                .toList();
    }

    private List<String> parseReferences(JsonNode node, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add("reference不是数组，已忽略参考文献列表");
            return List.of();
        }
        List<String> references = new ArrayList<>();
        for (JsonNode reference : node) {
            String doi = optionalDoi(reference.get("DOI"));
            if (doi != null) {
                references.add(doi);
            }
        }
        return references.stream().distinct().toList();
    }

    private ParsedDate parseDate(JsonNode node, List<String> warnings) {
        JsonNode dateParts = node == null ? null : node.get("date-parts");
        JsonNode first = dateParts == null || !dateParts.isArray() || dateParts.isEmpty()
                ? null : dateParts.get(0);
        if (first == null || !first.isArray() || first.isEmpty() || first.size() > 3) {
            if (node != null) {
                warnings.add("published/issued日期结构无效，已保留为空");
            }
            return new ParsedDate(null, null);
        }
        try {
            int year = first.get(0).intValue();
            int month = first.size() >= 2 ? first.get(1).intValue() : 1;
            int day = first.size() >= 3 ? first.get(2).intValue() : 1;
            SourceDatePrecision precision = switch (first.size()) {
                case 1 -> SourceDatePrecision.YEAR;
                case 2 -> SourceDatePrecision.MONTH;
                case 3 -> SourceDatePrecision.DAY;
                default -> SourceDatePrecision.UNKNOWN;
            };
            return new ParsedDate(LocalDate.of(year, month, day), precision);
        } catch (RuntimeException exception) {
            warnings.add("published/issued日期值无效，已保留为空");
            return new ParsedDate(null, null);
        }
    }

    private Instant parseIndexedAt(JsonNode indexed, List<String> warnings) {
        String value = text(indexed, "date-time");
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            warnings.add("indexed.date-time格式无效，已保留为空");
            return null;
        }
    }

    private JsonNode firstPresent(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode value = root.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private List<String> textList(JsonNode node, String field, List<String> warnings) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            warnings.add(field + "不是数组，已忽略");
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (value.isTextual() && !value.asString().isBlank()) {
                values.add(value.asString());
            }
        }
        return values.stream().distinct().toList();
    }

    private String joinName(String given, String family, String fallback) {
        String joined = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
        return joined.isBlank() ? fallback : joined;
    }

    private String firstText(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty() || !node.get(0).isTextual()) {
            return null;
        }
        return node.get(0).asString();
    }

    private String text(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode value = parent.get(field);
        return value != null && value.isTextual() ? value.asString() : null;
    }

    private String requiredDoi(JsonNode node) {
        String doi = optionalDoi(node);
        if (doi == null) {
            throw parseFailure("Crossref记录缺少有效DOI", null);
        }
        return doi;
    }

    private String optionalDoi(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asString().trim().toLowerCase(Locale.ROOT);
        return value.matches("10\\.\\d{4,9}/\\S+") && value.length() <= 255 ? value : null;
    }

    private URI sourceUri(String doi) {
        String encoded = URLEncoder.encode(doi, StandardCharsets.UTF_8).replace("+", "%20");
        return DataSourceConfiguration.CROSSREF_BASE_URI.resolve("/works/" + encoded);
    }

    private void copyNumericMeta(JsonNode message, Map<String, String> target, String field) {
        JsonNode value = message.get(field);
        if (value != null && value.isIntegralNumber()) {
            target.put("message." + field, value.asString());
        }
    }

    private void copyTextMeta(
            JsonNode message, Map<String, String> target, String objectField, String field) {
        JsonNode object = message.get(objectField);
        String value = text(object, field);
        if (value != null && value.length() <= 128) {
            target.put("message." + objectField + "." + field, value);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private CrossrefClientException parseFailure(String safeMessage, Throwable cause) {
        return new CrossrefClientException("PARSE", false, null, safeMessage, cause);
    }

    private record ParsedDate(LocalDate value, SourceDatePrecision precision) {
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
