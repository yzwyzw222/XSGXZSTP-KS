package com.aacv.system.ingestion.infrastructure.persistence;

public class SourceSnapshotRow {

    private long sourceId;
    private long rawRecordId;
    private String sourceType;
    private int sourcePriority;
    private String normalizedPayload;

    public long getSourceId() { return sourceId; }
    public void setSourceId(long sourceId) { this.sourceId = sourceId; }
    public long getRawRecordId() { return rawRecordId; }
    public void setRawRecordId(long rawRecordId) { this.rawRecordId = rawRecordId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public int getSourcePriority() { return sourcePriority; }
    public void setSourcePriority(int sourcePriority) { this.sourcePriority = sourcePriority; }
    public String getNormalizedPayload() { return normalizedPayload; }
    public void setNormalizedPayload(String normalizedPayload) { this.normalizedPayload = normalizedPayload; }
}
