package com.aacv.system.operations.infrastructure.persistence;

import java.time.Instant;

class AlertEventRow {
    private long id;
    private String alertType;
    private String severity;
    private String status;
    private String subjectType;
    private String subjectId;
    private String dedupKey;
    private String summary;
    private String evidenceJson;
    private Instant detectedSignalAt;
    private Instant firstDetectedAt;
    private Instant lastDetectedAt;
    private long occurrenceCount;
    private Long acknowledgedBy;
    private Instant acknowledgedAt;
    private String acknowledgementReason;
    private long version;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public Instant getDetectedSignalAt() { return detectedSignalAt; }
    public void setDetectedSignalAt(Instant detectedSignalAt) { this.detectedSignalAt = detectedSignalAt; }
    public Instant getFirstDetectedAt() { return firstDetectedAt; }
    public void setFirstDetectedAt(Instant firstDetectedAt) { this.firstDetectedAt = firstDetectedAt; }
    public Instant getLastDetectedAt() { return lastDetectedAt; }
    public void setLastDetectedAt(Instant lastDetectedAt) { this.lastDetectedAt = lastDetectedAt; }
    public long getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(long occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public Long getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(Long acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public String getAcknowledgementReason() { return acknowledgementReason; }
    public void setAcknowledgementReason(String acknowledgementReason) { this.acknowledgementReason = acknowledgementReason; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
