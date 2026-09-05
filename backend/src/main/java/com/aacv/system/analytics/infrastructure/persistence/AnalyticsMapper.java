package com.aacv.system.analytics.infrastructure.persistence;

import com.aacv.system.analytics.domain.AnalyticsQuery;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface AnalyticsMapper {
    AnalyticsRow coverage(@Param("query") AnalyticsQuery query);
    AnalyticsRow overview(@Param("query") AnalyticsQuery query);

    List<AnalyticsRow> trends(@Param("query") AnalyticsQuery query);

    List<AnalyticsRow> achievementTypes(@Param("query") AnalyticsQuery query);

    List<AnalyticsRow> sources(@Param("query") AnalyticsQuery query);

    List<AnalyticsRow> organizations(@Param("query") AnalyticsQuery query);

    List<AnalyticsRow> topics(@Param("query") AnalyticsQuery query);

    List<AnalyticsRow> authorCollaboration(
            @Param("query") AnalyticsQuery query,
            @Param("limit") int limit);

    List<AnalyticsRow> organizationCollaboration(
            @Param("query") AnalyticsQuery query,
            @Param("limit") int limit);

    Instant lastUpdatedAt(@Param("query") AnalyticsQuery query);
}
