package com.aacv.system.governance.infrastructure.persistence;

import java.time.Instant;

class CandidateRow {
    private long id;
    private String entityType;
    private long leftEntityId;
    private long rightEntityId;
    private String matchBasis;
    private String evidenceJson;
    private String status;
    private Long sourceId;
    private int ruleVersion;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public long getLeftEntityId() { return leftEntityId; }
    public void setLeftEntityId(long leftEntityId) { this.leftEntityId = leftEntityId; }
    public long getRightEntityId() { return rightEntityId; }
    public void setRightEntityId(long rightEntityId) { this.rightEntityId = rightEntityId; }
    public String getMatchBasis() { return matchBasis; }
    public void setMatchBasis(String matchBasis) { this.matchBasis = matchBasis; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public int getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(int ruleVersion) { this.ruleVersion = ruleVersion; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
