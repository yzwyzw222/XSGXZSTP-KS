package com.aacv.system.graph.infrastructure.persistence;

import java.time.LocalDate;

record GraphSnapshotRow(
        long achievementId,
        String title,
        String achievementType,
        String language,
        LocalDate publicationDate,
        String doi,
        Long venueId,
        String venueName,
        String venueType,
        String venueIssn) {
}
