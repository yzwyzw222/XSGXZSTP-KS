package com.aacv.system.export.infrastructure.persistence;

import java.time.LocalDate;

class ExportRecordRow {
    private long id;
    private String title;
    private String doi;
    private String achievementType;
    private String language;
    private LocalDate publicationDate;
    private String primaryVenue;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getAchievementType() {
        return achievementType;
    }

    public void setAchievementType(String achievementType) {
        this.achievementType = achievementType;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(LocalDate publicationDate) {
        this.publicationDate = publicationDate;
    }

    public String getPrimaryVenue() {
        return primaryVenue;
    }

    public void setPrimaryVenue(String primaryVenue) {
        this.primaryVenue = primaryVenue;
    }
}
