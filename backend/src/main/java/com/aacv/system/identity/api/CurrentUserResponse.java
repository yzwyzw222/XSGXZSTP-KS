package com.aacv.system.identity.api;

import com.aacv.system.identity.domain.AuthorizationPolicy;
import com.aacv.system.identity.domain.Permission;
import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.infrastructure.security.UserPrincipal;
import java.util.Set;
import java.util.TreeSet;

public record CurrentUserResponse(
        long id,
        String username,
        Set<RoleCode> roles,
        Set<Permission> permissions) {

    public static CurrentUserResponse from(UserPrincipal principal) {
        return new CurrentUserResponse(
                principal.userId(),
                principal.getUsername(),
                new TreeSet<>(principal.roles()),
                new TreeSet<>(AuthorizationPolicy.permissionsFor(principal.roles())));
    }
}
