package com.aacv.system.catalog.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;

public class CatalogRow {
    private long id;
    private long achievementId;
    private String title;
    private String doi;
    private String achievementType;
    private String language;
    private LocalDate publicationDate;
    private String primaryVenue;
    private String abstractText;
    private boolean authorshipsMayBeIncomplete;
    private long entityId;
    private String externalId;
    private String orcid;
    private String displayName;
    private String entityType;
    private int position;
    private long organizationId;
    private String organizationExternalId;
    private String organizationName;
    private long achievementCount;
    private long rawRecordId;
    private String sourceCode;
    private String externalRecordId;
    private String sourceUrl;
    private String parserVersion;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private String referencedExternalWorkId;
    private String fieldName;
    private boolean manualOverride;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getAchievementId() { return achievementId; }
    public void setAchievementId(long achievementId) { this.achievementId = achievementId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDoi() { return doi; }
    public void setDoi(String doi) { this.doi = doi; }
    public String getAchievementType() { return achievementType; }
    public void setAchievementType(String achievementType) { this.achievementType = achievementType; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public LocalDate getPublicationDate() { return publicationDate; }
    public void setPublicationDate(LocalDate publicationDate) { this.publicationDate = publicationDate; }
    public String getPrimaryVenue() { return primaryVenue; }
    public void setPrimaryVenue(String primaryVenue) { this.primaryVenue = primaryVenue; }
    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
    public boolean isAuthorshipsMayBeIncomplete() { return authorshipsMayBeIncomplete; }
    public void setAuthorshipsMayBeIncomplete(boolean value) { this.authorshipsMayBeIncomplete = value; }
    public long getEntityId() { return entityId; }
    public void setEntityId(long entityId) { this.entityId = entityId; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getOrcid() { return orcid; }
    public void setOrcid(String orcid) { this.orcid = orcid; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public long getOrganizationId() { return organizationId; }
    public void setOrganizationId(long organizationId) { this.organizationId = organizationId; }
    public String getOrganizationExternalId() { return organizationExternalId; }
    public void setOrganizationExternalId(String value) { this.organizationExternalId = value; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public long getAchievementCount() { return achievementCount; }
    public void setAchievementCount(long achievementCount) { this.achievementCount = achievementCount; }
    public long getRawRecordId() { return rawRecordId; }
    public void setRawRecordId(long rawRecordId) { this.rawRecordId = rawRecordId; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getExternalRecordId() { return externalRecordId; }
    public void setExternalRecordId(String externalRecordId) { this.externalRecordId = externalRecordId; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getReferencedExternalWorkId() { return referencedExternalWorkId; }
    public void setReferencedExternalWorkId(String value) { this.referencedExternalWorkId = value; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public boolean isManualOverride() { return manualOverride; }
    public void setManualOverride(boolean manualOverride) { this.manualOverride = manualOverride; }
}
