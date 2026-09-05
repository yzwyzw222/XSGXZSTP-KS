package com.aacv.system.operations.domain;

public enum AuditCategory {
    LOGIN, OPERATION;

    public static AuditCategory of(AuditAction action) {
        return switch (action) {
            case LOGIN_SUCCEEDED, LOGIN_FAILED, LOGOUT -> LOGIN;
            default -> OPERATION;
        };
    }
}
