package com.aacv.system.identity.domain;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("密码长度必须为12至128位");
        }
        if (password.isBlank() || password.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("密码不能只包含空白字符或包含控制字符");
        }
    }
}
