package com.aacv.system.source.infrastructure.persistence;

import java.time.Instant;

public class DataSourceRow {

    private Long id;
    private String sourceCode;
    private String sourceType;
    private String baseUrl;
    private boolean enabled;
    private int requestsPerSecond;
    private int maxConcurrency;
    private int connectTimeoutSeconds;
    private int responseTimeoutSeconds;
    private int maxRetries;
    private int maxResponseBytes;
    private String complianceNote;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    private int consecutiveFailures;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(int requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getResponseTimeoutSeconds() { return responseTimeoutSeconds; }
    public void setResponseTimeoutSeconds(int responseTimeoutSeconds) { this.responseTimeoutSeconds = responseTimeoutSeconds; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
    public String getComplianceNote() { return complianceNote; }
    public void setComplianceNote(String complianceNote) { this.complianceNote = complianceNote; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }
    public Instant getLastFailureAt() { return lastFailureAt; }
    public void setLastFailureAt(Instant lastFailureAt) { this.lastFailureAt = lastFailureAt; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
