package com.aacv.system.export.domain;

import java.time.LocalDate;

public record ExportRecord(
        long id,
        String title,
        String doi,
        String achievementType,
        String language,
        LocalDate publicationDate,
        String primaryVenue) {
}
