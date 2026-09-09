package com.aacv.system.crawl.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DailyScheduleRequest(
        @NotBlank @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String localTime,
        @NotBlank @Size(max = 64) String timeZone,
        @Min(0) Long version) {
}
