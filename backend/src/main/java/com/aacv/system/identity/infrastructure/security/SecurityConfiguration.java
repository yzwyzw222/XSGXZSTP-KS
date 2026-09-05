package com.aacv.system.identity.infrastructure.security;

import com.aacv.system.identity.application.port.UserAccountRepository;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.infrastructure.web.FailedOperationAuditFilter;
import com.aacv.system.shared.infrastructure.web.ProblemResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            UserAccountRepository userAccountRepository,
            AuditService auditService,
            ProblemResponseWriter problemResponseWriter)
            throws Exception {
        AccountFreshnessFilter accountFreshnessFilter =
                new AccountFreshnessFilter(userAccountRepository, problemResponseWriter);
        ApiAuthenticationEntryPoint authenticationEntryPoint =
                new ApiAuthenticationEntryPoint(problemResponseWriter);
        ApiAccessDeniedHandler accessDeniedHandler = new ApiAccessDeniedHandler(problemResponseWriter);
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/api/v1/auth/csrf", "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers("/api/v1/users/**", "/api/v1/operations/audits/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/exports")
                        .hasAuthority("EXPORT_CREATE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/exports/**")
                        .hasAuthority("EXPORT_READ")
                        .requestMatchers("/api/v1/analytics/**")
                        .hasAuthority("ANALYTICS_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/operations/alerts/*/acknowledge")
                        .hasAuthority("ALERT_MANAGE")
                        .requestMatchers("/api/v1/operations/overview", "/api/v1/operations/alerts/**")
                        .hasAuthority("OPERATIONS_READ")
                        .requestMatchers("/api/v1/operations/graph-maintenance/rebuild")
                        .hasRole("ADMIN")
                        .requestMatchers(
                                "/api/v1/operations/graph-events/**",
                                "/api/v1/operations/graph-maintenance/**",
                                "/api/v1/graph/sync-status")
                        .hasAnyRole("ADMIN", "DATA_OPERATOR")
                        .requestMatchers("/api/v1/sources/**")
                        .authenticated()
                        .requestMatchers("/api/v1/crawl/**")
                        .hasAnyRole("ADMIN", "DATA_OPERATOR")
                        .requestMatchers("/api/v1/duplicate-candidates/**", "/api/v1/merge-decisions/**")
                        .hasAnyRole("ADMIN", "DATA_OPERATOR")
                        .requestMatchers("/api/v1/quality-metrics/**")
                        .hasAnyRole("ADMIN", "DATA_OPERATOR")
                        .requestMatchers("/api/v1/catalog/**")
                        .authenticated()
                        .requestMatchers("/api/v1/graph/**")
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(new FailedOperationAuditFilter(auditService), SecurityContextHolderFilter.class)
                .addFilterAfter(accountFreshnessFilter, FailedOperationAuditFilter.class);
        return http.build();
    }
}
