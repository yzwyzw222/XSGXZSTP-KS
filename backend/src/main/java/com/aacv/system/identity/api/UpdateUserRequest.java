package com.aacv.system.identity.api;

import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserProfile;
import com.aacv.system.identity.domain.UserStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateUserRequest(
        @NotNull @Min(0) Long version, @NotEmpty Set<@NotNull RoleCode> roles, @NotNull UserStatus status,
        String realName, String email, String phone, String organization, String department, String remark) {

    public UserProfile profile() {
        return new UserProfile(realName, email, phone, organization, department, remark);
    }
}
