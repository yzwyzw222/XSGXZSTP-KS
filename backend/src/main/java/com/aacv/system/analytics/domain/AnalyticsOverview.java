package com.aacv.system.analytics.domain;

public record AnalyticsOverview(
        long achievementCount,
        long authorCount,
        long organizationCount,
        long sourceCount,
        AnalyticsCoverage coverage) {

    public AnalyticsOverview(long achievementCount, long authorCount, long organizationCount, long sourceCount) {
        this(achievementCount, authorCount, organizationCount, sourceCount, null);
    }
}
