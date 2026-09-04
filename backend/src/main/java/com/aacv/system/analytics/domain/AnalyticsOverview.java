package com.aacv.system.analytics.domain;

public record AnalyticsOverview(
        long achievementCount,
        long authorCount,
        long organizationCount,
        long sourceCount) {
}
