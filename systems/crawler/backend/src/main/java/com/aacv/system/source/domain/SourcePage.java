package com.aacv.system.source.domain;

import com.aacv.system.ingestion.domain.RawSourceRecord;
import java.util.List;
import java.util.Map;

public record SourcePage(
        List<RawSourceRecord> records,
        OpaqueCursor nextCursor,
        int requestCount,
        Map<String, String> responseMetadata) {

    public SourcePage {
        records = records == null ? List.of() : List.copyOf(records);
        responseMetadata = responseMetadata == null ? Map.of() : Map.copyOf(responseMetadata);
        if (requestCount < 0) {
            throw new IllegalArgumentException("来源页面请求数不能为负数");
        }
    }
}
