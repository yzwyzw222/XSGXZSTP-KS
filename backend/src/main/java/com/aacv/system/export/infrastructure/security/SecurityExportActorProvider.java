package com.aacv.system.export.infrastructure.security;

import com.aacv.system.export.application.port.ExportActorProvider;
import com.aacv.system.identity.domain.Permission;
import com.aacv.system.identity.infrastructure.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SecurityExportActorProvider implements ExportActorProvider {

    @Override
    public ExportActor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("未找到当前登录用户");
        }
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> Permission.USER_LIST.name().equals(authority.getAuthority()));
        return new ExportActor(principal.userId(), administrator);
    }
}
