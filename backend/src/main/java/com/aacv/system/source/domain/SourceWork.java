package com.aacv.system.source.domain;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record SourceWork(
        String externalId,
        String doi,
        String title,
        String type,
        String language,
        LocalDate publicationDate,
        SourceVenue primaryVenue,
        List<SourceAuthorship> authorships,
        List<SourceTopic> topics,
        List<String> referencedWorkIds,
        String abstractText,
        boolean authorshipsMayBeIncomplete,
        List<String> fieldWarnings,
        SourceDatePrecision publicationDatePrecision,
        Instant indexedAt,
        ScholarlyMetadata scholarlyMetadata) {

    public SourceWork {
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("来源成果ID不能为空");
        }
        authorships = authorships == null ? List.of() : List.copyOf(authorships);
        topics = topics == null ? List.of() : List.copyOf(topics);
        referencedWorkIds = referencedWorkIds == null ? List.of() : List.copyOf(referencedWorkIds);
        fieldWarnings = fieldWarnings == null ? List.of() : List.copyOf(fieldWarnings);
        if (publicationDate == null && publicationDatePrecision != null) {
            throw new IllegalArgumentException("缺少发表日期时不能声明日期精度");
        }
    }

    public SourceWork(
            String externalId, String doi, String title, String type, String language,
            LocalDate publicationDate, SourceVenue primaryVenue, List<SourceAuthorship> authorships,
            List<SourceTopic> topics, List<String> referencedWorkIds, String abstractText,
            boolean authorshipsMayBeIncomplete, List<String> fieldWarnings,
            SourceDatePrecision publicationDatePrecision, Instant indexedAt) {
        this(externalId, doi, title, type, language, publicationDate, primaryVenue, authorships,
                topics, referencedWorkIds, abstractText, authorshipsMayBeIncomplete, fieldWarnings,
                publicationDatePrecision, indexedAt, null);
    }

    public SourceWork(
            String externalId,
            String doi,
            String title,
            String type,
            String language,
            LocalDate publicationDate,
            SourceVenue primaryVenue,
            List<SourceAuthorship> authorships,
            List<SourceTopic> topics,
            List<String> referencedWorkIds,
            String abstractText,
            boolean authorshipsMayBeIncomplete,
            List<String> fieldWarnings) {
        this(externalId, doi, title, type, language, publicationDate, primaryVenue,
                authorships, topics, referencedWorkIds, abstractText,
                authorshipsMayBeIncomplete, fieldWarnings,
                publicationDate == null ? null : SourceDatePrecision.DAY, null);
    }

    public record SourceVenue(
            String externalId, String displayName, String issnL, String type, List<String> issns) {

        public SourceVenue {
            issns = issns == null ? List.of() : List.copyOf(issns);
        }

        public SourceVenue(String externalId, String displayName, String issnL, String type) {
            this(externalId, displayName, issnL, type,
                    issnL == null ? List.of() : List.of(issnL));
        }
    }

    public record SourceAuthorship(
            int position,
            String authorExternalId,
            String authorDisplayName,
            String orcid,
            List<SourceOrganization> organizations) {

        public SourceAuthorship {
            if (position < 1) {
                throw new IllegalArgumentException("作者顺序无效");
            }
            organizations = organizations == null ? List.of() : List.copyOf(organizations);
        }
    }

    public record SourceOrganization(
            String externalId, String displayName, String countryCode, String type, String rorId) {

        public SourceOrganization(String externalId, String displayName, String countryCode, String type) {
            this(externalId, displayName, countryCode, type, null);
        }
    }

    public record SourceTopic(String externalId, String displayName, String subfieldName, String fieldName) {
    }

    public enum SourceDatePrecision {
        DAY,
        MONTH,
        YEAR,
        UNKNOWN
    }
}
