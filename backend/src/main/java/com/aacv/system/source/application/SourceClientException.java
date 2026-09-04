package com.aacv.system.source.application;

public class SourceClientException extends RuntimeException {

    private final String category;
    private final boolean retryable;
    private final Integer statusCode;

    public SourceClientException(
            String category, boolean retryable, Integer statusCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.category = category;
        this.retryable = retryable;
        this.statusCode = statusCode;
    }

    public SourceClientException(
            String category, boolean retryable, Integer statusCode, String safeMessage) {
        this(category, retryable, statusCode, safeMessage, null);
    }

    public String category() {
        return category;
    }

    public boolean retryable() {
        return retryable;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
