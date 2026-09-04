package com.aacv.system.quality.infrastructure.persistence;

import java.time.Instant;

class QualityIssueRow {
    private long id;
    private long rawRecordId;
    private String externalRecordId;
    private String evidenceJson;
    private Instant createdAt;
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getRawRecordId() { return rawRecordId; }
    public void setRawRecordId(long rawRecordId) { this.rawRecordId = rawRecordId; }
    public String getExternalRecordId() { return externalRecordId; }
    public void setExternalRecordId(String externalRecordId) { this.externalRecordId = externalRecordId; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
