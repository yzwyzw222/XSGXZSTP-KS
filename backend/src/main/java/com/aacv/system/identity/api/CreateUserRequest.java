package com.aacv.system.identity.api;

import com.aacv.system.identity.domain.RoleCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotEmpty Set<RoleCode> roles) {
}
