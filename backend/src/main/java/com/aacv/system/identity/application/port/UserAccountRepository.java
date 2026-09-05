package com.aacv.system.identity.application.port;

import com.aacv.system.identity.domain.UserStatistics;

import com.aacv.system.identity.domain.UserProfile;

import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserAccount;
import com.aacv.system.identity.domain.UserStatus;
import com.aacv.system.identity.domain.Username;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import com.aacv.system.shared.domain.PageResult;

public interface UserAccountRepository {

    long countUsers();

    UserStatistics statistics();

    void lockAdministratorRole();

    long countActiveAdministrators();

    UserAccount createWithProfile(Username username, String passwordHash, UserStatus status, Set<RoleCode> roles,
            UserProfile profile, Instant now);

    boolean updateUser(long userId, long expectedVersion, UserProfile profile,
            UserStatus status, Set<RoleCode> roles, boolean rolesChanged, boolean securityChanged, Instant now);

    Optional<UserAccount> findById(long userId);

    Optional<UserAccount> findByUsername(Username username);

    PageResult<UserAccount> findPage(int page, int size);

    UserAccount create(Username username, String passwordHash, UserStatus status, Set<RoleCode> roles, Instant now);

    boolean updateStatus(long userId, long expectedVersion, UserStatus status, Instant now);

    boolean updatePassword(
            long userId, long expectedVersion, String passwordHash, UserStatus status, Instant credentialsChangedAt);

    boolean replaceRoles(long userId, long expectedVersion, Set<RoleCode> roles, Instant now);

    boolean acquireInitialAdminLock(int timeoutSeconds);

    void releaseInitialAdminLock();
}
