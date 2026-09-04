package com.aacv.system.graph.domain;

import java.time.LocalDate;
import java.util.List;

public record GraphAchievementSnapshot(
        long achievementId,
        String title,
        String achievementType,
        String language,
        LocalDate publicationDate,
        String doi,
        Venue venue,
        List<Author> authors,
        List<Affiliation> affiliations,
        List<Topic> topics,
        List<ReferencedAchievement> references) {

    public record Venue(long id, String name, String venueType, String issn) {
    }

    public record Author(long id, String name, String orcid) {
    }

    public record Affiliation(
            long authorId,
            long institutionId,
            String institutionName,
            String standardCode,
            String countryCode) {
    }

    public record Topic(long id, String name, String code, String path) {
    }

    public record ReferencedAchievement(
            long id, String title, String achievementType, String language, LocalDate publicationDate, String doi) {
    }
}
