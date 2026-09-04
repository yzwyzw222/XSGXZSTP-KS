package com.aacv.system.identity.infrastructure.persistence;

import com.aacv.system.identity.application.port.UserAccountRepository;
import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserAccount;
import com.aacv.system.identity.domain.UserStatus;
import com.aacv.system.identity.domain.Username;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;
import com.aacv.system.shared.domain.PageResult;

@Repository
public class MyBatisUserAccountRepository implements UserAccountRepository {

    private final UserAccountMapper mapper;

    public MyBatisUserAccountRepository(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long countUsers() {
        return mapper.countUsers();
    }

    @Override
    public Optional<UserAccount> findById(long userId) {
        return Optional.ofNullable(mapper.findById(userId)).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findByUsername(Username username) {
        return Optional.ofNullable(mapper.findByUsername(username.value())).map(this::toDomain);
    }

    @Override
    public PageResult<UserAccount> findPage(int page, int size) {
        long total = mapper.countUsers();
        long offset = Math.multiplyExact((long) page, size);
        List<UserAccountRow> rows = mapper.findPage(offset, size);
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), page, size, total);
        }

        List<Long> userIds = rows.stream().map(UserAccountRow::getId).toList();
        Map<Long, EnumSet<RoleCode>> rolesByUserId = new HashMap<>();
        mapper.findRoleCodesByUserIds(userIds).forEach(row -> rolesByUserId
                .computeIfAbsent(row.userId(), ignored -> EnumSet.noneOf(RoleCode.class))
                .add(RoleCode.valueOf(row.roleCode())));
        List<UserAccount> accounts = rows.stream()
                .map(row -> toDomain(row, rolesByUserId.getOrDefault(row.getId(), EnumSet.noneOf(RoleCode.class))))
                .toList();
        return PageResult.of(accounts, page, size, total);
    }

    @Override
    public UserAccount create(
            Username username, String passwordHash, UserStatus status, Set<RoleCode> roles, Instant now) {
        UserAccountRow row = new UserAccountRow();
        row.setUsername(username.value());
        row.setPasswordHash(passwordHash);
        row.setStatus(status.name());
        row.setCredentialsChangedAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insertUser(row);
        roles.stream().sorted().forEach(role -> mapper.insertUserRole(row.getId(), role.name()));
        return findById(row.getId()).orElseThrow(() -> new IllegalStateException("新建用户无法重新读取"));
    }

    @Override
    public boolean updateStatus(long userId, long expectedVersion, UserStatus status, Instant now) {
        return mapper.updateStatus(userId, expectedVersion, status.name(), now) == 1;
    }

    @Override
    public boolean updatePassword(
            long userId,
            long expectedVersion,
            String passwordHash,
            UserStatus status,
            Instant credentialsChangedAt) {
        return mapper.updatePassword(
                        userId, expectedVersion, passwordHash, status.name(), credentialsChangedAt)
                == 1;
    }

    @Override
    public boolean replaceRoles(long userId, long expectedVersion, Set<RoleCode> roles, Instant now) {
        if (mapper.incrementVersion(userId, expectedVersion, now) != 1) {
            return false;
        }
        mapper.deleteUserRoles(userId);
        roles.stream().sorted().forEach(role -> mapper.insertUserRole(userId, role.name()));
        return true;
    }

    @Override
    public boolean acquireInitialAdminLock(int timeoutSeconds) {
        return Integer.valueOf(1).equals(mapper.acquireInitialAdminLock(timeoutSeconds));
    }

    @Override
    public void releaseInitialAdminLock() {
        mapper.releaseInitialAdminLock();
    }

    private UserAccount toDomain(UserAccountRow row) {
        EnumSet<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
        mapper.findRoleCodesByUserId(row.getId()).stream().map(RoleCode::valueOf).forEach(roles::add);
        return toDomain(row, roles);
    }

    private UserAccount toDomain(UserAccountRow row, Set<RoleCode> roles) {
        return new UserAccount(
                row.getId(),
                new Username(row.getUsername()),
                row.getPasswordHash(),
                UserStatus.valueOf(row.getStatus()),
                row.getVersion(),
                row.getCredentialsChangedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                roles);
    }
}
