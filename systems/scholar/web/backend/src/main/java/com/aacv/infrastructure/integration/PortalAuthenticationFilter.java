package com.aacv.infrastructure.integration;

import com.aacv.domain.user.Role;
import com.aacv.infrastructure.security.SessionUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.filter.OncePerRequestFilter;

/** 集成模式每次向门户确认身份，不复用本地登录状态或持久化跨系统权限快照。 */
public final class PortalAuthenticationFilter extends OncePerRequestFilter {
    private final RestClient client;

    public PortalAuthenticationFilter(String portalUrl) {
        URI origin = URI.create(portalUrl);
        if (!"http".equals(origin.getScheme()) || origin.getHost() == null || !Set.of("127.0.0.1", "localhost").contains(origin.getHost())
                || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
            throw new IllegalArgumentException("统一认证地址必须为本机 HTTP 入口");
        }
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(4));
        client = RestClient.builder().requestFactory(factory).baseUrl(portalUrl).build();
    }

    public record PortalIdentity(long id, String username, List<String> roles) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        SecurityContextHolder.clearContext();
        if (!path.startsWith("/api/") || path.equals("/api/integration/health") || path.equals("/api/v1/auth/csrf")) {
            chain.doFilter(request, response);
            return;
        }
        if (path.matches("/api/v1/auth/(?:login|register|logout)/?")) {
            reject(response, 410, "请使用统一登录入口。");
            return;
        }
        List<Cookie> cookies = request.getCookies() == null ? List.of()
                : Arrays.stream(request.getCookies()).filter(cookie -> "PORTAL_SESSION".equals(cookie.getName())).toList();
        if (cookies.size() != 1 || cookies.get(0).getValue() == null || !cookies.get(0).getValue().matches("[A-Za-z0-9+/=_-]{1,256}")) {
            reject(response, 401, "请先通过统一入口登录。");
            return;
        }
        PortalIdentity identity;
        try {
            identity = client.get().uri("/__integration/auth/me")
                    .header(HttpHeaders.COOKIE, "PORTAL_SESSION=" + cookies.get(0).getValue())
                    .retrieve().body(PortalIdentity.class);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            reject(response, status == 401 || status == 403 ? 401 : 503,
                    status == 401 || status == 403 ? "登录已失效，请重新登录。" : "统一认证服务暂不可用，请稍后重试。");
            return;
        } catch (RestClientException failure) {
            reject(response, 503, "无法连接统一认证服务，请稍后重试。");
            return;
        }
        if (identity == null || identity.id() <= 0 || identity.username() == null
                || identity.username().isBlank() || identity.username().length() > 64
                || identity.roles() == null || identity.roles().isEmpty()
                || identity.roles().stream().anyMatch(java.util.Objects::isNull)
                || !Set.of("ADMIN", "DATA_OPERATOR", "RESEARCHER").containsAll(identity.roles())) {
            reject(response, 503, "统一认证服务返回了无效身份。");
            return;
        }
        // 研究人员只读；写入仍需数据操作员或管理员，不能因接入统一登录提升权限。
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                && identity.roles().stream().noneMatch(role -> role.equals("ADMIN") || role.equals("DATA_OPERATOR"))) {
            reject(response, 403, "当前账号没有执行该操作的权限。");
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication(identity));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken authentication(PortalIdentity identity) {
        Role role = identity.roles().contains("ADMIN") ? Role.ADMIN
                : identity.roles().contains("DATA_OPERATOR") ? Role.OPERATOR : Role.VIEWER;
        var user = new SessionUser(identity.id(), identity.username(), Set.of(role));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + message + "\"}");
    }
}
