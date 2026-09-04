package com.aacv.system.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @PositiveOrZero long version,
        @NotBlank @Size(min = 12, max = 128) String newPassword) {
}
