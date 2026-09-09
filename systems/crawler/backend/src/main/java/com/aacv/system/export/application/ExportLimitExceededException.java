package com.aacv.system.export.application;

public class ExportLimitExceededException extends RuntimeException {
    public ExportLimitExceededException(String message) {
        super(message);
    }
}
