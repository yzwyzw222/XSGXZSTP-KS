package com.aacv.system.quality.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;

class QualityMetricRow {
    private long id;
    private long sourceId;
    private long taskId;
    private long runId;
    private String metricCode;
    private long numerator;
    private long denominator;
    private BigDecimal metricValue;
    private Instant measuredAt;
    private long version;
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getSourceId() { return sourceId; }
    public void setSourceId(long sourceId) { this.sourceId = sourceId; }
    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }
    public long getRunId() { return runId; }
    public void setRunId(long runId) { this.runId = runId; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public long getNumerator() { return numerator; }
    public void setNumerator(long numerator) { this.numerator = numerator; }
    public long getDenominator() { return denominator; }
    public void setDenominator(long denominator) { this.denominator = denominator; }
    public BigDecimal getMetricValue() { return metricValue; }
    public void setMetricValue(BigDecimal metricValue) { this.metricValue = metricValue; }
    public Instant getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(Instant measuredAt) { this.measuredAt = measuredAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
