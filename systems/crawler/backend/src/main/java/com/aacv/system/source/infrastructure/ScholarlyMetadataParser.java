package com.aacv.system.source.infrastructure;

import com.aacv.system.source.domain.ScholarlyMetadata;
import com.aacv.system.source.domain.ScholarlyMetadata.VersionRelation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class ScholarlyMetadataParser {

    private ScholarlyMetadataParser() {
    }

    public static ScholarlyMetadata openAlex(JsonNode root, Instant observedAt, List<String> warnings) {
        JsonNode access = root.path("open_access");
        Boolean openAccess = optionalBoolean(access.get("is_oa"), "open_access.is_oa", warnings);
        String status = null;
        JsonNode rawStatus = access.get("oa_status");
        if (rawStatus != null && !rawStatus.isNull()) {
            if (rawStatus.isTextual() && Set.of("diamond", "gold", "green", "hybrid", "bronze", "closed")
                    .contains(rawStatus.asString())) {
                status = rawStatus.asString();
            } else warnings.add("open_access.oa_status未知，未推测开放状态");
        }
        return new ScholarlyMetadata(observedAt,
                optionalCount(root.get("cited_by_count"), "cited_by_count", warnings),
                optionalBoolean(root.get("is_retracted"), "is_retracted", warnings),
                openAccess, status, List.of());
    }

    public static ScholarlyMetadata crossref(JsonNode root, Instant observedAt, List<String> warnings) {
        List<VersionRelation> relations = new ArrayList<>();
        for (String type : List.of("is-preprint-of", "has-preprint", "is-version-of", "has-version")) {
            JsonNode values = root.path("relation").get(type);
            if (values == null || values.isNull()) continue;
            if (!values.isArray()) {
                warnings.add("版本关系不是数组，已忽略");
                continue;
            }
            for (JsonNode value : values) {
                if (relations.size() >= 50) {
                    warnings.add("版本关系超过50条，后续关系未保存");
                    break;
                }
                JsonNode id = value.get("id");
                if (!"doi".equals(value.path("id-type").asString()) || id == null || !id.isTextual()) continue;
                String doi = id.asString().trim().toLowerCase(Locale.ROOT);
                if (doi.startsWith("https://doi.org/")) doi = doi.substring(16);
                if (doi.length() > 255 || !doi.matches("10\\.\\d{4,9}/\\S+")) {
                    warnings.add("版本关系DOI无效，已忽略");
                    continue;
                }
                VersionRelation relation = new VersionRelation(type, doi);
                if (!relations.contains(relation)) relations.add(relation);
            }
        }
        // Crossref的license或撤稿通知不等同于当前论文的开放/撤稿状态，缺少明确字段时保留未知。
        return new ScholarlyMetadata(observedAt,
                optionalCount(root.get("is-referenced-by-count"), "is-referenced-by-count", warnings),
                null, null, null, relations);
    }

    private static Long optionalCount(JsonNode value, String field, List<String> warnings) {
        if (value == null || value.isNull()) return null;
        if (value.isIntegralNumber() && value.canConvertToLong() && value.asLong() >= 0) return value.asLong();
        warnings.add(field + "不是有效的非负整数，未计为零");
        return null;
    }

    private static Boolean optionalBoolean(JsonNode value, String field, List<String> warnings) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.asBoolean();
        warnings.add(field + "不是布尔值，状态保留未知");
        return null;
    }
}
