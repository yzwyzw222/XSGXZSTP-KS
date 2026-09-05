package com.aacv.system.identity.api;

import jakarta.validation.constraints.NotNull;

import com.aacv.system.identity.domain.UserProfile;

import com.aacv.system.identity.domain.RoleCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotEmpty Set<@NotNull RoleCode> roles,
        String realName, String email, String phone, String organization, String department, String remark) {

    public UserProfile profile() {
        return new UserProfile(realName, email, phone, organization, department, remark);
    }
}
