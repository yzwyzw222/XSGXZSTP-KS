package com.aacv.system.ingestion.domain;

public record RetryFailureRecord(long failureId, RawSourceRecord rawRecord) {

    public RetryFailureRecord {
        if (failureId < 1 || rawRecord == null) {
            throw new IllegalArgumentException("失败重试记录无效");
        }
    }
}
