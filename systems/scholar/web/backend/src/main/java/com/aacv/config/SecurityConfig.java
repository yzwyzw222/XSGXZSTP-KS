package com.aacv.config;

import com.aacv.api.common.ProblemResponse;
import com.aacv.infrastructure.security.AacvUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import com.aacv.infrastructure.integration.PortalAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * CSRF Token 交换与前端约定：
     * - Token 通过 Cookie 保存（同源请求自动携带）
     * - 非安全方法通过 X-CSRF-TOKEN 请求头回传
     * - Token 在 CsrfController 中写入 X-CSRF-TOKEN 响应头（GET /csrf 时）
     *
     * 仅重写 resolveCsrfTokenValue，使校验从 X-CSRF-TOKEN 请求头读取。
     */
    static final class AacvCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler
            implements CsrfTokenRequestHandler {

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String header = request.getHeader("X-CSRF-TOKEN");
            if (header != null && !header.isBlank()) {
                return header;
            }
            return super.resolveCsrfTokenValue(request, csrfToken);
        }
    }

    private final AacvUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(AacvUserDetailsService userDetailsService, ObjectMapper objectMapper) {
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment,
            @Value("${integration.portal-url:http://127.0.0.1:18000}") String portalUrl) throws Exception {
        var requestHandler = new AacvCsrfTokenRequestHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .userDetailsService(userDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(ex -> ex
                        // 未认证返回 401 problem+json，前端据此跳转登录页
                        .authenticationEntryPoint(this::handleUnauthenticated)
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                )
                .sessionManagement(session -> session
                        .sessionFixation().migrateSession()
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                );

        if (environment.matchesProfiles("integration")) {
            http.addFilterAfter(new PortalAuthenticationFilter(portalUrl), SecurityContextHolderFilter.class);
        }
        return http.build();
    }

    private void handleUnauthenticated(HttpServletRequest request, HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException authException)
            throws java.io.IOException {
        ProblemResponse problem = new ProblemResponse(
                401,
                "Unauthorized",
                "未登录或会话已过期",
                request.getRequestURI(),
                "UNAUTHORIZED"
        );
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder())
                .and()
                .build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }
}
