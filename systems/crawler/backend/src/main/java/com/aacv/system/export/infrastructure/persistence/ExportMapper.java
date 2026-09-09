package com.aacv.system.export.infrastructure.persistence;

import com.aacv.system.export.domain.ExportFilter;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface ExportMapper {
    void lockRequester(@Param("userId") long userId);

    long countActiveByRequester(@Param("userId") long userId);

    long countActive();

    long countRecords(@Param("filter") ExportFilter filter);

    List<ExportRecordRow> findRecords(@Param("filter") ExportFilter filter, @Param("limit") int limit);

    int insert(ExportTaskRow task);

    ExportTaskRow findById(@Param("taskId") String taskId);

    int claim(@Param("taskId") String taskId, @Param("startedAt") Instant startedAt);

    int markSucceeded(
            @Param("taskId") String taskId,
            @Param("exportedCount") long exportedCount,
            @Param("fileName") String fileName,
            @Param("fileRelativePath") String fileRelativePath,
            @Param("completedAt") Instant completedAt,
            @Param("expiresAt") Instant expiresAt);

    int markFailed(
            @Param("taskId") String taskId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") Instant completedAt);

    List<String> findRunningIds();

    List<String> findPendingIds(@Param("limit") int limit);

    int markExpired(@Param("taskId") String taskId, @Param("expiredAt") Instant expiredAt);
}
