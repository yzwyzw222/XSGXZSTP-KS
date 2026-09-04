package com.aacv.system.crawl.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCrawlTaskRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull @Valid CrawlTaskParametersRequest parameters,
        @Min(0) long version) {
}
