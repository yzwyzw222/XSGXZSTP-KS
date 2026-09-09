package com.aacv.system.export.infrastructure.persistence;

import java.time.Instant;

class ExportTaskRow {
    private String id;
    private String format;
    private String status;
    private String filtersJson;
    private long requestedBy;
    private long requestedCount;
    private long exportedCount;
    private String downloadToken;
    private String fileName;
    private String fileRelativePath;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant expiresAt;
    private String errorCode;
    private String errorMessage;
    private long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFiltersJson() { return filtersJson; }
    public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }
    public long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(long requestedBy) { this.requestedBy = requestedBy; }
    public long getRequestedCount() { return requestedCount; }
    public void setRequestedCount(long requestedCount) { this.requestedCount = requestedCount; }
    public long getExportedCount() { return exportedCount; }
    public void setExportedCount(long exportedCount) { this.exportedCount = exportedCount; }
    public String getDownloadToken() { return downloadToken; }
    public void setDownloadToken(String downloadToken) { this.downloadToken = downloadToken; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileRelativePath() { return fileRelativePath; }
    public void setFileRelativePath(String fileRelativePath) { this.fileRelativePath = fileRelativePath; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
