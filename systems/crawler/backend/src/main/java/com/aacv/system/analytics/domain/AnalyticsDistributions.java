package com.aacv.system.analytics.domain;

import java.util.List;

public record AnalyticsDistributions(
        List<AnalyticsDistributionItem> achievementTypes,
        List<AnalyticsDistributionItem> sources,
        List<AnalyticsDistributionItem> organizations,
        List<AnalyticsDistributionItem> topics) {

    public AnalyticsDistributions {
        achievementTypes = List.copyOf(achievementTypes);
        sources = List.copyOf(sources);
        organizations = List.copyOf(organizations);
        topics = List.copyOf(topics);
    }
}
