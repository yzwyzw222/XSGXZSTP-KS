package com.aacv.system.analytics.application;

import com.aacv.system.analytics.application.port.AnalyticsRepository;
import com.aacv.system.analytics.domain.AnalyticsCollaboration;
import com.aacv.system.analytics.domain.AnalyticsDistributions;
import com.aacv.system.analytics.domain.AnalyticsOverview;
import com.aacv.system.analytics.domain.AnalyticsQuery;
import com.aacv.system.analytics.domain.AnalyticsSnapshot;
import com.aacv.system.analytics.domain.AnalyticsTrend;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private final AnalyticsRepository repository;

    public AnalyticsService(AnalyticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public AnalyticsSnapshot<AnalyticsOverview> overview(AnalyticsQuery query) {
        return snapshot(repository.overview(query), query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public AnalyticsSnapshot<List<AnalyticsTrend>> trends(AnalyticsQuery query) {
        return snapshot(repository.trends(query), query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public AnalyticsSnapshot<AnalyticsDistributions> distributions(AnalyticsQuery query) {
        return snapshot(repository.distributions(query), query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ANALYTICS_READ')")
    public AnalyticsSnapshot<AnalyticsCollaboration> collaboration(AnalyticsQuery query, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("合作统计数量限制无效");
        }
        return snapshot(repository.collaboration(query, limit), query);
    }

    private <T> AnalyticsSnapshot<T> snapshot(T value, AnalyticsQuery query) {
        return new AnalyticsSnapshot<>(value, query, repository.lastUpdatedAt(query));
    }
}
