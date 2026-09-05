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
        Set<RoleCode> roles,
        UserProfile profile,
        long securityVersion) {

    public UserAccount(long id, Username username, String passwordHash, UserStatus status, long version,
            Instant credentialsChangedAt, Instant createdAt, Instant updatedAt, Set<RoleCode> roles) {
        this(id, username, passwordHash, status, version, credentialsChangedAt, createdAt, updatedAt,
                roles, UserProfile.EMPTY, 0);
    }

    public UserAccount {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        profile = profile == null ? UserProfile.EMPTY : profile;
    }

    public boolean canAuthenticate() {
        return status != null && status.canAuthenticate();
    }
}
