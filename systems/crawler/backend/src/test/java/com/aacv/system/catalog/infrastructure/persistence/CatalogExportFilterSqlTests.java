package com.aacv.system.catalog.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.aacv.system.catalog.domain.CatalogQuery;
import com.aacv.system.export.domain.ExportFilter;
import com.aacv.system.source.domain.SourceType;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogExportFilterSqlTests {
    private Configuration configuration;

    @BeforeEach
    void loadActualMappers() throws Exception {
        configuration = new Configuration();
        for (String resource : new String[] {"mapper/catalog/CatalogMapper.xml", "mapper/export/ExportMapper.xml", "mapper/crawl/CrawlMapper.xml"}) {
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream);
                new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void catalogAndExportUseIdenticalFuzzyFiltersIncludingAliasesAndOverrides() {
        var query = new CatalogQuery("题名", "王", "研究院", 2026, "article", "CROSSREF", "Science", "图", 2, 20);
        var filter = new ExportFilter("题名", null, null, 2026, 2026, "article", null, null, null,
                "王", "研究院", "Science", "图", "CROSSREF");
        var catalog = catalog(query);
        var export = export(filter);
        assertEquals(where(catalog), where(export));
        assertTrue(where(export).contains("organization_name_evidence"));
        assertTrue(where(export).contains("title_override.field_value"));
        assertEquals(properties(catalog), properties(export));
    }

    @Test
    void selectedIdsTakePrecedenceOverDisplayNamesInBothQueries() {
        var query = new CatalogQuery(null, "同名作者", "同名机构", null, null, null, "同名期刊", "同名主题", 0, 20,
                1L, 2L, 3L, 4L);
        var filter = new ExportFilter(null, 1L, 2L, null, null, null, null, 3L, 4L,
                "同名作者", "同名机构", "同名期刊", "同名主题", null);
        assertEquals(where(catalog(query)), where(export(filter)));
        assertEquals(java.util.List.of("query.authorId", "query.organizationId", "query.venueId", "query.topicId"), properties(export(filter)));
    }

    @Test
    void legacyExportStillSupportsYearRangesAndSourceType() {
        var bound = export(new ExportFilter(null, null, null, 2020, 2026, null, SourceType.OPENALEX, null, null));
        assertTrue(properties(bound).containsAll(java.util.List.of("query.publicationYearFrom", "query.publicationYearTo", "query.sourceType")));
        assertTrue(where(bound).contains("source_value.source_type"));
    }

    @Test
    void batchActivityQueriesBindIdsAndSelectLatestRunPerTask() {
        var runs = configuration.getMappedStatement("com.aacv.system.crawl.infrastructure.persistence.CrawlMapper.findLatestRuns")
                .getBoundSql(Map.of("taskIds", java.util.List.of(1L, 2L)));
        assertTrue(runs.getSql().contains("MAX(id)"));
        assertTrue(runs.getSql().contains("GROUP BY task_id"));
        assertEquals(2, runs.getParameterMappings().size());
        var schedules = configuration.getMappedStatement("com.aacv.system.crawl.infrastructure.persistence.CrawlMapper.findSchedules")
                .getBoundSql(Map.of("taskIds", java.util.List.of(1L, 2L)));
        assertEquals(2, schedules.getParameterMappings().size());
    }

    private BoundSql catalog(CatalogQuery query) {
        var parameters = new HashMap<String, Object>();
        parameters.put("query", query);
        parameters.put("relatedId", null);
        parameters.put("relatedKind", null);
        return configuration.getMappedStatement("com.aacv.system.catalog.infrastructure.persistence.CatalogMapper.countAchievements").getBoundSql(parameters);
    }

    private BoundSql export(ExportFilter filter) {
        return configuration.getMappedStatement("com.aacv.system.export.infrastructure.persistence.ExportMapper.countRecords")
                .getBoundSql(Map.of("filter", filter));
    }

    private String where(BoundSql sql) { return sql.getSql().substring(sql.getSql().indexOf("WHERE")).replaceAll("\\s+", " ").trim(); }
    private java.util.List<String> properties(BoundSql sql) { return sql.getParameterMappings().stream().map(value -> value.getProperty()).toList(); }
}
