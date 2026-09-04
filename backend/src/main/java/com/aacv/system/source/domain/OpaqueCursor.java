package com.aacv.system.source.domain;

public record OpaqueCursor(String value) {

    public static final String FIRST_VALUE = "*";

    public OpaqueCursor {
        if (value == null || value.isBlank() || value.length() > 2048) {
            throw new IllegalArgumentException("来源游标不能为空且长度不能超过2048");
        }
    }

    public static OpaqueCursor first() {
        return new OpaqueCursor(FIRST_VALUE);
    }
}
