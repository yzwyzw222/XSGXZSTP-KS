package com.aacv.system.identity.domain;

public enum UserStatus {
    ACTIVE,
    DISABLED,
    PASSWORD_RESET_REQUIRED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
