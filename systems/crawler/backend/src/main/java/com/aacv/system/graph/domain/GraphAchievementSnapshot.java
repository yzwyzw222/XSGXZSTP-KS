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
        List<ReferencedAchievement> references,
        String abstractText,
        List<Author> advisors,
        List<Institution> institutions) {

    public GraphAchievementSnapshot(long achievementId, String title, String achievementType, String language,
            LocalDate publicationDate, String doi, Venue venue, List<Author> authors, List<Affiliation> affiliations,
            List<Topic> topics, List<ReferencedAchievement> references) {
        this(achievementId, title, achievementType, language, publicationDate, doi, venue, authors,
                affiliations, topics, references, null, List.of(), List.of());
    }

    public record Institution(long id, String name) { }

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
