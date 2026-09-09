package com.aacv.system.identity.application;

public class UsernameConflictException extends RuntimeException {

    public UsernameConflictException() {
        super("用户名已存在");
    }
}
