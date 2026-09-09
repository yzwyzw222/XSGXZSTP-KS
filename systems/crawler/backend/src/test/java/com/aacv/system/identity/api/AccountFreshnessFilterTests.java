package com.aacv.system.identity.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aacv.system.identity.application.port.UserAccountRepository;
import com.aacv.system.identity.domain.*;
import com.aacv.system.identity.infrastructure.security.AccountFreshnessFilter;
import com.aacv.system.identity.infrastructure.security.UserPrincipal;
import com.aacv.system.shared.infrastructure.web.ProblemResponseWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class AccountFreshnessFilterTests {
    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void profileVersionChangeDoesNotExpireSession() throws Exception {
        var principal = UserPrincipal.from(account(1, 0));
        var response = filter(principal, account(2, 0));
        assertEquals(200, response.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void securityVersionChangeAndLegacySessionRequireAuthentication() throws Exception {
        assertEquals(401, filter(UserPrincipal.from(account(1, 0)), account(2, 1)).getStatus());
        var legacy = UserPrincipal.from(account(1, 0));
        // 模拟旧序列化会话没有新增字段时的默认值。
        ReflectionTestUtils.setField(legacy, "securityVersion", null);
        assertEquals(401, filter(legacy, account(1, 0)).getStatus());
    }

    private MockHttpServletResponse filter(UserPrincipal principal, UserAccount account) throws Exception {
        var repository = mock(UserAccountRepository.class);
        when(repository.findById(1)).thenReturn(Optional.of(account));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        var request = new MockHttpServletRequest(); request.getSession();
        var response = new MockHttpServletResponse();
        new AccountFreshnessFilter(repository, new ProblemResponseWriter(new ObjectMapper()))
                .doFilter(request, response, new MockFilterChain());
        return response;
    }

    private UserAccount account(long version, long securityVersion) {
        return new UserAccount(1, new Username("test-user"), "unusable-hash", UserStatus.ACTIVE, version,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, Set.of(RoleCode.ADMIN), UserProfile.EMPTY, securityVersion);
    }
}
