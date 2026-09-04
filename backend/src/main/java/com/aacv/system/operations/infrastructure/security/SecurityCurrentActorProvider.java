package com.aacv.system.operations.infrastructure.security;

import com.aacv.system.identity.infrastructure.security.UserPrincipal;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import java.util.OptionalLong;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityCurrentActorProvider implements CurrentActorProvider {

    @Override
    public OptionalLong currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return OptionalLong.of(principal.userId());
        }
        return OptionalLong.empty();
    }
}
