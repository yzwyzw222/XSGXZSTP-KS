package com.aacv.system.catalog.domain;

import java.time.Instant;
import java.util.List;

public record CatalogEntityEvidence(
        long entityId, CatalogEntityKind entityType, List<OrganizationName> names,
        List<AffiliationObservation> affiliations, boolean namesTruncated, boolean affiliationsTruncated) {

    public CatalogEntityEvidence {
        names = List.copyOf(names);
        affiliations = List.copyOf(affiliations);
    }

    public record OrganizationName(String displayName, String sourceCode, Instant firstObservedAt, Instant lastObservedAt) { }

    public record AffiliationObservation(
            long organizationId, String displayName, Integer firstPublicationYear, Integer lastPublicationYear,
            long achievementCount, long datedAchievementCount) { }
}
