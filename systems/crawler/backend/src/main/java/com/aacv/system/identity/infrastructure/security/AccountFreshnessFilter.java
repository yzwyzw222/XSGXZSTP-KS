package com.aacv.system.identity.infrastructure.security;

import com.aacv.system.identity.application.port.UserAccountRepository;
import com.aacv.system.identity.domain.UserAccount;
import com.aacv.system.shared.domain.ErrorCode;
import com.aacv.system.shared.infrastructure.web.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountFreshnessFilter extends OncePerRequestFilter {

    private final UserAccountRepository repository;
    private final ProblemResponseWriter problemResponseWriter;

    public AccountFreshnessFilter(
            UserAccountRepository repository, ProblemResponseWriter problemResponseWriter) {
        this.repository = repository;
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            Optional<UserAccount> current = repository.findById(principal.userId());
            if (current.isEmpty()
                    || !current.orElseThrow().canAuthenticate()
                    || principal.securityVersion() == null
                    || current.orElseThrow().securityVersion() != principal.securityVersion()) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                problemResponseWriter.write(
                        request,
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        ErrorCode.SESSION_EXPIRED,
                        "账号状态已变化，请重新登录");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
