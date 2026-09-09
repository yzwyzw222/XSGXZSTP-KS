package com.aacv.system.source.infrastructure.openalex;

import com.aacv.system.source.application.SourceClientException;

public class OpenAlexClientException extends SourceClientException {

    public OpenAlexClientException(
            String category, boolean retryable, Integer statusCode, String safeMessage, Throwable cause) {
        super(category, retryable, statusCode, safeMessage, cause);
    }

    public OpenAlexClientException(
            String category, boolean retryable, Integer statusCode, String safeMessage) {
        this(category, retryable, statusCode, safeMessage, null);
    }

}
