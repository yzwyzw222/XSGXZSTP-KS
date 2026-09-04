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
