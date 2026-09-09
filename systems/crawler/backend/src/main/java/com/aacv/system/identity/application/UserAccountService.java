package com.aacv.system.identity.application;

import com.aacv.system.identity.application.port.SessionInvalidator;
import com.aacv.system.identity.application.port.UserAccountRepository;
import com.aacv.system.identity.domain.PasswordPolicy;
import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserAccount;
import com.aacv.system.identity.domain.UserStatus;
import com.aacv.system.identity.domain.Username;
import com.aacv.system.identity.domain.UserProfile;
import com.aacv.system.identity.domain.UserStatistics;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import com.aacv.system.shared.domain.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private static final int INITIAL_ADMIN_LOCK_TIMEOUT_SECONDS = 5;

    private final UserAccountRepository repository;
    private final SessionInvalidator sessionInvalidator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AuditService auditService;
    private final CurrentActorProvider currentActorProvider;

    public UserAccountService(
            UserAccountRepository repository,
            SessionInvalidator sessionInvalidator,
            PasswordEncoder passwordEncoder,
            Clock clock,
            AuditService auditService, CurrentActorProvider currentActorProvider) {
        this.repository = repository;
        this.sessionInvalidator = sessionInvalidator;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.auditService = auditService;
        this.currentActorProvider = currentActorProvider;
    }

    @Transactional(readOnly = true)
    public UserAccount getById(long userId) {
        return repository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findByUsername(String username) {
        return repository.findByUsername(new Username(username));
    }

    @Transactional(readOnly = true)
    public PageResult<UserAccount> findPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return repository.findPage(page, size);
    }

    @Transactional
    public UserAccount createUser(CreateUserCommand command) {
        UserAccount created = create(command, UserStatus.ACTIVE);
        auditService.record(
                AuditAction.USER_CREATED,
                "USER_ACCOUNT",
                Long.toString(created.id()),
                AuditResult.SUCCESS,
                Map.of("roles", roleSummary(created.roles())));
        return created;
    }

    @Transactional
    public Optional<UserAccount> bootstrapInitialAdministrator(CreateUserCommand command) {
        if (!repository.acquireInitialAdminLock(INITIAL_ADMIN_LOCK_TIMEOUT_SECONDS)) {
            throw new IllegalStateException("无法获得初始管理员引导锁");
        }
        try {
            if (repository.countUsers() != 0) {
                return Optional.empty();
            }
            Set<RoleCode> roles = requireRoles(command.roles());
            if (!roles.equals(Set.of(RoleCode.ADMIN))) {
                throw new IllegalArgumentException("初始管理员只能分配ADMIN角色");
            }
            return Optional.of(create(command, UserStatus.ACTIVE));
        } finally {
            repository.releaseInitialAdminLock();
        }
    }

    @Transactional(readOnly = true)
    public UserStatistics statistics() {
        UserStatistics result = repository.statistics();
        if (result.totalUsers() != result.admin() + result.dataOperator() + result.researcher()) {
            throw new IllegalStateException("用户角色数据不完整，无法统计");
        }
        return result;
    }

    @Transactional
    public UserAccount updateUser(long userId, UpdateUserCommand command) {
        if (command == null || command.profile() == null || command.status() == null || command.version() < 0) {
            throw new IllegalArgumentException("用户编辑参数无效");
        }
        Set<RoleCode> roles = requireRoles(command.roles());
        repository.lockAdministratorRole();
        UserAccount existing = getById(userId);
        requireVersion(existing, command.version());
        protectAdministrator(existing, command.status(), roles);
        boolean securityChanged = existing.status() != command.status() || !existing.roles().equals(roles);
        if (!securityChanged && existing.profile().equals(command.profile())) return existing;
        if (!repository.updateUser(userId, command.version(), command.profile(), command.status(), roles,
                !existing.roles().equals(roles), securityChanged, clock.instant())) throwVersionOrNotFound(userId);
        if (securityChanged) sessionInvalidator.invalidateByPrincipalName(existing.username().value());
        // 只记录变更类别，避免把联系方式等个人资料复制到审计摘要。
        auditService.record(AuditAction.USER_UPDATED, "USER_ACCOUNT", Long.toString(userId), AuditResult.SUCCESS,
                Map.of("profileChanged", Boolean.toString(!existing.profile().equals(command.profile())),
                        "rolesChanged", Boolean.toString(!existing.roles().equals(roles)),
                        "statusChanged", Boolean.toString(existing.status() != command.status())));
        return getById(userId);
    }

    private void requireVersion(UserAccount existing, long version) {
        if (version < 0 || existing.version() != version) throw new VersionConflictException(existing.id());
    }

    private void protectAdministrator(UserAccount existing, UserStatus status, Set<RoleCode> roles) {
        if (!existing.roles().contains(RoleCode.ADMIN)) return;
        boolean removesAccess = status != UserStatus.ACTIVE || !roles.contains(RoleCode.ADMIN);
        boolean changesAccess = status != existing.status() || !roles.contains(RoleCode.ADMIN);
        if (removesAccess && changesAccess && currentActorProvider.currentUserId().orElse(-1L) == existing.id()) {
            throw new ResourceConflictException("不能停用自己或移除自己的管理员角色");
        }
        if (existing.canAuthenticate() && removesAccess && repository.countActiveAdministrators() <= 1) {
            throw new ResourceConflictException("必须保留至少一个可用管理员");
        }
    }

    @Transactional
    public UserAccount enableUser(long userId, long expectedVersion) {
        repository.lockAdministratorRole();
        UserAccount existing = getById(userId);
        requireVersion(existing, expectedVersion);
        if (existing.status() == UserStatus.ACTIVE) return existing;
        Instant now = clock.instant();
        if (!repository.updateStatus(userId, expectedVersion, UserStatus.ACTIVE, now)) {
            throwVersionOrNotFound(userId);
        }
        UserAccount updated = getById(userId);
        auditService.record(
                AuditAction.USER_ENABLED,
                "USER_ACCOUNT",
                Long.toString(userId),
                AuditResult.SUCCESS,
                Map.of("status", updated.status().name()));
        return updated;
    }

    @Transactional
    public UserAccount disableUser(long userId, long expectedVersion) {
        repository.lockAdministratorRole();
        UserAccount existing = getById(userId);
        requireVersion(existing, expectedVersion);
        protectAdministrator(existing, UserStatus.DISABLED, existing.roles());
        if (existing.status() == UserStatus.DISABLED) return existing;
        Instant now = clock.instant();
        if (!repository.updateStatus(userId, expectedVersion, UserStatus.DISABLED, now)) {
            throwVersionOrNotFound(userId);
        }
        sessionInvalidator.invalidateByPrincipalName(existing.username().value());
        UserAccount updated = getById(userId);
        auditService.record(
                AuditAction.USER_DISABLED,
                "USER_ACCOUNT",
                Long.toString(userId),
                AuditResult.SUCCESS,
                Map.of("status", updated.status().name()));
        return updated;
    }

    @Transactional
    public UserAccount resetPassword(long userId, long expectedVersion, String newPassword) {
        PasswordPolicy.validate(newPassword);
        repository.lockAdministratorRole();
        UserAccount existing = getById(userId);
        Instant changedAt = clock.instant();
        String passwordHash = passwordEncoder.encode(newPassword);
        if (!repository.updatePassword(
                userId, expectedVersion, passwordHash, UserStatus.ACTIVE, changedAt)) {
            throwVersionOrNotFound(userId);
        }
        sessionInvalidator.invalidateByPrincipalName(existing.username().value());
        UserAccount updated = getById(userId);
        auditService.record(
                AuditAction.USER_PASSWORD_RESET,
                "USER_ACCOUNT",
                Long.toString(userId),
                AuditResult.SUCCESS,
                Map.of("credentialsChanged", "true"));
        return updated;
    }

    @Transactional
    public UserAccount replaceRoles(long userId, long expectedVersion, Set<RoleCode> roles) {
        repository.lockAdministratorRole();
        UserAccount existing = getById(userId);
        requireVersion(existing, expectedVersion);
        Set<RoleCode> requiredRoles = requireRoles(roles);
        protectAdministrator(existing, existing.status(), requiredRoles);
        if (existing.roles().equals(requiredRoles)) return existing;
        if (!repository.replaceRoles(userId, expectedVersion, requiredRoles, clock.instant())) {
            throwVersionOrNotFound(userId);
        }
        sessionInvalidator.invalidateByPrincipalName(existing.username().value());
        UserAccount updated = getById(userId);
        auditService.record(
                AuditAction.USER_ROLES_CHANGED,
                "USER_ACCOUNT",
                Long.toString(userId),
                AuditResult.SUCCESS,
                Map.of("roles", roleSummary(updated.roles())));
        return updated;
    }

    private UserAccount create(CreateUserCommand command, UserStatus initialStatus) {
        if (command == null) {
            throw new IllegalArgumentException("用户创建参数不能为空");
        }
        Username username = new Username(command.username());
        PasswordPolicy.validate(command.password());
        Set<RoleCode> roles = requireRoles(command.roles());
        String passwordHash = passwordEncoder.encode(command.password());
        try {
            UserProfile profile = command.profile() == null ? UserProfile.EMPTY : command.profile();
            if (profile.equals(UserProfile.EMPTY)) {
                return repository.create(username, passwordHash, initialStatus, roles, clock.instant());
            }
            return repository.createWithProfile(username, passwordHash, initialStatus, roles, profile, clock.instant());
        } catch (DuplicateKeyException exception) {
            throw new UsernameConflictException();
        }
    }

    private Set<RoleCode> requireRoles(Set<RoleCode> roles) {
        if (roles == null || roles.isEmpty() || roles.stream().anyMatch(role -> role == null)) {
            throw new IllegalArgumentException("至少需要分配一个有效角色");
        }
        return Set.copyOf(EnumSet.copyOf(roles));
    }

    private void throwVersionOrNotFound(long userId) {
        if (repository.findById(userId).isEmpty()) {
            throw new UserNotFoundException(userId);
        }
        throw new VersionConflictException(userId);
    }

    private String roleSummary(Set<RoleCode> roles) {
        return roles.stream().sorted().map(RoleCode::name).reduce((left, right) -> left + "," + right).orElse("");
    }
}
