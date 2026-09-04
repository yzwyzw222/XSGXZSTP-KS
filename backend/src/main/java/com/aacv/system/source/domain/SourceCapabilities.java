package com.aacv.system.source.domain;

import java.util.Set;

public record SourceCapabilities(
        Set<SourceFilter> supportedFilters,
        IncrementalMode incrementalMode,
        int maxPageSize,
        boolean cursorPaging) {

    public SourceCapabilities {
        supportedFilters = supportedFilters == null ? Set.of() : Set.copyOf(supportedFilters);
        if (incrementalMode == null) {
            throw new IllegalArgumentException("必须声明增量模式");
        }
        if (maxPageSize < 1 || maxPageSize > 200) {
            throw new IllegalArgumentException("来源最大分页大小无效");
        }
    }

    public enum SourceFilter {
        PUBLICATION_DATE,
        KEYWORD,
        AUTHOR_ID,
        INSTITUTION_ID,
        DOI,
        ORCID,
        ROR,
        UPDATED_AT
    }

    public enum IncrementalMode {
        ROLLING_PUBLICATION_DATE_WINDOW,
        CLOSED_INDEX_DATE_WINDOW
    }
}
