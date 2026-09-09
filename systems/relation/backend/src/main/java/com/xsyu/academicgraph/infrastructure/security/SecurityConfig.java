package com.xsyu.academicgraph.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsyu.academicgraph.api.common.ApiError;
import com.xsyu.academicgraph.infrastructure.web.ProblemJson;
import com.xsyu.academicgraph.infrastructure.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.io.IOException;
import com.xsyu.academicgraph.infrastructure.integration.PortalAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Spring Security 总配置。
 * 认证方案（工程规范）：服务端 Session + CSRF 防护，禁用 JWT/Basic/表单登录——
 * 前端登录后凭 JSESSIONID Cookie 保持会话，写操作带 X-CSRF-TOKEN 头防跨站请求伪造。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 允许在方法上用 @PreAuthorize 做细粒度权限控制
public class SecurityConfig {

    /** BCrypt：密码只存哈希，登录时用同一编码器比对 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 认证管理器：把"用户名+密码"交给 DaoAuthenticationProvider 校验 */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** 用户来源：从 MySQL sys_user 表加载账号与角色 */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService,
                                                               PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Session 安全上下文仓库（与过滤器链内部使用的默认实现一致）。
     * 登录/注册成功后 AuthService 用它显式把认证信息写进 Session——
     * Spring Security 6 不会自动保存手工设置的 SecurityContext，必须走 saveContext。
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper, Environment environment,
            @Value("${integration.portal-url:http://127.0.0.1:18000}") String portalUrl) throws Exception {
        // CSRF Token 存 Cookie（httpOnly），前端从 /auth/csrf 接口拿 Token 值放到 X-CSRF-TOKEN 头
        CookieCsrfTokenRepository csrfRepo = new CookieCsrfTokenRepository();
        csrfRepo.setHeaderName("X-CSRF-TOKEN");
        // Spring Security 6 默认把 token 值做延迟解析处理，这里关掉让 /auth/csrf 能直接读到明文
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(handler)
                        .ignoringRequestMatchers("/api/v1/auth/login", "/api/v1/auth/register")
                )
                // 纯 Session 认证：不需要 Basic 弹窗和默认登录页
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) // 有会话才创建，不强制
                )
                .authorizeHttpRequests(auth -> auth
                        // 无需登录：拿 CSRF、注册、登录、接口文档、错误页
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error", "/api/integration/health").permitAll()
                        // 后台管理只有 ADMIN 角色能进
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 其余接口一律需要登录
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // 未登录访问受保护接口：返回 401 problem+json（而不是默认跳转登录页）
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(objectMapper, response, HttpStatus.UNAUTHORIZED.value(),
                                        "未登录或会话已过期", "请先登录后再访问该接口", "AUTH_REQUIRED", request.getRequestURI()))
                        // 已登录但权限不足：403
                        .accessDeniedHandler((request, response, e) ->
                                writeError(objectMapper, response, HttpStatus.FORBIDDEN.value(),
                                        "权限不足", "当前账号没有执行该操作的权限", "FORBIDDEN", request.getRequestURI()))
                );
        if (environment.matchesProfiles("integration")) {
            http.addFilterAfter(new PortalAuthenticationFilter(portalUrl), SecurityContextHolderFilter.class);
        }
        return http.build();
    }

    private static void writeError(ObjectMapper mapper, HttpServletResponse response, int status,
                                   String title, String detail, String code, String instance) throws IOException {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        ProblemJson.write(response, mapper,
                ApiError.of(status, title, detail, instance, code, traceId == null ? "unknown" : traceId));
    }
}
