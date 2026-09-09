package com.aacv.system.analytics.infrastructure.persistence;

class AnalyticsRow {
    private long achievementCount;
    private long authorCount;
    private long organizationCount;
    private long sourceCount;
    private Integer publicationYear;
    private String itemKey;
    private String itemLabel;
    private Long leftId;
    private String leftLabel;
    private Long rightId;
    private String rightLabel;
    private long sharedAchievementCount;
    private long withDoiCount;
    private long withPublicationYearCount;
    private long withAbstractCount;
    private long withCitationCount;
    private long withOpenAccessStatusCount;
    private long withRetractionStatusCount;
    private long authorshipsMayBeIncompleteCount;

    public long getWithDoiCount() { return withDoiCount; }
    public long getWithPublicationYearCount() { return withPublicationYearCount; }
    public long getWithAbstractCount() { return withAbstractCount; }
    public long getWithCitationCount() { return withCitationCount; }
    public long getWithOpenAccessStatusCount() { return withOpenAccessStatusCount; }
    public long getWithRetractionStatusCount() { return withRetractionStatusCount; }
    public long getAuthorshipsMayBeIncompleteCount() { return authorshipsMayBeIncompleteCount; }

    public long getAchievementCount() {
        return achievementCount;
    }

    public long getAuthorCount() {
        return authorCount;
    }

    public long getOrganizationCount() {
        return organizationCount;
    }

    public long getSourceCount() {
        return sourceCount;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public String getItemKey() {
        return itemKey;
    }

    public String getItemLabel() {
        return itemLabel;
    }

    public Long getLeftId() {
        return leftId;
    }

    public String getLeftLabel() {
        return leftLabel;
    }

    public Long getRightId() {
        return rightId;
    }

    public String getRightLabel() {
        return rightLabel;
    }

    public long getSharedAchievementCount() {
        return sharedAchievementCount;
    }
}
