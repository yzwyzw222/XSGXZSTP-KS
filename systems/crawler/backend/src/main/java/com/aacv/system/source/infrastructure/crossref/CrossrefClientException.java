package com.aacv.system.source.infrastructure.crossref;

import com.aacv.system.source.application.SourceClientException;

public class CrossrefClientException extends SourceClientException {

    public CrossrefClientException(
            String category, boolean retryable, Integer statusCode, String safeMessage, Throwable cause) {
        super(category, retryable, statusCode, safeMessage, cause);
    }

    public CrossrefClientException(
            String category, boolean retryable, Integer statusCode, String safeMessage) {
        this(category, retryable, statusCode, safeMessage, null);
    }
}
