package com.aacv.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemResponse {

    private int status;
    private String title;
    private String detail;
    private String instance;
    private String errorCode;
    private String traceId;
    private List<FieldError> fieldErrors;

    public ProblemResponse() {
        this.traceId = generateTraceId();
    }

    public ProblemResponse(int status, String title, String detail, String instance, String errorCode) {
        this();
        this.status = status;
        this.title = title;
        this.detail = detail;
        this.instance = instance;
        this.errorCode = errorCode;
    }

    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getTraceId() {
        return traceId;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(List<FieldError> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public static class FieldError {
        private String field;
        private String message;

        public FieldError() {
        }

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}