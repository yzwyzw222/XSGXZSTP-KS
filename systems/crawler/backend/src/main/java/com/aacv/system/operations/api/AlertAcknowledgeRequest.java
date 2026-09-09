package com.aacv.system.operations.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlertAcknowledgeRequest(
        @NotBlank @Size(max = 1000) String reason,
        @Min(0) long version) {
}
