package com.aacv.system.source.infrastructure.crossref;

import java.time.Duration;
import java.util.Map;

record CrossrefHttpResponse(
        int statusCode,
        byte[] body,
        Duration retryAfter,
        Map<String, String> responseMetadata) {

    CrossrefHttpResponse {
        body = body == null ? new byte[0] : body.clone();
        responseMetadata = responseMetadata == null ? Map.of() : Map.copyOf(responseMetadata);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
