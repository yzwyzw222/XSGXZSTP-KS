package com.aacv.system.catalog.api;

import com.aacv.system.catalog.domain.AchievementCatalogDetail;
import java.time.Instant;
import java.util.List;

public record AchievementDetailResponse(
        AchievementSummaryResponse summary,
        String language,
        String abstractText,
        boolean authorshipsMayBeIncomplete,
        List<AuthorshipResponse> authorships,
        List<String> referencedWorkIds,
        List<SourceTraceResponse> sources,
        List<FieldStateResponse> fields) {

    static AchievementDetailResponse from(AchievementCatalogDetail detail) {
        return new AchievementDetailResponse(
                AchievementPageResponse.toSummary(detail.summary()),
                detail.language(),
                detail.abstractText(),
                detail.authorshipsMayBeIncomplete(),
                detail.authorships().stream().map(AuthorshipResponse::from).toList(),
                detail.referencedWorkIds(),
                detail.sources().stream().map(SourceTraceResponse::from).toList(),
                detail.fields().stream().map(FieldStateResponse::from).toList());
    }

    public record AuthorshipResponse(
            long authorId,
            String openAlexId,
            String orcid,
            String displayName,
            int position,
            List<OrganizationResponse> organizations) {
        static AuthorshipResponse from(AchievementCatalogDetail.Authorship authorship) {
            return new AuthorshipResponse(
                    authorship.authorId(),
                    authorship.openAlexId(),
                    authorship.orcid(),
                    authorship.displayName(),
                    authorship.position(),
                    authorship.organizations().stream().map(OrganizationResponse::from).toList());
        }
    }

    public record OrganizationResponse(long id, String openAlexId, String displayName) {
        static OrganizationResponse from(AchievementCatalogDetail.Organization organization) {
            return new OrganizationResponse(
                    organization.id(), organization.openAlexId(), organization.displayName());
        }
    }

    public record SourceTraceResponse(
            long sourceRecordId,
            long rawRecordId,
            String sourceCode,
            String externalRecordId,
            String sourceUrl,
            Instant firstSeenAt,
            Instant lastSeenAt,
            String parserVersion) {
        static SourceTraceResponse from(AchievementCatalogDetail.SourceTrace source) {
            return new SourceTraceResponse(
                    source.sourceRecordId(),
                    source.rawRecordId(),
                    source.sourceCode(),
                    source.externalRecordId(),
                    source.sourceUrl(),
                    source.firstSeenAt(),
                    source.lastSeenAt(),
                    source.parserVersion());
        }
    }

    public record FieldStateResponse(
            String fieldName,
            String sourceCode,
            Long rawRecordId,
            boolean manualOverride) {
        static FieldStateResponse from(AchievementCatalogDetail.FieldState field) {
            return new FieldStateResponse(
                    field.fieldName(), field.sourceCode(), field.rawRecordId(), field.manualOverride());
        }
    }
}
