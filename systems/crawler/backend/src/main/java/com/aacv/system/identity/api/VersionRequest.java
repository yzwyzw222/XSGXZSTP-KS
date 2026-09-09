package com.aacv.system.identity.api;

import jakarta.validation.constraints.PositiveOrZero;

public record VersionRequest(@PositiveOrZero long version) {
}
