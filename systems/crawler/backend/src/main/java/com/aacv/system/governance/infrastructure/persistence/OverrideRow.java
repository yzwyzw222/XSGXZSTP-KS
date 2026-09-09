package com.aacv.system.governance.infrastructure.persistence;

import java.time.Instant;

class OverrideRow {
    private Long id;
    private long achievementId;
    private String fieldName;
    private String fieldValue;
    private long revisionId;
    private long actorUserId;
    private String reason;
    private boolean active;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getAchievementId() { return achievementId; }
    public void setAchievementId(long achievementId) { this.achievementId = achievementId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldValue() { return fieldValue; }
    public void setFieldValue(String fieldValue) { this.fieldValue = fieldValue; }
    public long getRevisionId() { return revisionId; }
    public void setRevisionId(long revisionId) { this.revisionId = revisionId; }
    public long getActorUserId() { return actorUserId; }
    public void setActorUserId(long actorUserId) { this.actorUserId = actorUserId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
