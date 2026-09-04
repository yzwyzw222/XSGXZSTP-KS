package com.aacv.system.analytics.application.port;

import com.aacv.system.analytics.domain.AnalyticsCollaboration;
import com.aacv.system.analytics.domain.AnalyticsDistributions;
import com.aacv.system.analytics.domain.AnalyticsOverview;
import com.aacv.system.analytics.domain.AnalyticsQuery;
import com.aacv.system.analytics.domain.AnalyticsTrend;
import java.time.Instant;
import java.util.List;

public interface AnalyticsRepository {
    AnalyticsOverview overview(AnalyticsQuery query);

    List<AnalyticsTrend> trends(AnalyticsQuery query);

    AnalyticsDistributions distributions(AnalyticsQuery query);

    AnalyticsCollaboration collaboration(AnalyticsQuery query, int limit);

    Instant lastUpdatedAt(AnalyticsQuery query);
}
