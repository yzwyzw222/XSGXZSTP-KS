package com.aacv.system.identity.application;

import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserProfile;
import com.aacv.system.identity.domain.UserStatus;
import java.util.Set;

public record UpdateUserCommand(long version, UserProfile profile, Set<RoleCode> roles, UserStatus status) {
}
