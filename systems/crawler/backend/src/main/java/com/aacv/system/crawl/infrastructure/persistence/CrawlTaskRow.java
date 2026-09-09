package com.aacv.system.crawl.infrastructure.persistence;

import java.time.Instant;

public class CrawlTaskRow {
    private Long id;
    private long sourceId;
    private String taskName;
    private int parameterVersion;
    private String parametersJson;
    private String parameterHash;
    private boolean enabled;
    private long version;
    private long createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getSourceId() { return sourceId; }
    public void setSourceId(long sourceId) { this.sourceId = sourceId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public int getParameterVersion() { return parameterVersion; }
    public void setParameterVersion(int parameterVersion) { this.parameterVersion = parameterVersion; }
    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
    public String getParameterHash() { return parameterHash; }
    public void setParameterHash(String parameterHash) { this.parameterHash = parameterHash; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public long getCreatedBy() { return createdBy; }
    public void setCreatedBy(long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
