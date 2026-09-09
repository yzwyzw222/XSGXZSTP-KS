package com.xsyu.academicgraph.infrastructure.integration;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class PortalAuthenticationFilterTest {
    private HttpServer server;
    private PortalAuthenticationFilter filter;
    private volatile int identityStatus;
    private volatile String roles;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        identityStatus = 200;
        roles = "[\"ADMIN\"]";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/__integration/auth/me", exchange -> {
            requests.incrementAndGet();
            if (!"PORTAL_SESSION=test-session".equals(exchange.getRequestHeaders().getFirst("Cookie"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            byte[] body = ("{\"id\":7,\"username\":\"research-user\",\"roles\":" + roles
                    + ",\"permissions\":[]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(identityStatus, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        filter = new PortalAuthenticationFilter("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse request(String method, String path, Cookie... cookies) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setCookies(cookies);
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> called.set(true));
        if (response.getStatus() == 200) assertTrue(called.get());
        else assertFalse(called.get());
        return response;
    }

    @Test
    void rejectsAnonymousLegacyAndDuplicateCookies() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "legacy-admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertEquals(401, request("GET", "/api/v1/papers", new Cookie("JSESSIONID", "legacy")).getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, request("GET", "/api/v1/papers", new Cookie("PORTAL_SESSION", "one"),
                new Cookie("PORTAL_SESSION", "two")).getStatus());
        assertEquals(0, requests.get());
    }

    @Test
    void mapsAdminAndRevalidatesAfterLogout() throws Exception {
        Cookie cookie = new Cookie("PORTAL_SESSION", "test-session");
        assertEquals(200, request("GET", "/api/v1/auth/me", cookie).getStatus());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("research-user", authentication.getName());
        assertTrue(authentication.getAuthorities().stream().anyMatch(value -> "ROLE_ADMIN".equals(value.getAuthority())));
        identityStatus = 401;
        assertEquals(401, request("GET", "/api/v1/papers", cookie).getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(2, requests.get());
    }

    @Test
    void keepsResearchersReadOnlyAndOperatorsNonAdministrative() throws Exception {
        Cookie cookie = new Cookie("PORTAL_SESSION", "test-session");
        roles = "[\"RESEARCHER\"]";
        assertEquals(200, request("GET", "/api/v1/papers", cookie).getStatus());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(value -> "ROLE_RESEARCHER".equals(value.getAuthority())));
        assertEquals(403, request("POST", "/api/v1/papers", cookie).getStatus());
        roles = "[\"DATA_OPERATOR\"]";
        assertEquals(200, request("POST", "/api/v1/papers", cookie).getStatus());
        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertTrue(authorities.stream().anyMatch(value -> "ROLE_ANALYST".equals(value.getAuthority())));
        assertFalse(authorities.stream().anyMatch(value -> "ROLE_ADMIN".equals(value.getAuthority())));
    }

    @Test
    void failsClosedOnUnavailableOrMalformedIdentity() throws Exception {
        Cookie cookie = new Cookie("PORTAL_SESSION", "test-session");
        identityStatus = 503;
        assertEquals(503, request("GET", "/api/v1/papers", cookie).getStatus());
        identityStatus = 200;
        for (String invalid : List.of("[]", "[\"UNKNOWN\"]", "null", "[null]")) {
            roles = invalid;
            assertEquals(503, request("GET", "/api/v1/papers", cookie).getStatus());
        }
        server.stop(0);
        assertEquals(503, request("GET", "/api/v1/papers", cookie).getStatus());
    }

    @Test
    void leavesHealthAndCsrfAvailableButClosesLegacyLogin() throws Exception {
        assertEquals(200, request("GET", "/api/integration/health").getStatus());
        assertEquals(200, request("GET", "/api/v1/auth/csrf").getStatus());
        for (String action : List.of("login", "logout", "register")) {
            assertEquals(410, request("POST", "/api/v1/auth/" + action).getStatus());
        }
        assertEquals(0, requests.get());
    }

    @Test
    void rejectsNonLoopbackIdentityEndpoints() {
        for (String url : List.of("http://example.com", "http://127.0.0.1/other", "http://user@127.0.0.1", "http:/invalid")) {
            assertThrows(IllegalArgumentException.class, () -> new PortalAuthenticationFilter(url));
        }
    }
}
