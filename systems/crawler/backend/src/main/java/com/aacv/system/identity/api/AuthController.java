package com.aacv.system.identity.api;

import com.aacv.system.operations.infrastructure.web.FailedOperationAuditFilter;

import com.aacv.system.identity.application.InvalidCredentialsException;
import com.aacv.system.identity.infrastructure.security.UserPrincipal;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AuditService auditService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.auditService = auditService;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    public CurrentUserResponse login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            loginRequest.username(), loginRequest.password()));
            request.getSession(true);
            request.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            try {
                auditService.record(
                        AuditAction.LOGIN_SUCCEEDED,
                        "USER_ACCOUNT",
                        Long.toString(principal.userId()),
                        AuditResult.SUCCESS,
                        Map.of());
                securityContextRepository.saveContext(context, request, response);
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
                request.getSession(false).invalidate();
                throw exception;
            }
            return CurrentUserResponse.from(principal);
        } catch (AuthenticationException exception) {
            auditService.recordLoginFailure(loginRequest.username().trim(), "invalid_credentials");
            request.setAttribute(FailedOperationAuditFilter.LOGIN_RECORDED, true);
            throw new InvalidCredentialsException();
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        try {
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
                auditService.record(
                        AuditAction.LOGOUT,
                        "USER_ACCOUNT",
                        Long.toString(principal.userId()),
                        AuditResult.SUCCESS,
                        Map.of());
            }
        } finally {
            logoutHandler.logout(request, response, authentication);
        }
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return CurrentUserResponse.from((UserPrincipal) authentication.getPrincipal());
    }
}
