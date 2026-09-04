package com.aacv.system.export.infrastructure.persistence;

import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.domain.ExportFilter;
import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportRecord;
import com.aacv.system.export.domain.ExportStatus;
import com.aacv.system.export.domain.ExportTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
class MyBatisExportRepository implements ExportRepository {

    private final ExportMapper mapper;
    private final ObjectMapper objectMapper;

    MyBatisExportRepository(ExportMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void lockRequester(long userId) {
        mapper.lockRequester(userId);
    }

    @Override
    public long countActiveByRequester(long userId) {
        return mapper.countActiveByRequester(userId);
    }

    @Override
    public long countActive() {
        return mapper.countActive();
    }

    @Override
    public long countRecords(ExportFilter filter) {
        return mapper.countRecords(filter);
    }

    @Override
    public List<ExportRecord> findRecords(ExportFilter filter, int limit) {
        return mapper.findRecords(filter, limit).stream()
                .map(row -> new ExportRecord(
                        row.getId(), row.getTitle(), row.getDoi(), row.getAchievementType(),
                        row.getLanguage(), row.getPublicationDate(), row.getPrimaryVenue()))
                .toList();
    }

    @Override
    public void insert(ExportTask task) {
        ExportTaskRow row = new ExportTaskRow();
        row.setId(task.id());
        row.setFormat(task.format().name());
        row.setStatus(task.status().name());
        row.setFiltersJson(writeFilters(task.filters()));
        row.setRequestedBy(task.requestedBy());
        row.setRequestedCount(task.requestedCount());
        row.setExportedCount(task.exportedCount());
        row.setDownloadToken(task.downloadToken());
        row.setCreatedAt(task.createdAt());
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException("导出任务创建失败");
        }
    }

    @Override
    public Optional<ExportTask> findById(String taskId) {
        return Optional.ofNullable(mapper.findById(taskId)).map(this::toDomain);
    }

    @Override
    public boolean claim(String taskId, Instant startedAt) {
        return mapper.claim(taskId, startedAt) == 1;
    }

    @Override
    public void markSucceeded(
            String taskId,
            long exportedCount,
            String fileName,
            String fileRelativePath,
            Instant completedAt,
            Instant expiresAt) {
        if (mapper.markSucceeded(taskId, exportedCount, fileName, fileRelativePath, completedAt, expiresAt) != 1) {
            throw new IllegalStateException("导出任务完成状态写入失败");
        }
    }

    @Override
    public boolean markFailed(String taskId, String errorCode, String errorMessage, Instant completedAt) {
        return mapper.markFailed(taskId, errorCode, errorMessage, completedAt) == 1;
    }

    @Override
    public List<String> findRunningIds() {
        return mapper.findRunningIds();
    }

    @Override
    public List<String> findPendingIds(int limit) {
        return mapper.findPendingIds(limit);
    }

    @Override
    public boolean markExpired(String taskId, Instant expiredAt) {
        return mapper.markExpired(taskId, expiredAt) == 1;
    }

    private ExportTask toDomain(ExportTaskRow row) {
        return new ExportTask(
                row.getId(), ExportFormat.valueOf(row.getFormat()), ExportStatus.valueOf(row.getStatus()),
                readFilters(row.getFiltersJson()), row.getRequestedBy(), row.getRequestedCount(),
                row.getExportedCount(), row.getDownloadToken(), row.getFileName(), row.getFileRelativePath(),
                row.getCreatedAt(), row.getStartedAt(), row.getCompletedAt(), row.getExpiresAt(),
                row.getErrorCode(), row.getErrorMessage(), row.getVersion());
    }

    private String writeFilters(ExportFilter filters) {
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JacksonException exception) {
            throw new IllegalStateException("导出筛选条件无法序列化", exception);
        }
    }

    private ExportFilter readFilters(String filtersJson) {
        try {
            return objectMapper.readValue(filtersJson, ExportFilter.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("导出筛选条件无法读取", exception);
        }
    }
}
