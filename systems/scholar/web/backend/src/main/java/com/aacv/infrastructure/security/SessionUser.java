package com.aacv.infrastructure.security;

import com.aacv.domain.user.Role;
import com.aacv.domain.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SessionUser implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String password;
    private final String displayName;
    private final Set<Role> roles;
    private final boolean enabled;

    public SessionUser(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.displayName = user.getDisplayName();
        this.roles = Collections.unmodifiableSet(new HashSet<>(user.getRoles()));
        this.enabled = user.isEnabled();
    }

    /** 统一身份只在当前请求内使用，不创建本地账号或保存密码。 */
    public SessionUser(Long id, String username, Set<Role> roles) {
        this.id = id;
        this.username = username;
        this.password = "";
        this.displayName = username;
        this.roles = Set.copyOf(roles);
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return roles.stream()
                .map(role -> "ROLE_" + role.name())
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
