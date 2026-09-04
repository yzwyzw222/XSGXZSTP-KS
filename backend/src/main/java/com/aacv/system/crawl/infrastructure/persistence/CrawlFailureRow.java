package com.aacv.system.crawl.infrastructure.persistence;

import java.time.Instant;

public class CrawlFailureRow {
    private long id;
    private long runId;
    private Long rawRecordId;
    private String externalRecordId;
    private String failureStage;
    private String errorCategory;
    private String safeMessage;
    private boolean retryable;
    private int attemptCount;
    private boolean resolved;
    private String evidenceHash;
    private Instant createdAt;
    private Instant updatedAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getRunId() { return runId; }
    public void setRunId(long runId) { this.runId = runId; }
    public Long getRawRecordId() { return rawRecordId; }
    public void setRawRecordId(Long rawRecordId) { this.rawRecordId = rawRecordId; }
    public String getExternalRecordId() { return externalRecordId; }
    public void setExternalRecordId(String value) { this.externalRecordId = value; }
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }
    public String getSafeMessage() { return safeMessage; }
    public void setSafeMessage(String safeMessage) { this.safeMessage = safeMessage; }
    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public String getEvidenceHash() { return evidenceHash; }
    public void setEvidenceHash(String evidenceHash) { this.evidenceHash = evidenceHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
