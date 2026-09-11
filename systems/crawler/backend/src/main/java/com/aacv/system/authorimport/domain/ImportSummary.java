package com.aacv.system.authorimport.domain;

import java.time.Instant;

public record ImportSummary(long id, long authorId, String scholarName, String fileName, String sheetName,
        String importMode, int totalRows, int importedCount, int linkedCount, int skippedCount, Instant createdAt) { }
