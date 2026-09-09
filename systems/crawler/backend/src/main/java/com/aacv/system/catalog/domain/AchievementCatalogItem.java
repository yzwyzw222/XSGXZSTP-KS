package com.aacv.system.catalog.domain;

import java.time.LocalDate;
import java.util.List;

public record AchievementCatalogItem(
        long id,
        String title,
        String doi,
        String achievementType,
        LocalDate publicationDate,
        String primaryVenue,
        List<String> authors,
        List<String> topics) {

    public AchievementCatalogItem {
        authors = authors == null ? List.of() : List.copyOf(authors);
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}
