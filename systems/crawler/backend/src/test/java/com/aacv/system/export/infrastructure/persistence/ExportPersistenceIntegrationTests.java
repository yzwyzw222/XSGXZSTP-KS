package com.aacv.system.export.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.domain.ExportFilter;
import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportStatus;
import com.aacv.system.export.domain.ExportTask;
import com.aacv.system.source.domain.SourceType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExportPersistenceIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_export_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private ExportRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void filtersCanonicalRecordsAndPersistsTaskLifecycle() {
        insertFixture();
        ExportFilter filter = new ExportFilter(
                "curated", 1L, 10L, 2026, 2026, "dataset", SourceType.CROSSREF, 2L, 20L);

        assertEquals(1, repository.countRecords(filter));
        var records = repository.findRecords(filter, 10_000);
        assertEquals(1, records.size());
        assertEquals(100, records.getFirst().id());
        assertEquals("Curated title", records.getFirst().title());
        assertEquals("dataset", records.getFirst().achievementType());
        assertEquals("zh", records.getFirst().language());
        assertEquals("Curated Venue", records.getFirst().primaryVenue());

        Instant createdAt = Instant.parse("2026-09-02T12:00:00Z");
        String taskId = "00000000-0000-0000-0000-000000000401";
        ExportTask task = new ExportTask(
                taskId, ExportFormat.JSON, ExportStatus.PENDING, filter, 90, 1, 0,
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG", null, null,
                createdAt, null, null, null, null, null, 0);
        repository.insert(task);

        assertEquals(1, repository.countActiveByRequester(90));
        assertTrue(repository.claim(taskId, createdAt.plusSeconds(1)));
        repository.markSucceeded(
                taskId, 1, "result.json", "result.json", createdAt.plusSeconds(2), createdAt.plusSeconds(3600));
        ExportTask succeeded = repository.findById(taskId).orElseThrow();
        assertEquals(ExportStatus.SUCCEEDED, succeeded.status());
        assertEquals(filter, succeeded.filters());
        assertEquals(2, succeeded.version());
        assertTrue(repository.markExpired(taskId, createdAt.plusSeconds(3600)));
        assertEquals(ExportStatus.EXPIRED, repository.findById(taskId).orElseThrow().status());
    }

    private void insertFixture() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password_hash, status)
                VALUES (90, 'export-test', 'not-a-real-password-hash', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO data_source (
                    id, source_code, source_type, base_url, adapter_code, compliance_note
                ) VALUES
                    (1, 'OPENALEX', 'OPENALEX', 'https://api.openalex.org', 'OPENALEX_REST_V1', 'test'),
                    (2, 'CROSSREF', 'CROSSREF', 'https://api.crossref.org', 'CROSSREF_REST_V1', 'test')
                """);
        jdbcTemplate.update("""
                INSERT INTO crawl_task (
                    id, source_id, task_name, parameter_version, parameters_json,
                    parameter_hash, enabled, created_by
                ) VALUES
                    (1, 1, 'export-openalex', 1, JSON_OBJECT(), REPEAT('1', 64), TRUE, 90),
                    (2, 2, 'export-crossref', 2, JSON_OBJECT(), REPEAT('2', 64), TRUE, 90)
                """);
        jdbcTemplate.update("""
                INSERT INTO crawl_run (
                    id, task_id, run_number, trigger_type, status, requested_by, parameter_hash
                ) VALUES
                    (1, 1, '00000000-0000-0000-0000-000000000411', 'MANUAL', 'SUCCEEDED', 90, REPEAT('1', 64)),
                    (2, 2, '00000000-0000-0000-0000-000000000412', 'MANUAL', 'SUCCEEDED', 90, REPEAT('2', 64))
                """);
        jdbcTemplate.update("""
                INSERT INTO raw_record (
                    id, source_id, run_id, external_record_id, source_url, fetched_at,
                    payload_hash, parser_version, parse_status, payload_expires_at,
                    first_seen_at, last_seen_at
                ) VALUES
                    (1, 1, 1, 'W100', 'https://openalex.org/W100', UTC_TIMESTAMP(6),
                     REPEAT('a', 64), 'test', 'PARSED', DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 90 DAY),
                     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (2, 2, 2, '10.1000/member', 'https://doi.org/10.1000/member', UTC_TIMESTAMP(6),
                     REPEAT('b', 64), 'test', 'PARSED', DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 90 DAY),
                     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        jdbcTemplate.update("""
                INSERT INTO venue (id, openalex_id, display_name) VALUES
                    (1, 'S1', 'Original Venue'), (2, 'S2', 'Curated Venue')
                """);
        jdbcTemplate.update("INSERT INTO author (id, display_name) VALUES (1, 'Alice')");
        jdbcTemplate.update("""
                INSERT INTO organization (id, openalex_id, display_name)
                VALUES (10, 'I10', 'Research Lab')
                """);
        jdbcTemplate.update("""
                INSERT INTO subject (id, source_id, external_id, display_name)
                VALUES (20, 1, 'T20', 'Security')
                """);
        jdbcTemplate.update("""
                INSERT INTO achievement (
                    id, title_original, title_normalized, match_fingerprint, achievement_type,
                    language, publication_date, primary_venue_id, first_seen_at, last_seen_at
                ) VALUES
                    (100, 'Original title', 'original title', REPEAT('c', 64), 'article',
                     'en', '2025-01-01', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (101, 'Member title', 'member title', REPEAT('d', 64), 'article',
                     'en', '2025-01-01', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        jdbcTemplate.update("""
                INSERT INTO achievement_source (
                    achievement_id, source_id, raw_record_id, external_record_id,
                    source_url, parser_version, first_seen_at, last_seen_at
                ) VALUES
                    (100, 1, 1, 'W100', 'https://openalex.org/W100', 'test', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (101, 2, 2, '10.1000/member', 'https://doi.org/10.1000/member', 'test', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        jdbcTemplate.update("INSERT INTO achievement_author VALUES (101, 1, 1, UTC_TIMESTAMP(6))");
        jdbcTemplate.update("INSERT INTO authorship_organization VALUES (101, 1, 10, UTC_TIMESTAMP(6))");
        jdbcTemplate.update("INSERT INTO achievement_subject VALUES (101, 20, 1, UTC_TIMESTAMP(6))");
        jdbcTemplate.update("""
                INSERT INTO canonical_entity_link (entity_type, entity_id, canonical_entity_id)
                VALUES ('ACHIEVEMENT', 101, 100)
                """);
        jdbcTemplate.update("""
                INSERT INTO data_revision (
                    id, entity_type, entity_id, revision_action, actor_user_id, reason
                ) VALUES
                    (1, 'ACHIEVEMENT', 100, 'FIELD_OVERRIDE', 90, 'test'),
                    (2, 'ACHIEVEMENT', 100, 'FIELD_OVERRIDE', 90, 'test'),
                    (3, 'ACHIEVEMENT', 100, 'FIELD_OVERRIDE', 90, 'test'),
                    (4, 'ACHIEVEMENT', 100, 'FIELD_OVERRIDE', 90, 'test'),
                    (5, 'ACHIEVEMENT', 100, 'FIELD_OVERRIDE', 90, 'test')
                """);
        jdbcTemplate.update("""
                INSERT INTO manual_field_override (
                    achievement_id, field_name, field_value, revision_id, actor_user_id, reason
                ) VALUES
                    (100, 'title', JSON_QUOTE('Curated title'), 1, 90, 'test'),
                    (100, 'type', JSON_QUOTE('dataset'), 2, 90, 'test'),
                    (100, 'language', JSON_QUOTE('zh'), 3, 90, 'test'),
                    (100, 'publicationDate', JSON_QUOTE('2026-02-03'), 4, 90, 'test'),
                    (100, 'venueId', CAST('2' AS JSON), 5, 90, 'test')
                """);
    }
}
