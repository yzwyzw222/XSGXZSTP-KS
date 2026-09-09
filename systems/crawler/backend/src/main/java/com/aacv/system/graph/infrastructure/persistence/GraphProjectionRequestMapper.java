package com.aacv.system.graph.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface GraphProjectionRequestMapper {

    int advanceDesiredVersion(@Param("achievementId") long achievementId, @Param("now") Instant now);

    long findDesiredVersion(long achievementId);

    int insertOutboxEvent(
            @Param("eventId") String eventId,
            @Param("achievementId") long achievementId,
            @Param("desiredVersion") long desiredVersion,
            @Param("now") Instant now);

    List<Long> findRelatedAchievementIds(
            @Param("entityType") String entityType, @Param("entityIds") Collection<Long> entityIds);
}
