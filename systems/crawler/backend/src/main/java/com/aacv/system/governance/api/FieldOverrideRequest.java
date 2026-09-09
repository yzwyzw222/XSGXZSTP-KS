package com.aacv.system.governance.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FieldOverrideRequest(
        @NotBlank @Size(max = 64) String fieldName,
        @NotNull Object value,
        @NotBlank @Size(max = 1000) String reason,
        @Min(0) long version) {
}
