package com.aacv.system.analytics.api;

import com.aacv.system.analytics.domain.AnalyticsOverview;
import com.aacv.system.analytics.domain.AnalyticsSnapshot;
import java.time.Instant;

public record AnalyticsOverviewResponse(
        long achievementCount,
        long authorCount,
        long organizationCount,
        long sourceCount,
        AnalyticsScopeResponse scope,
        Instant updatedAt) {

    static AnalyticsOverviewResponse from(AnalyticsSnapshot<AnalyticsOverview> snapshot) {
        AnalyticsOverview value = snapshot.value();
        return new AnalyticsOverviewResponse(
                value.achievementCount(), value.authorCount(), value.organizationCount(), value.sourceCount(),
                AnalyticsScopeResponse.mysql(snapshot.filters()), snapshot.updatedAt());
    }
}
