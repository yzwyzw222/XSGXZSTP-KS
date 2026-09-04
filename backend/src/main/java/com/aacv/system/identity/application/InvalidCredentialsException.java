package com.aacv.system.identity.application;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("用户名或密码错误");
    }
}
