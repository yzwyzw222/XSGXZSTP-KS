package com.aacv.system.ingestion.infrastructure.persistence;

import java.time.Instant;

class RawRecordPersistenceRow {

    private Long id;
    private long sourceId;
    private long runId;
    private String externalRecordId;
    private String sourceUrl;
    private Instant fetchedAt;
    private String payloadHash;
    private String parserVersion;
    private String payload;
    private Instant payloadExpiresAt;
    private Instant now;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getSourceId() {
        return sourceId;
    }

    public void setSourceId(long sourceId) {
        this.sourceId = sourceId;
    }

    public long getRunId() {
        return runId;
    }

    public void setRunId(long runId) {
        this.runId = runId;
    }

    public String getExternalRecordId() {
        return externalRecordId;
    }

    public void setExternalRecordId(String externalRecordId) {
        this.externalRecordId = externalRecordId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public void setParserVersion(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getPayloadExpiresAt() {
        return payloadExpiresAt;
    }

    public void setPayloadExpiresAt(Instant payloadExpiresAt) {
        this.payloadExpiresAt = payloadExpiresAt;
    }

    public Instant getNow() {
        return now;
    }

    public void setNow(Instant now) {
        this.now = now;
    }
}
