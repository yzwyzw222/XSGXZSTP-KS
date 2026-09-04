package com.aacv.system.export.application.port;

import com.aacv.system.export.domain.ExportFilter;
import com.aacv.system.export.domain.ExportRecord;
import com.aacv.system.export.domain.ExportTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExportRepository {
    void lockRequester(long userId);

    long countActiveByRequester(long userId);

    long countActive();

    long countRecords(ExportFilter filter);

    List<ExportRecord> findRecords(ExportFilter filter, int limit);

    void insert(ExportTask task);

    Optional<ExportTask> findById(String taskId);

    boolean claim(String taskId, Instant startedAt);

    void markSucceeded(
            String taskId,
            long exportedCount,
            String fileName,
            String fileRelativePath,
            Instant completedAt,
            Instant expiresAt);

    boolean markFailed(String taskId, String errorCode, String errorMessage, Instant completedAt);

    List<String> findRunningIds();

    List<String> findPendingIds(int limit);

    boolean markExpired(String taskId, Instant expiredAt);
}
