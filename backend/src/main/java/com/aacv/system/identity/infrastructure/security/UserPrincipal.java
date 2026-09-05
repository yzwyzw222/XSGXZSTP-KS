package com.aacv.system.identity.infrastructure.security;

import com.aacv.system.identity.domain.AuthorizationPolicy;
import com.aacv.system.identity.domain.Permission;
import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserAccount;
import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class UserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String username;
    private String passwordHash;
    private final long version;
    // 保留序列化兼容性，旧会话缺少安全版本时必须重新认证。
    private final Long securityVersion;
    private final Instant credentialsChangedAt;
    private final Set<RoleCode> roles;
    private final Set<GrantedAuthority> authorities;

    private UserPrincipal(UserAccount account) {
        userId = account.id();
        username = account.username().value();
        passwordHash = account.passwordHash();
        version = account.version();
        securityVersion = account.securityVersion();
        credentialsChangedAt = account.credentialsChangedAt();
        roles = Set.copyOf(account.roles());
        LinkedHashSet<GrantedAuthority> granted = new LinkedHashSet<>();
        roles.stream()
                .sorted()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .forEach(granted::add);
        AuthorizationPolicy.permissionsFor(roles).stream()
                .sorted()
                .map(Permission::name)
                .map(SimpleGrantedAuthority::new)
                .forEach(granted::add);
        authorities = Set.copyOf(granted);
    }

    public static UserPrincipal from(UserAccount account) {
        return new UserPrincipal(account);
    }

    public long userId() {
        return userId;
    }

    public Long securityVersion() {
        return securityVersion;
    }

    public long version() {
        return version;
    }

    public Instant credentialsChangedAt() {
        return credentialsChangedAt;
    }

    public Set<RoleCode> roles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
