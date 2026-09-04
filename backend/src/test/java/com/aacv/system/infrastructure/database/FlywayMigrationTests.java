package com.aacv.system.infrastructure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class FlywayMigrationTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_migration_test");

    @Container
    static final MySQLContainer UPGRADE_MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_upgrade_test");

    @Container
    static final MySQLContainer STAGE_THREE_MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_stage_three_upgrade_test");

    @Container
    static final MySQLContainer STAGE_FOUR_MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_stage_four_upgrade_test");

    @Test
    void migratesEmptySchemaAndValidatesStageFiveTablesAndConstraints() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertEquals(11, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);

        try (Connection connection = MYSQL.createConnection("");
                Statement statement = connection.createStatement()) {
            Set<String> tables = tableNames(statement);
            assertTrue(tables.containsAll(Set.of(
                    "SPRING_SESSION",
                    "audit_log",
                    "data_source",
                    "crawl_task",
                    "crawl_run",
                    "crawl_checkpoint",
                    "crawl_failure",
                    "raw_record",
                    "achievement",
                    "author",
                    "organization",
                    "venue",
                    "topic",
                    "organization_external_id",
                    "venue_external_id",
                    "subject",
                    "achievement_subject",
                    "entity_field_provenance",
                    "duplicate_candidate",
                    "merge_decision",
                    "data_revision",
                    "manual_field_override",
                    "canonical_entity_link",
                    "quality_metric_snapshot",
                    "graph_projection_state",
                    "graph_outbox_event",
                    "graph_sync_dead_letter",
                    "graph_maintenance_run",
                    "export_task",
                    "alert_event",
                    "BATCH_JOB_INSTANCE",
                    "BATCH_JOB_EXECUTION",
                    "QRTZ_JOB_DETAILS",
                    "QRTZ_TRIGGERS")));
            assertEquals(Set.of("ADMIN", "DATA_OPERATOR", "RESEARCHER"), roleCodes(statement));
            assertEquals(1, statement.executeUpdate("""
                    INSERT INTO data_source (
                        source_code, source_type, base_url, adapter_code, compliance_note
                    ) VALUES (
                        'CROSSREF', 'CROSSREF', 'https://api.crossref.org',
                        'CROSSREF_REST_V1', '阶段4迁移约束测试'
                    )
                    """));
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO data_source (
                        source_code, source_type, base_url, adapter_code, compliance_note
                    ) VALUES (
                        'INVALID', 'CROSSREF', 'https://example.invalid',
                        'CROSSREF_REST_V1', '必须被固定身份约束拒绝'
                    )
                    """));

            assertEquals(0, scalarCount(statement, "SELECT COUNT(*) FROM graph_outbox_event"));
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO graph_outbox_event (
                        event_id, achievement_id, desired_version, status
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000001', 100, 0, 'PENDING'
                    )
                    """));

            statement.executeUpdate("""
                    INSERT INTO achievement (
                        id, title_normalized, match_fingerprint, achievement_type,
                        first_seen_at, last_seen_at
                    ) VALUES
                        (100, 'candidate one', REPEAT('1', 64), 'article', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                        (101, 'candidate two', REPEAT('2', 64), 'article', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO duplicate_candidate (
                        entity_type, left_entity_id, right_entity_id, match_basis,
                        source_id, rule_version, evidence_json, evidence_hash
                    ) VALUES (
                        'ACHIEVEMENT', 100, 101, 'FINGERPRINT', 1, 1,
                        JSON_OBJECT('rule', 'exact'), REPEAT('3', 64)
                    )
                    """);
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO duplicate_candidate (
                        entity_type, left_entity_id, right_entity_id, match_basis,
                        source_id, rule_version, evidence_json, evidence_hash
                    ) VALUES (
                        'ACHIEVEMENT', 100, 101, 'FINGERPRINT', 1, 1,
                        JSON_OBJECT('rule', 'duplicate'), REPEAT('4', 64)
                    )
                    """));

            connection.setAutoCommit(false);
            statement.executeUpdate("""
                    INSERT INTO subject (source_id, external_id, display_name)
                    VALUES (1, 'rollback-subject', '必须回滚')
                    """);
            connection.rollback();
            assertEquals(0, scalarCount(
                    statement, "SELECT COUNT(*) FROM subject WHERE external_id = 'rollback-subject'"));
            connection.setAutoCommit(true);
        }
    }

    @Test
    void upgradesExistingStageTwoSchemaWithoutChangingAppliedMigrations() throws Exception {
        Flyway stageTwo = Flyway.configure()
                .dataSource(UPGRADE_MYSQL.getJdbcUrl(), UPGRADE_MYSQL.getUsername(), UPGRADE_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("3")
                .load();
        assertEquals(3, stageTwo.migrate().migrationsExecuted);

        Flyway stageThree = Flyway.configure()
                .dataSource(UPGRADE_MYSQL.getJdbcUrl(), UPGRADE_MYSQL.getUsername(), UPGRADE_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertEquals(8, stageThree.migrate().migrationsExecuted);
        assertTrue(stageThree.validateWithResult().validationSuccessful);
    }

    @Test
    void upgradesStageThreeCatalogWithoutLosingExistingIdentifiersOrSubjects() throws Exception {
        Flyway stageThree = Flyway.configure()
                .dataSource(
                        STAGE_THREE_MYSQL.getJdbcUrl(),
                        STAGE_THREE_MYSQL.getUsername(),
                        STAGE_THREE_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("7")
                .load();
        assertEquals(7, stageThree.migrate().migrationsExecuted);

        try (Connection connection = STAGE_THREE_MYSQL.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO data_source (
                        id, source_code, source_type, base_url, adapter_code, compliance_note
                    ) VALUES (
                        1, 'OPENALEX', 'OPENALEX', 'https://api.openalex.org',
                        'OPENALEX_REST_V1', '阶段3升级数据'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO venue (id, openalex_id, display_name, issn_l)
                    VALUES (1, 'https://openalex.org/S1', 'Stage Three Venue', '1234-567X')
                    """);
            statement.executeUpdate("""
                    INSERT INTO organization (id, openalex_id, display_name)
                    VALUES (1, 'https://openalex.org/I1', 'Stage Three Organization')
                    """);
            statement.executeUpdate("""
                    INSERT INTO topic (id, openalex_id, display_name, subfield_name, field_name)
                    VALUES (1, 'https://openalex.org/T1', 'Topic', 'Subfield', 'Field')
                    """);
            statement.executeUpdate("""
                    INSERT INTO achievement (
                        id, title_normalized, match_fingerprint, achievement_type,
                        primary_venue_id, first_seen_at, last_seen_at
                    ) VALUES (
                        1, 'stage three work', REPEAT('a', 64), 'article', 1,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO achievement_topic (achievement_id, topic_id, topic_position)
                    VALUES (1, 1, 1)
                    """);
        }

        Flyway stageFour = Flyway.configure()
                .dataSource(
                        STAGE_THREE_MYSQL.getJdbcUrl(),
                        STAGE_THREE_MYSQL.getUsername(),
                        STAGE_THREE_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertEquals(4, stageFour.migrate().migrationsExecuted);
        assertTrue(stageFour.validateWithResult().validationSuccessful);
        try (Connection connection = STAGE_THREE_MYSQL.createConnection("");
                Statement statement = connection.createStatement()) {
            assertEquals(1, scalarCount(statement, "SELECT COUNT(*) FROM achievement WHERE id = 1"));
            assertEquals(1, scalarCount(statement, "SELECT COUNT(*) FROM organization_external_id WHERE id_type = 'OPENALEX'"));
            assertEquals(2, scalarCount(statement, "SELECT COUNT(*) FROM venue_external_id"));
            assertEquals(1, scalarCount(statement, "SELECT COUNT(*) FROM achievement_subject WHERE achievement_id = 1"));
        }
    }

    @Test
    void upgradesStageFourSchemaWithoutLosingAchievementsOrCreatingImplicitEvents() throws Exception {
        Flyway stageFour = Flyway.configure()
                .dataSource(
                        STAGE_FOUR_MYSQL.getJdbcUrl(),
                        STAGE_FOUR_MYSQL.getUsername(),
                        STAGE_FOUR_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("8")
                .load();
        assertEquals(8, stageFour.migrate().migrationsExecuted);

        try (Connection connection = STAGE_FOUR_MYSQL.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO achievement (
                        id, title_normalized, match_fingerprint, achievement_type,
                        first_seen_at, last_seen_at
                    ) VALUES (
                        501, 'stage four preserved work', REPEAT('5', 64), 'article',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                    )
                    """);
        }

        Flyway stageFive = Flyway.configure()
                .dataSource(
                        STAGE_FOUR_MYSQL.getJdbcUrl(),
                        STAGE_FOUR_MYSQL.getUsername(),
                        STAGE_FOUR_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertEquals(3, stageFive.migrate().migrationsExecuted);
        assertTrue(stageFive.validateWithResult().validationSuccessful);

        try (Connection connection = STAGE_FOUR_MYSQL.createConnection("");
                Statement statement = connection.createStatement()) {
            assertEquals(1, scalarCount(statement, "SELECT COUNT(*) FROM achievement WHERE id = 501"));
            assertEquals(0, scalarCount(statement, "SELECT COUNT(*) FROM graph_projection_state"));
            assertEquals(0, scalarCount(statement, "SELECT COUNT(*) FROM graph_outbox_event"));
        }
    }

    private Set<String> tableNames(Statement statement) throws Exception {
        Set<String> names = new TreeSet<>();
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME")) {
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
        }
        return names;
    }

    private Set<String> roleCodes(Statement statement) throws Exception {
        Set<String> codes = new TreeSet<>();
        try (ResultSet resultSet = statement.executeQuery("SELECT role_code FROM sys_role ORDER BY role_code")) {
            while (resultSet.next()) {
                codes.add(resultSet.getString(1));
            }
        }
        return codes;
    }

    private long scalarCount(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }
}
