package com.aacv.system.graph.infrastructure.persistence;

import com.aacv.system.graph.domain.GraphMaintenanceRun;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GraphMaintenanceMapper {

    int insertRun(GraphMaintenanceRow row);

    GraphMaintenanceRun findRun(long runId);

    GraphMaintenanceRun findLatestFailed(String runType);

    int resumeRun(@Param("runId") long runId, @Param("requestedBy") long requestedBy);

    int markRunning(long runId);

    int markSucceeded(long runId);

    int markFailed(
            @Param("runId") long runId,
            @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary);

    List<Long> findAchievementPage(@Param("cursor") long cursor, @Param("size") int size);

    Long findDesiredVersion(long achievementId);

    int advanceProgress(
            @Param("runId") long runId,
            @Param("cursor") long cursor,
            @Param("scanned") long scanned,
            @Param("repaired") long repaired,
            @Param("differences") long differences);

    long countRuns();

    List<GraphMaintenanceRun> findRuns(@Param("offset") long offset, @Param("size") int size);
}
