package com.aacv.infrastructure.security;

import com.aacv.domain.user.Role;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AuthorizationPolicy {

    public boolean hasRole(SessionUser user, Role role) {
        return user.getRoles().contains(role);
    }

    public boolean hasAnyRole(SessionUser user, Role... roles) {
        return Set.of(roles).stream().anyMatch(user.getRoles()::contains);
    }

    public boolean isAdmin(SessionUser user) {
        return user.getRoles().contains(Role.ADMIN);
    }

    public boolean isOwner(SessionUser user, Long resourceOwnerId) {
        return user.getId().equals(resourceOwnerId);
    }

    public boolean isOwnerOrAdmin(SessionUser user, Long resourceOwnerId) {
        return isAdmin(user) || isOwner(user, resourceOwnerId);
    }
}