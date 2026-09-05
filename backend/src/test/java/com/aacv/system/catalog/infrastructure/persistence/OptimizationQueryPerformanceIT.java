package com.aacv.system.catalog.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.analytics.domain.AnalyticsQuery;
import com.aacv.system.catalog.domain.CatalogQuery;
import com.aacv.system.source.domain.SourceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(properties = "aacv.operations.alerts-enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OptimizationQueryPerformanceIT {
    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42").withDatabaseName("aacv_query_profile");
    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private SqlSessionFactory sessions;

    @Test
    void profileCombinedFiltersDeepPaginationAndAnalytics() throws Exception {
        String label = System.getProperty("aacv.profile.label", "current");
        int sampleCount = Integer.getInteger("aacv.profile.samples", 5);
        boolean explainOnly = Boolean.getBoolean("aacv.profile.explainOnly");
        if (!label.matches("[a-z-]{1,20}") || sampleCount < 3 || sampleCount > 100) {
            throw new IllegalArgumentException("查询采样参数无效");
        }
        // 容量数据只写入本用例新建的Testcontainers数据库。
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("sql/optimization-query-sample.sql"));
            return null;
        });
        assertEquals(100000, jdbc.queryForObject("SELECT COUNT(*) FROM achievement", Integer.class));
        CatalogQuery combined = new CatalogQuery("Study", "Author 10#", "Former Institute 10#", 2024,
                "article", "OPENALEX", "Journal 10#", "Topic 10#", 0, 20);
        CatalogQuery all = new CatalogQuery(null, null, null, null, null, null, null, null, 4999, 20);
        AnalyticsQuery analytics = new AnalyticsQuery(2024, 2024, "article", SourceType.OPENALEX, 10L, 10L);
        String catalog = "com.aacv.system.catalog.infrastructure.persistence.CatalogMapper.";
        String aggregate = "com.aacv.system.analytics.infrastructure.persistence.AnalyticsMapper.";
        List<Scenario> scenarios = List.of(
                new Scenario("catalog-combined-count", catalog + "countAchievements", catalogParameters(combined, 0)),
                new Scenario("catalog-combined-page", catalog + "findAchievementPage", catalogParameters(combined, 0)),
                new Scenario("catalog-deep-page", catalog + "findAchievementPage", catalogParameters(all, 99980)),
                new Scenario("analytics-filtered", aggregate + "overview", Map.of("query", analytics)),
                new Scenario("analytics-coverage", aggregate + "coverage", Map.of("query", new AnalyticsQuery(null, null, null, null, null, null))),
                new Scenario("analytics-collaboration", aggregate + "organizationCollaboration", Map.of("query", analytics, "limit", 20)));
        List<Map<String, Object>> results = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scenario", scenario.name());
            try (Connection connection = dataSource.getConnection()) {
                result.put("explain", execute(connection, scenario, true, "EXPLAIN FORMAT=TREE "));
                if (!explainOnly) {
                    result.put("explainAnalyze", execute(connection, scenario, true));
                    execute(connection, scenario, false);
                    List<Double> durations = new ArrayList<>();
                    for (int index = 0; index < sampleCount; index++) {
                        long started = System.nanoTime();
                        execute(connection, scenario, false);
                        durations.add((System.nanoTime() - started) / 1_000_000.0);
                    }
                    List<Double> sorted = durations.stream().sorted().toList();
                    result.put("milliseconds", durations);
                    result.put("p95Milliseconds", sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1));
                }
            } catch (Exception exception) {
                failures.add(scenario.name() + ": " + exception.getClass().getSimpleName());
                result.put("error", exception.getClass().getSimpleName());
            }
            results.add(result);
        }
        Map<String, Object> report = Map.of("recordedAt", Instant.now().toString(), "achievementCount", 100000,
                "mysqlVersion", jdbc.queryForObject("SELECT VERSION()", String.class), "samples", sampleCount,
                "concurrency", 1, "scope", "隔离合成数据的真实MySQL SQL测量，不包含HTTP和图查询", "results", results);
        Files.writeString(Path.of("target", "optimization-query-" + label + ".json"),
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
        assertTrue(failures.isEmpty(), String.join(", ", failures));
    }

    private Map<String, Object> catalogParameters(CatalogQuery query, long offset) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("query", query);
        parameters.put("relatedKind", null);
        parameters.put("relatedId", null);
        parameters.put("offset", offset);
        parameters.put("size", 20);
        return parameters;
    }

    private List<String> execute(Connection connection, Scenario scenario, boolean explain) throws Exception {
        return execute(connection, scenario, explain, explain ? "EXPLAIN ANALYZE " : "");
    }

    private List<String> execute(Connection connection, Scenario scenario, boolean explain, String prefix) throws Exception {
        var statement = sessions.getConfiguration().getMappedStatement(scenario.statementId());
        var bound = statement.getBoundSql(scenario.parameters());
        try (var prepared = connection.prepareStatement(prefix + bound.getSql())) {
            prepared.setQueryTimeout(30);
            new DefaultParameterHandler(statement, scenario.parameters(), bound).setParameters(prepared);
            try (var rows = prepared.executeQuery()) {
                List<String> values = new ArrayList<>();
                int rowCount = 0;
                while (rows.next()) {
                    rowCount++;
                    if (explain) values.add(rows.getString(1));
                    if (!explain && scenario.name().endsWith("-count")) assertTrue(rows.getLong(1) > 0);
                }
                assertTrue(rowCount > 0, "采样场景应返回匹配数据：" + scenario.name());
                return values;
            }
        }
    }

    private record Scenario(String name, String statementId, Map<String, Object> parameters) { }
}
