package com.aacv.system.ingestion.infrastructure.persistence;

import java.time.Instant;

public class RetryFailureRow {
    private long failureId;
    private String sourceType;
    private String externalRecordId;
    private String sourceUrl;
    private String payload;
    private Instant fetchedAt;

    public long getFailureId() { return failureId; }
    public void setFailureId(long failureId) { this.failureId = failureId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getExternalRecordId() { return externalRecordId; }
    public void setExternalRecordId(String externalRecordId) { this.externalRecordId = externalRecordId; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
