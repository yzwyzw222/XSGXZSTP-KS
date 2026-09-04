package com.aacv.system.analytics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aacv.system.source.domain.SourceType;
import org.junit.jupiter.api.Test;

class AnalyticsQueryTests {

    @Test
    void normalizesOptionalTypeAndPreservesControlledFilters() {
        AnalyticsQuery query = new AnalyticsQuery(2020, 2026, " article ", SourceType.OPENALEX, 3L, 4L);

        assertEquals("article", query.achievementType());
        assertEquals(SourceType.OPENALEX, query.sourceType());
        assertEquals(3L, query.organizationId());
    }

    @Test
    void treatsBlankTypeAsNoFilter() {
        assertNull(new AnalyticsQuery(null, null, "  ", null, null, null).achievementType());
    }

    @Test
    void rejectsInvalidYearRangeEntityIdsAndOversizedType() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsQuery(2027, 2026, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsQuery(null, null, null, null, 0L, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsQuery(null, null, "x".repeat(65), null, null, null));
    }
}
