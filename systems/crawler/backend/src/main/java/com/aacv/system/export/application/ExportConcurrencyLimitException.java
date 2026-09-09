package com.aacv.system.export.application;

public class ExportConcurrencyLimitException extends RuntimeException {
    public ExportConcurrencyLimitException(String message) {
        super(message);
    }
}
