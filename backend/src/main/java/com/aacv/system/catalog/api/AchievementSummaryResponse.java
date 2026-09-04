package com.aacv.system.catalog.api;

import java.time.LocalDate;
import java.util.List;

public record AchievementSummaryResponse(
        long id,
        String title,
        String doi,
        String achievementType,
        LocalDate publicationDate,
        String primaryVenue,
        List<String> authors,
        List<String> topics) {

    public AchievementSummaryResponse {
        authors = authors == null ? List.of() : List.copyOf(authors);
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}
