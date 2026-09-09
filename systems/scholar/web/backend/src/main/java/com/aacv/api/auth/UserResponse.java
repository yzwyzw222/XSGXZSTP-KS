package com.aacv.api.auth;

import com.aacv.domain.user.Role;
import com.aacv.infrastructure.security.SessionUser;

import java.util.Set;

public class UserResponse {

    private Long id;
    private String username;
    private String displayName;
    private Set<Role> roles;
    private Set<String> permissions;

    public UserResponse() {
    }

    public UserResponse(SessionUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.roles = user.getRoles();
        this.permissions = user.getPermissions();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }
}