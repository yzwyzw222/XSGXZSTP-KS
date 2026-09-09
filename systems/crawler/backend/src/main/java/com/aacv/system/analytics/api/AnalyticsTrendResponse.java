package com.aacv.system.analytics.api;

import com.aacv.system.analytics.domain.AnalyticsSnapshot;
import com.aacv.system.analytics.domain.AnalyticsTrend;
import java.time.Instant;
import java.util.List;

public record AnalyticsTrendResponse(
        List<AnalyticsTrend> items,
        AnalyticsScopeResponse scope,
        Instant updatedAt) {

    static AnalyticsTrendResponse from(AnalyticsSnapshot<List<AnalyticsTrend>> snapshot) {
        return new AnalyticsTrendResponse(
                snapshot.value(), AnalyticsScopeResponse.mysql(snapshot.filters()), snapshot.updatedAt());
    }
}
