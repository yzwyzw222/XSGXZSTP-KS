package com.aacv.system.graph.infrastructure.persistence;

import com.aacv.system.graph.domain.GraphOutboxEvent;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GraphOutboxMapper {

    int recoverExpiredLeases(Instant now);

    boolean rebuildInProgress();

    List<Long> findClaimableIds(@Param("now") Instant now, @Param("limit") int limit);

    int claimIds(
            @Param("ids") List<Long> ids,
            @Param("workerId") String workerId,
            @Param("lockedUntil") Instant lockedUntil);

    List<GraphOutboxEvent> findClaimed(
            @Param("ids") List<Long> ids, @Param("workerId") String workerId);

    int markEventSucceeded(
            @Param("id") long id, @Param("workerId") String workerId, @Param("now") Instant now);

    int advanceAppliedVersion(
            @Param("achievementId") long achievementId,
            @Param("desiredVersion") long desiredVersion,
            @Param("now") Instant now);

    int markRetry(
            @Param("id") long id,
            @Param("workerId") String workerId,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary);

    int markDead(
            @Param("id") long id,
            @Param("workerId") String workerId,
            @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary,
            @Param("failedAt") Instant failedAt);

    int insertDeadLetter(
            @Param("eventId") String eventId,
            @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary,
            @Param("failedAt") Instant failedAt);

    Long findDeadAchievementId(String eventId);

    int linkReplay(@Param("eventId") String eventId, @Param("replayEventId") String replayEventId);
}
