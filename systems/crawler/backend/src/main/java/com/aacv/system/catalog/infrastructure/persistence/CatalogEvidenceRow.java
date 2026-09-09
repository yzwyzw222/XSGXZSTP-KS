package com.aacv.system.catalog.infrastructure.persistence;

import java.time.Instant;

public class CatalogEvidenceRow {
    private long organizationId;
    private String displayName;
    private String sourceCode;
    private Instant firstObservedAt;
    private Instant lastObservedAt;
    private Integer firstPublicationYear;
    private Integer lastPublicationYear;
    private long achievementCount;
    private long datedAchievementCount;

    public long getOrganizationId() { return organizationId; }
    public void setOrganizationId(long value) { organizationId = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String value) { sourceCode = value; }
    public Instant getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(Instant value) { firstObservedAt = value; }
    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant value) { lastObservedAt = value; }
    public Integer getFirstPublicationYear() { return firstPublicationYear; }
    public void setFirstPublicationYear(Integer value) { firstPublicationYear = value; }
    public Integer getLastPublicationYear() { return lastPublicationYear; }
    public void setLastPublicationYear(Integer value) { lastPublicationYear = value; }
    public long getAchievementCount() { return achievementCount; }
    public void setAchievementCount(long value) { achievementCount = value; }
    public long getDatedAchievementCount() { return datedAchievementCount; }
    public void setDatedAchievementCount(long value) { datedAchievementCount = value; }
}
