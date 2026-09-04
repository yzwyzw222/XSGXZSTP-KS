package com.aacv.system.export.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExportFilterTests {

    @Test
    void normalizesOptionalText() {
        ExportFilter filter = new ExportFilter("  paper  ", null, null, null, null, "  article  ", null, null, null);

        assertEquals("paper", filter.title());
        assertEquals("article", filter.achievementType());
        assertNull(new ExportFilter(" ", null, null, null, null, null, null, null, null).title());
    }

    @Test
    void rejectsInvalidYearsAndIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExportFilter(null, null, null, 2026, 2025, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ExportFilter(null, 0L, null, null, null, null, null, null, null));
    }
}
