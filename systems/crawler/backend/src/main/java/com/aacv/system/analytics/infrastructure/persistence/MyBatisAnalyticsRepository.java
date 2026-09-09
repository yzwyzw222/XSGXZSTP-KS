package com.aacv.system.analytics.infrastructure.persistence;

import com.aacv.system.analytics.domain.AnalyticsCoverage;
import com.aacv.system.analytics.application.port.AnalyticsRepository;
import com.aacv.system.analytics.domain.AnalyticsCollaboration;
import com.aacv.system.analytics.domain.AnalyticsCollaborationItem;
import com.aacv.system.analytics.domain.AnalyticsDistributionItem;
import com.aacv.system.analytics.domain.AnalyticsDistributions;
import com.aacv.system.analytics.domain.AnalyticsOverview;
import com.aacv.system.analytics.domain.AnalyticsQuery;
import com.aacv.system.analytics.domain.AnalyticsTrend;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class MyBatisAnalyticsRepository implements AnalyticsRepository {

    private final AnalyticsMapper mapper;

    MyBatisAnalyticsRepository(AnalyticsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AnalyticsOverview overview(AnalyticsQuery query) {
        AnalyticsRow row = mapper.overview(query);
        AnalyticsRow coverage = mapper.coverage(query);
        return new AnalyticsOverview(
                row.getAchievementCount(), row.getAuthorCount(), row.getOrganizationCount(), row.getSourceCount(),
                new AnalyticsCoverage(coverage.getWithDoiCount(),
                        coverage.getWithPublicationYearCount(), coverage.getWithAbstractCount(), coverage.getWithCitationCount(),
                        coverage.getWithOpenAccessStatusCount(), coverage.getWithRetractionStatusCount(),
                        coverage.getAuthorshipsMayBeIncompleteCount()));
    }

    @Override
    public List<AnalyticsTrend> trends(AnalyticsQuery query) {
        return mapper.trends(query).stream()
                .map(row -> new AnalyticsTrend(row.getPublicationYear(), row.getAchievementCount()))
                .toList();
    }

    @Override
    public AnalyticsDistributions distributions(AnalyticsQuery query) {
        return new AnalyticsDistributions(
                distributions(mapper.achievementTypes(query)),
                distributions(mapper.sources(query)),
                distributions(mapper.organizations(query)),
                distributions(mapper.topics(query)));
    }

    @Override
    public AnalyticsCollaboration collaboration(AnalyticsQuery query, int limit) {
        return new AnalyticsCollaboration(
                collaborations(mapper.authorCollaboration(query, limit)),
                collaborations(mapper.organizationCollaboration(query, limit)));
    }

    @Override
    public Instant lastUpdatedAt(AnalyticsQuery query) {
        return mapper.lastUpdatedAt(query);
    }

    private List<AnalyticsDistributionItem> distributions(List<AnalyticsRow> rows) {
        return rows.stream()
                .map(row -> new AnalyticsDistributionItem(
                        row.getItemKey(), row.getItemLabel(), row.getAchievementCount()))
                .toList();
    }

    private List<AnalyticsCollaborationItem> collaborations(List<AnalyticsRow> rows) {
        return rows.stream()
                .map(row -> new AnalyticsCollaborationItem(
                        row.getLeftId(), row.getLeftLabel(), row.getRightId(), row.getRightLabel(),
                        row.getSharedAchievementCount()))
                .toList();
    }
}
