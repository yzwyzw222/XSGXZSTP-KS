package com.aacv.system.crawl.infrastructure.persistence;

public class CrawlRecoveryCandidateRow {
    private long runId;
    private String businessStatus;
    private Long batchJobExecutionId;
    private String batchStatus;
    private boolean checkpointPresent;

    public long getRunId() { return runId; }
    public void setRunId(long runId) { this.runId = runId; }
    public String getBusinessStatus() { return businessStatus; }
    public void setBusinessStatus(String businessStatus) { this.businessStatus = businessStatus; }
    public Long getBatchJobExecutionId() { return batchJobExecutionId; }
    public void setBatchJobExecutionId(Long batchJobExecutionId) { this.batchJobExecutionId = batchJobExecutionId; }
    public String getBatchStatus() { return batchStatus; }
    public void setBatchStatus(String batchStatus) { this.batchStatus = batchStatus; }
    public boolean isCheckpointPresent() { return checkpointPresent; }
    public void setCheckpointPresent(boolean checkpointPresent) { this.checkpointPresent = checkpointPresent; }
}
