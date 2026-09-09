package com.aacv.system.crawl.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record CrawlTaskParametersRequest(
        LocalDate publicationDateFrom,
        LocalDate publicationDateTo,
        @Size(max = 200) String keyword,
        @Size(max = 50) List<@Size(max = 128) String> authorIds,
        @Size(max = 50) List<@Size(max = 128) String> institutionIds,
        @Size(max = 50) List<@Size(max = 255) String> dois,
        @Size(max = 50) List<@Size(max = 64) String> orcids,
        @Size(max = 50) List<@Size(max = 128) String> rorIds,
        Instant updatedFrom,
        Instant updatedUntil,
        @Min(1) @Max(5) int maxPages,
        @Min(1) @Max(500) int maxRecords) {

    public CrawlTaskParametersRequest {
        authorIds = authorIds == null ? List.of() : List.copyOf(authorIds);
        institutionIds = institutionIds == null ? List.of() : List.copyOf(institutionIds);
        dois = dois == null ? List.of() : List.copyOf(dois);
        orcids = orcids == null ? List.of() : List.copyOf(orcids);
        rorIds = rorIds == null ? List.of() : List.copyOf(rorIds);
    }

    public CrawlTaskParametersRequest(
            LocalDate publicationDateFrom,
            LocalDate publicationDateTo,
            String keyword,
            List<String> authorIds,
            List<String> institutionIds,
            int maxPages,
            int maxRecords) {
        this(publicationDateFrom, publicationDateTo, keyword, authorIds, institutionIds,
                List.of(), List.of(), List.of(), null, null, maxPages, maxRecords);
    }
}
