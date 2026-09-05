package com.aacv.system.crawl.infrastructure.persistence;

import java.time.Instant;

public class CrawlRunRow {
    private Long id;
    private long taskId;
    private String runNumber;
    private String triggerType;
    private Long parentRunId;
    private String status;
    private Long batchJobExecutionId;
    private long readCount;
    private long parsedCount;
    private long createdCount;
    private long updatedCount;
    private long duplicateCount;
    private long failureCount;
    private long requestCount;
    private String checkpoint;
    private Instant startedAt;
    private Instant finishedAt;
    private long version;
    private String completionReason;
    private Instant deferredUntil;
    private int quotaDeferrals;

    public String getCompletionReason() { return completionReason; }
    public void setCompletionReason(String completionReason) { this.completionReason = completionReason; }
    public Instant getDeferredUntil() { return deferredUntil; }
    public void setDeferredUntil(Instant deferredUntil) { this.deferredUntil = deferredUntil; }
    public int getQuotaDeferrals() { return quotaDeferrals; }
    public void setQuotaDeferrals(int quotaDeferrals) { this.quotaDeferrals = quotaDeferrals; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }
    public String getRunNumber() { return runNumber; }
    public void setRunNumber(String runNumber) { this.runNumber = runNumber; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public Long getParentRunId() { return parentRunId; }
    public void setParentRunId(Long parentRunId) { this.parentRunId = parentRunId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getBatchJobExecutionId() { return batchJobExecutionId; }
    public void setBatchJobExecutionId(Long batchJobExecutionId) { this.batchJobExecutionId = batchJobExecutionId; }
    public long getReadCount() { return readCount; }
    public void setReadCount(long readCount) { this.readCount = readCount; }
    public long getParsedCount() { return parsedCount; }
    public void setParsedCount(long parsedCount) { this.parsedCount = parsedCount; }
    public long getCreatedCount() { return createdCount; }
    public void setCreatedCount(long createdCount) { this.createdCount = createdCount; }
    public long getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(long updatedCount) { this.updatedCount = updatedCount; }
    public long getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(long duplicateCount) { this.duplicateCount = duplicateCount; }
    public long getFailureCount() { return failureCount; }
    public void setFailureCount(long failureCount) { this.failureCount = failureCount; }
    public long getRequestCount() { return requestCount; }
    public void setRequestCount(long requestCount) { this.requestCount = requestCount; }
    public String getCheckpoint() { return checkpoint; }
    public void setCheckpoint(String checkpoint) { this.checkpoint = checkpoint; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
