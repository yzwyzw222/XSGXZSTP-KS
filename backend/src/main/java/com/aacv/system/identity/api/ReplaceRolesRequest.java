package com.aacv.system.identity.api;

import com.aacv.system.identity.domain.RoleCode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;

public record ReplaceRolesRequest(
        @PositiveOrZero long version,
        @NotEmpty Set<RoleCode> roles) {
}
