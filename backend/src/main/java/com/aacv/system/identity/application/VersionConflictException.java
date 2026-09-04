package com.aacv.system.identity.application;

public class VersionConflictException extends RuntimeException {

    public VersionConflictException(long userId) {
        super("用户版本冲突: " + userId);
    }
}
