package com.aacv.system.identity.api;

import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserAccount;
import com.aacv.system.identity.domain.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public record UserResponse(
        long id,
        String username,
        UserStatus status,
        long version,
        Instant credentialsChangedAt,
        Instant createdAt,
        Instant updatedAt,
        Set<RoleCode> roles,
        String realName, String email, String phone, String organization, String department, String remark) {

    public static UserResponse from(UserAccount account) {
        return new UserResponse(
                account.id(),
                account.username().value(),
                account.status(),
                account.version(),
                account.credentialsChangedAt(),
                account.createdAt(),
                account.updatedAt(),
                new TreeSet<>(account.roles()),
                account.profile().realName(), account.profile().email(), account.profile().phone(),
                account.profile().organization(), account.profile().department(), account.profile().remark());
    }
}
