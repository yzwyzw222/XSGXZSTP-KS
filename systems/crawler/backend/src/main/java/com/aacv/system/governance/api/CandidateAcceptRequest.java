package com.aacv.system.governance.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CandidateAcceptRequest(
        @Min(1) long canonicalEntityId,
        @NotBlank @Size(max = 1000) String reason,
        @Min(0) long version) {
}
