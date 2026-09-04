package com.aacv.system.analytics.api;

import com.aacv.system.analytics.domain.AnalyticsDistributionItem;
import com.aacv.system.analytics.domain.AnalyticsDistributions;
import com.aacv.system.analytics.domain.AnalyticsSnapshot;
import java.time.Instant;
import java.util.List;

public record AnalyticsDistributionResponse(
        List<AnalyticsDistributionItem> achievementTypes,
        List<AnalyticsDistributionItem> sources,
        List<AnalyticsDistributionItem> organizations,
        List<AnalyticsDistributionItem> topics,
        AnalyticsScopeResponse scope,
        Instant updatedAt) {

    static AnalyticsDistributionResponse from(AnalyticsSnapshot<AnalyticsDistributions> snapshot) {
        AnalyticsDistributions value = snapshot.value();
        return new AnalyticsDistributionResponse(
                value.achievementTypes(), value.sources(), value.organizations(), value.topics(),
                AnalyticsScopeResponse.mysql(snapshot.filters()), snapshot.updatedAt());
    }
}
