package com.aacv.system.identity.application;

import com.aacv.system.identity.domain.RoleCode;
import java.util.Set;

public record CreateUserCommand(String username, String password, Set<RoleCode> roles) {
}
