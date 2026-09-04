package com.aacv.system.crawl.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CrawlMapper {
    long countTasks();
    int countTaskName(@Param("sourceId") long sourceId, @Param("taskName") String taskName);
    List<CrawlTaskRow> findTaskPage(@Param("offset") long offset, @Param("size") int size);
    CrawlTaskRow findTaskById(@Param("id") long id);
    CrawlTaskRow lockTaskById(@Param("id") long id);
    int countTaskRuns(@Param("taskId") long taskId);
    int insertTask(CrawlTaskRow row);
    int updateTask(@Param("row") CrawlTaskRow row, @Param("expectedVersion") long expectedVersion);
    int countActiveConflicts(@Param("sourceId") long sourceId, @Param("parameterHash") String parameterHash);
    int insertPendingRun(
            @Param("taskId") long taskId,
            @Param("runNumber") String runNumber,
            @Param("parameterHash") String parameterHash,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd,
            @Param("triggerType") String triggerType,
            @Param("requestedBy") Long requestedBy,
            @Param("parentRunId") Long parentRunId,
            @Param("createdAt") Instant createdAt);
    int countRetryableFailures(@Param("runId") long runId, @Param("limit") int limit);
    long countFailures(@Param("runId") long runId);
    List<CrawlFailureRow> findFailurePage(
            @Param("runId") long runId,
            @Param("offset") long offset,
            @Param("size") int size);
    CrawlRunRow findRunByNumber(@Param("runNumber") String runNumber);
    CrawlRunRow findRunById(@Param("runId") long runId);
    CrawlRunRow lockRunById(@Param("runId") long runId);
    CrawlCheckpointRow findCheckpoint(@Param("runId") long runId);
    String findControlIntent(@Param("runId") long runId);
    int transitionRun(
            @Param("runId") long runId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("controlIntent") String controlIntent,
            @Param("batchJobExecutionId") Long batchJobExecutionId,
            @Param("setStartedAt") boolean setStartedAt,
            @Param("setFinishedAt") boolean setFinishedAt);
    int attachBatchExecution(
            @Param("runId") long runId,
            @Param("expectedStatus") String expectedStatus,
            @Param("batchJobExecutionId") long batchJobExecutionId);
    CrawlScheduleRow findScheduleByTaskId(@Param("taskId") long taskId);
    int insertSchedule(CrawlScheduleRow row);
    int updateSchedule(@Param("row") CrawlScheduleRow row, @Param("expectedVersion") long expectedVersion);
    List<CrawlScheduleRow> findEnabledSchedules();
    List<CrawlRecoveryCandidateRow> findRecoveryCandidates();
}
