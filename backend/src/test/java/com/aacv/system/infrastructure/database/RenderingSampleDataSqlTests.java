package com.aacv.system.infrastructure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class RenderingSampleDataSqlTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_rendering_sample_test");

    @Test
    void insertsCompleteRenderingSampleAndRemainsIdempotent() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertEquals(14, flyway.migrate().migrationsExecuted);

        try (Connection connection = MYSQL.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO sys_user (id, username, password_hash, status)
                    VALUES (9001, 'rendering-sample-admin', '$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuu', 'ACTIVE')
                    """);
            statement.executeUpdate("INSERT INTO sys_user_role (user_id, role_id) VALUES (9001, 1)");

            executeSampleScript(connection, statement);
            Map<String, Long> firstCounts = sampleCounts(statement);
            assertExpectedCounts(firstCounts);
            assertEquals(2, scalar(statement, """
                    SELECT COUNT(*)
                    FROM (
                        SELECT source_record.achievement_id
                        FROM achievement_source source_record
                        JOIN achievement ON achievement.id = source_record.achievement_id
                        WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                        GROUP BY source_record.achievement_id
                        HAVING COUNT(DISTINCT source_record.source_id) = 2
                    ) dual_source
                    """));
            assertEquals(2, scalar(statement, """
                    SELECT desired_version
                    FROM graph_projection_state projection_state
                    JOIN achievement ON achievement.id = projection_state.achievement_id
                    WHERE achievement.doi_normalized = '10.9999/aacv-demo.012'
                    """));

            statement.executeUpdate("""
                    UPDATE data_source
                    SET requests_per_second = 7,
                        compliance_note = 'existing-source-configuration'
                    WHERE source_code = 'OPENALEX'
                    """);
            executeSampleScript(connection, statement);
            assertEquals(firstCounts, sampleCounts(statement));
            assertEquals(7, scalar(statement,
                    "SELECT requests_per_second FROM data_source WHERE source_code = 'OPENALEX'"));
            assertEquals(1, scalar(statement, """
                    SELECT COUNT(*) FROM data_source
                    WHERE source_code = 'OPENALEX'
                      AND compliance_note = 'existing-source-configuration'
                    """));
        }
    }

    private void executeSampleScript(Connection connection, Statement statement) throws Exception {
        statement.execute("SET @aacv_demo_actor_id = 9001");
        ScriptUtils.executeSqlScript(connection, new FileSystemResource(findSampleSql()));
    }

    private Path findSampleSql() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("tools/development/rendering-sample-data.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("未找到页面测试数据 SQL 文件");
    }

    private Map<String, Long> sampleCounts(Statement statement) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("tasks", scalar(statement,
                "SELECT COUNT(*) FROM crawl_task WHERE task_name LIKE '[页面测试]%'"));
        counts.put("runs", scalar(statement,
                "SELECT COUNT(*) FROM crawl_run WHERE run_number LIKE '10000000-0000-4000-8000-00000000000%'"));
        counts.put("rawRecords", scalar(statement,
                "SELECT COUNT(*) FROM raw_record WHERE external_record_id LIKE 'AACV-DEMO-%'"));
        counts.put("achievements", scalar(statement,
                "SELECT COUNT(*) FROM achievement WHERE doi_normalized LIKE '10.9999/aacv-demo.%'"));
        counts.put("paperDetails", scalar(statement, """
                SELECT COUNT(*) FROM paper_detail detail_value
                JOIN achievement ON achievement.id = detail_value.achievement_id
                WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                """));
        counts.put("achievementSources", scalar(statement, """
                SELECT COUNT(*) FROM achievement_source source_record
                JOIN achievement ON achievement.id = source_record.achievement_id
                WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                """));
        counts.put("authors", scalar(statement,
                "SELECT COUNT(*) FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id LIKE 'https://openalex.org/AACVDEMOA%'"));
        counts.put("organizations", scalar(statement,
                "SELECT COUNT(*) FROM organization WHERE openalex_id LIKE 'https://openalex.org/AACVDEMOI%'"));
        counts.put("venues", scalar(statement,
                "SELECT COUNT(*) FROM venue WHERE openalex_id LIKE 'https://openalex.org/AACVDEMOV%'"));
        counts.put("topics", scalar(statement,
                "SELECT COUNT(*) FROM topic WHERE openalex_id LIKE 'https://openalex.org/AACVDEMOT%'"));
        counts.put("subjects", scalar(statement,
                "SELECT COUNT(*) FROM subject WHERE external_id LIKE 'AACV-DEMO-SUBJECT-%'"));
        counts.put("authorships", scalar(statement, """
                SELECT COUNT(*) FROM achievement_author authorship
                JOIN achievement ON achievement.id = authorship.achievement_id
                WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                """));
        counts.put("affiliations", scalar(statement, """
                SELECT COUNT(*) FROM authorship_organization affiliation
                JOIN achievement ON achievement.id = affiliation.achievement_id
                WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                """));
        counts.put("legacyTopics", scalar(statement, """
                SELECT COUNT(*) FROM achievement_topic topic_relation
                JOIN achievement ON achievement.id = topic_relation.achievement_id
                WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                """));
        counts.put("subjectsRelations", scalar(statement, """
                SELECT COUNT(*) FROM achievement_subject subject_relation
                JOIN achievement ON achievement.id = subject_relation.achievement_id
                WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                """));
        counts.put("references", scalar(statement, """
                SELECT COUNT(*) FROM achievement_reference reference_value
                JOIN achievement ON achievement.id = reference_value.citing_achievement_id
                WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                """));
        counts.put("provenance", scalar(statement, """
                SELECT COUNT(*) FROM entity_field_provenance provenance
                JOIN achievement ON achievement.id = provenance.entity_id
                WHERE provenance.entity_type = 'ACHIEVEMENT'
                  AND achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                """));
        counts.put("failures", scalar(statement,
                "SELECT COUNT(*) FROM crawl_failure WHERE safe_message LIKE '[页面测试]%'"));
        counts.put("qualityMetrics", scalar(statement, """
                SELECT COUNT(*) FROM quality_metric_snapshot metric
                WHERE metric.run_id IN (
                    SELECT id FROM crawl_run
                    WHERE run_number LIKE '10000000-0000-4000-8000-00000000000%'
                )
                """));
        counts.put("qualitySamples", scalar(statement, """
                SELECT COUNT(*) FROM quality_issue_sample
                WHERE external_record_id LIKE 'AACV-DEMO-%'
                """));
        counts.put("candidates", scalar(statement,
                "SELECT COUNT(*) FROM duplicate_candidate WHERE evidence_hash IN (SHA2('aacv-demo-candidate-author', 256), SHA2('aacv-demo-candidate-organization', 256))"));
        counts.put("projectionStates", scalar(statement, """
                SELECT COUNT(*) FROM graph_projection_state projection_state
                JOIN achievement ON achievement.id = projection_state.achievement_id
                WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
                """));
        counts.put("outboxEvents", scalar(statement,
                "SELECT COUNT(*) FROM graph_outbox_event WHERE event_id LIKE '2%'"));
        counts.put("deadLetters", scalar(statement,
                "SELECT COUNT(*) FROM graph_sync_dead_letter WHERE event_id = '2fffffff-0000-4000-8000-000000000012'"));
        counts.put("maintenanceRuns", scalar(statement,
                "SELECT COUNT(*) FROM graph_maintenance_run WHERE scanned_count IN (8, 12) AND (repaired_count = 1 OR repaired_count = 12)"));
        counts.put("alerts", scalar(statement,
                "SELECT COUNT(*) FROM alert_event WHERE summary LIKE '[页面测试]%'"));
        counts.put("audits", scalar(statement,
                "SELECT COUNT(*) FROM audit_log WHERE trace_id LIKE 'aacv-demo-trace-%'"));
        return counts;
    }

    private void assertExpectedCounts(Map<String, Long> counts) {
        Map<String, Long> expected = Map.ofEntries(
                Map.entry("tasks", 2L),
                Map.entry("runs", 2L),
                Map.entry("rawRecords", 15L),
                Map.entry("achievements", 12L),
                Map.entry("paperDetails", 12L),
                Map.entry("achievementSources", 14L),
                Map.entry("authors", 8L),
                Map.entry("organizations", 4L),
                Map.entry("venues", 3L),
                Map.entry("topics", 5L),
                Map.entry("subjects", 5L),
                Map.entry("authorships", 25L),
                Map.entry("affiliations", 25L),
                Map.entry("legacyTopics", 24L),
                Map.entry("subjectsRelations", 24L),
                Map.entry("references", 5L),
                Map.entry("provenance", 36L),
                Map.entry("failures", 2L),
                Map.entry("qualityMetrics", 9L),
                Map.entry("qualitySamples", 3L),
                Map.entry("candidates", 2L),
                Map.entry("projectionStates", 12L),
                Map.entry("outboxEvents", 13L),
                Map.entry("deadLetters", 1L),
                Map.entry("maintenanceRuns", 2L),
                Map.entry("alerts", 3L),
                Map.entry("audits", 4L));
        assertEquals(expected, counts);
        assertTrue(counts.values().stream().allMatch(value -> value > 0));
    }

    private long scalar(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }
}
