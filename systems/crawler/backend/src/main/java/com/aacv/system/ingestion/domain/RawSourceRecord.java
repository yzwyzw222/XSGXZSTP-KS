package com.aacv.system.ingestion.domain;

import com.aacv.system.source.domain.SourceType;
import java.net.URI;
import java.time.Instant;

public record RawSourceRecord(
        SourceType sourceType,
        String externalRecordId,
        URI sourceLocation,
        String payload,
        Instant fetchedAt) {

    public RawSourceRecord {
        if (sourceType == null || externalRecordId == null || externalRecordId.isBlank()
                || externalRecordId.length() > 255) {
            throw new IllegalArgumentException("来源类型和外部记录ID不能为空");
        }
        if (sourceLocation == null || !"https".equalsIgnoreCase(sourceLocation.getScheme())) {
            throw new IllegalArgumentException("来源定位必须使用HTTPS");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("原始Payload不能为空");
        }
        if (fetchedAt == null) {
            throw new IllegalArgumentException("抓取时间不能为空");
        }
    }
}
