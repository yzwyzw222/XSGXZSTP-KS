package com.aacv.system.catalog.domain;

import java.time.Instant;
import java.util.List;

public record AchievementCatalogDetail(
        AchievementCatalogItem summary,
        String language,
        String abstractText,
        boolean authorshipsMayBeIncomplete,
        List<Authorship> authorships,
        List<String> referencedWorkIds,
        List<SourceTrace> sources,
        List<FieldState> fields) {

    public AchievementCatalogDetail {
        if (summary == null) {
            throw new IllegalArgumentException("成果详情摘要不能为空");
        }
        authorships = authorships == null ? List.of() : List.copyOf(authorships);
        referencedWorkIds = referencedWorkIds == null ? List.of() : List.copyOf(referencedWorkIds);
        sources = sources == null ? List.of() : List.copyOf(sources);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public record Authorship(
            long authorId,
            String openAlexId,
            String orcid,
            String displayName,
            int position,
            List<Organization> organizations) {
        public Authorship {
            organizations = organizations == null ? List.of() : List.copyOf(organizations);
        }
    }

    public record Organization(long id, String openAlexId, String displayName) {
    }

    public record SourceTrace(
            long sourceRecordId,
            long rawRecordId,
            String sourceCode,
            String externalRecordId,
            String sourceUrl,
            Instant firstSeenAt,
            Instant lastSeenAt,
            String parserVersion,
            com.aacv.system.source.domain.ScholarlyMetadata scholarlyMetadata) {
    }

    public record FieldState(
            String fieldName,
            String sourceCode,
            Long rawRecordId,
            boolean manualOverride) {
    }
}
