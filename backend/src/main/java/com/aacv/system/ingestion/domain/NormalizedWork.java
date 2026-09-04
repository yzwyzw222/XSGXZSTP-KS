package com.aacv.system.ingestion.domain;

import java.time.LocalDate;
import java.util.List;

public record NormalizedWork(
        String externalId,
        String doi,
        String titleOriginal,
        String titleNormalized,
        String achievementType,
        String language,
        LocalDate publicationDate,
        DatePrecision datePrecision,
        String matchFingerprint,
        NormalizedVenue primaryVenue,
        List<NormalizedAuthorship> authorships,
        List<NormalizedTopic> topics,
        List<String> referencedWorkIds,
        String abstractText,
        boolean authorshipsMayBeIncomplete,
        List<String> fieldWarnings) {

    public NormalizedWork {
        authorships = authorships == null ? List.of() : List.copyOf(authorships);
        topics = topics == null ? List.of() : List.copyOf(topics);
        referencedWorkIds = referencedWorkIds == null ? List.of() : List.copyOf(referencedWorkIds);
        fieldWarnings = fieldWarnings == null ? List.of() : List.copyOf(fieldWarnings);
        if (externalId == null || externalId.isBlank() || achievementType == null
                || matchFingerprint == null || matchFingerprint.length() != 64) {
            throw new IllegalArgumentException("规范化成果缺少必要标识");
        }
    }

    public enum DatePrecision {
        DAY,
        MONTH,
        YEAR,
        UNKNOWN
    }

    public record NormalizedVenue(
            String externalId,
            String displayName,
            String issnL,
            String type) {
    }

    public record NormalizedAuthorship(
            int position,
            String authorExternalId,
            String displayName,
            String orcid,
            List<NormalizedOrganization> organizations) {

        public NormalizedAuthorship {
            organizations = organizations == null ? List.of() : List.copyOf(organizations);
        }
    }

    public record NormalizedOrganization(
            String externalId,
            String displayName,
            String countryCode,
            String type,
            String rorId) {

        public NormalizedOrganization(
                String externalId, String displayName, String countryCode, String type) {
            this(externalId, displayName, countryCode, type, null);
        }
    }

    public record NormalizedTopic(
            String externalId,
            String displayName,
            String subfieldName,
            String fieldName) {
    }
}
