package com.aacv.system.identity.application;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(long userId) {
        super("用户不存在: " + userId);
    }
}
