package com.aacv.system.source.infrastructure.openalex;

import java.time.Duration;
import java.util.Map;

record OpenAlexHttpResponse(
        int statusCode,
        byte[] body,
        Duration retryAfter,
        Map<String, String> responseMetadata) {

    OpenAlexHttpResponse {
        body = body == null ? new byte[0] : body.clone();
        responseMetadata = responseMetadata == null ? Map.of() : Map.copyOf(responseMetadata);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
