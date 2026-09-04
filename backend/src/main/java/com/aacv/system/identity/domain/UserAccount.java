package com.aacv.system.identity.domain;

import java.time.Instant;
import java.util.Set;

public record UserAccount(
        long id,
        Username username,
        String passwordHash,
        UserStatus status,
        long version,
        Instant credentialsChangedAt,
        Instant createdAt,
        Instant updatedAt,
        Set<RoleCode> roles) {

    public UserAccount {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean canAuthenticate() {
        return status != null && status.canAuthenticate();
    }
}
