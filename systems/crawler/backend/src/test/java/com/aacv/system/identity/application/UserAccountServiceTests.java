package com.aacv.system.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.identity.application.port.SessionInvalidator;
import com.aacv.system.identity.application.port.UserAccountRepository;
import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserAccount;
import com.aacv.system.identity.domain.UserStatus;
import com.aacv.system.identity.domain.Username;
import com.aacv.system.operations.application.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserAccountServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final String STRONG_PASSWORD = "correct horse battery staple";

    private UserAccountRepository repository;
    private SessionInvalidator sessionInvalidator;
    private PasswordEncoder passwordEncoder;
    private AuditService auditService;
    private UserAccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        sessionInvalidator = mock(SessionInvalidator.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditService = mock(AuditService.class);
        service = new UserAccountService(
                repository,
                sessionInvalidator,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC),
                auditService, mock(com.aacv.system.operations.application.port.CurrentActorProvider.class));
    }

    @Test
    void bootstrapCreatesExactlyOneAdministratorWhenUserTableIsEmpty() {
        UserAccount created = account(1, UserStatus.ACTIVE, 0, Set.of(RoleCode.ADMIN));
        when(repository.acquireInitialAdminLock(5)).thenReturn(true);
        when(repository.countUsers()).thenReturn(0L);
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("{bcrypt}encoded");
        when(repository.create(
                        new Username("admin"),
                        "{bcrypt}encoded",
                        UserStatus.ACTIVE,
                        Set.of(RoleCode.ADMIN),
                        NOW))
                .thenReturn(created);

        Optional<UserAccount> result = service.bootstrapInitialAdministrator(
                new CreateUserCommand("Admin", STRONG_PASSWORD, Set.of(RoleCode.ADMIN)));

        assertTrue(result.isPresent());
        assertEquals(1, result.orElseThrow().id());
        verify(repository).releaseInitialAdminLock();
    }

    @Test
    void bootstrapDoesNotCreateOrOverwriteWhenAnyUserExists() {
        when(repository.acquireInitialAdminLock(5)).thenReturn(true);
        when(repository.countUsers()).thenReturn(1L);

        Optional<UserAccount> result = service.bootstrapInitialAdministrator(
                new CreateUserCommand("admin", STRONG_PASSWORD, Set.of(RoleCode.ADMIN)));

        assertTrue(result.isEmpty());
        verify(repository, never()).create(any(), any(), any(), any(), any());
        verify(passwordEncoder, never()).encode(any());
        verify(repository).releaseInitialAdminLock();
    }

    @Test
    void disablingUserInvalidatesExistingSessionsAfterOptimisticUpdate() {
        UserAccount active = account(7, UserStatus.ACTIVE, 2, Set.of(RoleCode.RESEARCHER));
        UserAccount disabled = account(7, UserStatus.DISABLED, 3, Set.of(RoleCode.RESEARCHER));
        when(repository.findById(7)).thenReturn(Optional.of(active), Optional.of(disabled));
        when(repository.updateStatus(7, 2, UserStatus.DISABLED, NOW)).thenReturn(true);

        UserAccount result = service.disableUser(7, 2);

        assertEquals(UserStatus.DISABLED, result.status());
        verify(sessionInvalidator).invalidateByPrincipalName("user7");
    }

    @Test
    void passwordResetUsesHashAndInvalidatesExistingSessions() {
        UserAccount active = account(9, UserStatus.ACTIVE, 4, Set.of(RoleCode.DATA_OPERATOR));
        UserAccount reset = account(9, UserStatus.ACTIVE, 5, Set.of(RoleCode.DATA_OPERATOR));
        when(repository.findById(9)).thenReturn(Optional.of(active), Optional.of(reset));
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("{bcrypt}new-hash");
        when(repository.updatePassword(9, 4, "{bcrypt}new-hash", UserStatus.ACTIVE, NOW)).thenReturn(true);

        UserAccount result = service.resetPassword(9, 4, STRONG_PASSWORD);

        assertEquals(5, result.version());
        verify(sessionInvalidator).invalidateByPrincipalName("user9");
        verify(repository).updatePassword(9, 4, "{bcrypt}new-hash", UserStatus.ACTIVE, NOW);
    }

    @Test
    void optimisticConflictDoesNotInvalidateSessions() {
        UserAccount active = account(11, UserStatus.ACTIVE, 3, Set.of(RoleCode.RESEARCHER));
        when(repository.findById(11)).thenReturn(Optional.of(active));
        when(repository.updateStatus(11, 2, UserStatus.DISABLED, NOW)).thenReturn(false);

        assertThrows(VersionConflictException.class, () -> service.disableUser(11, 2));

        verify(sessionInvalidator, never()).invalidateByPrincipalName(any());
    }

    @Test
    void shortPasswordIsRejectedBeforeHashingOrWriting() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.createUser(new CreateUserCommand("valid-user", "too-short", Set.of(RoleCode.RESEARCHER))));

        verify(passwordEncoder, never()).encode(any());
        verify(repository, never()).create(any(), any(), any(), any(), any());
    }

    private UserAccount account(long id, UserStatus status, long version, Set<RoleCode> roles) {
        return new UserAccount(
                id,
                new Username("user" + id),
                "{bcrypt}stored",
                status,
                version,
                NOW,
                NOW,
                NOW,
                roles);
    }
}
