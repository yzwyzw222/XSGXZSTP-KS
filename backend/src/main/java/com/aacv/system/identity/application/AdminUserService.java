package com.aacv.system.identity.application;

import com.aacv.system.identity.domain.UserStatistics;

import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserAccount;
import com.aacv.system.shared.domain.PageResult;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AdminUserService {

    private final UserAccountService userAccountService;

    public AdminUserService(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PreAuthorize("hasAuthority('USER_LIST')")
    public PageResult<UserAccount> findPage(int page, int size) {
        return userAccountService.findPage(page, size);
    }

    @PreAuthorize("hasAuthority('USER_LIST')")
    public UserStatistics statistics() {
        return userAccountService.statistics();
    }

    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public UserAccount updateUser(long userId, UpdateUserCommand command) {
        return userAccountService.updateUser(userId, command);
    }

    @PreAuthorize("hasAuthority('USER_CREATE')")
    public UserAccount createUser(CreateUserCommand command) {
        return userAccountService.createUser(command);
    }

    @PreAuthorize("hasAuthority('USER_ENABLE')")
    public UserAccount enableUser(long userId, long expectedVersion) {
        return userAccountService.enableUser(userId, expectedVersion);
    }

    @PreAuthorize("hasAuthority('USER_DISABLE')")
    public UserAccount disableUser(long userId, long expectedVersion) {
        return userAccountService.disableUser(userId, expectedVersion);
    }

    @PreAuthorize("hasAuthority('USER_PASSWORD_RESET')")
    public UserAccount resetPassword(long userId, long expectedVersion, String newPassword) {
        return userAccountService.resetPassword(userId, expectedVersion, newPassword);
    }

    @PreAuthorize("hasAuthority('USER_ROLE_CHANGE')")
    public UserAccount replaceRoles(long userId, long expectedVersion, Set<RoleCode> roles) {
        return userAccountService.replaceRoles(userId, expectedVersion, roles);
    }
}
