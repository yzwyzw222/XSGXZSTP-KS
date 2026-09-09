package com.aacv.application.auth;

import com.aacv.api.auth.UserResponse;
import com.aacv.infrastructure.security.SessionUser;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public AuthService(AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository,
                       SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    public UserResponse login(String username, String password,
                              HttpServletRequest request, HttpServletResponse response) {
        var auth = new UsernamePasswordAuthenticationToken(username, password);
        Authentication result = authenticationManager.authenticate(auth);

        SecurityContextHolder.getContext().setAuthentication(result);
        securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
        sessionAuthenticationStrategy.onAuthentication(result, request, response);

        return new UserResponse((SessionUser) result.getPrincipal());
    }

    public UserResponse getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof SessionUser user)) {
            return null;
        }
        return new UserResponse(user);
    }
}