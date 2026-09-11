package com.aacv.system.export.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExportFilterTests {

    @Test
    void legacyJsonAndNewTextFiltersRoundTrip() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var legacy = mapper.readValue("{\"authorId\":42,\"publicationYearFrom\":2020,\"publicationYearTo\":2026}", ExportFilter.class);
        assertEquals(42L, legacy.authorId());
        assertNull(legacy.author());
        var fuzzy = new ExportFilter(null, null, null, null, null, null, null, null, null,
                " 王 ", " 研究院 ", "期刊", "主题", "OPENALEX");
        assertEquals("王", fuzzy.author());
        assertEquals(fuzzy, mapper.readValue(mapper.writeValueAsString(fuzzy), ExportFilter.class));
        assertThrows(IllegalArgumentException.class, () -> new ExportFilter(null, null, null, null, null, null,
                null, null, null, "名".repeat(201), null, null, null, null));
    }

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
