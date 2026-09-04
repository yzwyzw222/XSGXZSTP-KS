package com.aacv.system.analytics.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aacv.system.analytics.application.port.AnalyticsRepository;
import com.aacv.system.analytics.domain.AnalyticsQuery;
import com.aacv.system.source.domain.SourceType;
import org.junit.jupiter.api.BeforeEach;
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
class AnalyticsPersistenceIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_analytics_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private AnalyticsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password_hash, status)
                VALUES (90, 'analytics-test', 'not-a-real-password-hash', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO data_source (
                    id, source_code, source_type, base_url, adapter_code, enabled,
                    requests_per_second, max_concurrency, connect_timeout_seconds,
                    response_timeout_seconds, max_retries, max_response_bytes, compliance_note
                ) VALUES
                    (1, 'OPENALEX', 'OPENALEX', 'https://api.openalex.org', 'OPENALEX_REST_V1', TRUE,
                     1, 1, 10, 30, 3, 1048576, 'test'),
                    (2, 'CROSSREF', 'CROSSREF', 'https://api.crossref.org', 'CROSSREF_REST_V1', TRUE,
                     1, 1, 10, 30, 3, 1048576, 'test')
                """);
        jdbcTemplate.update("""
                INSERT INTO crawl_task (
                    id, source_id, task_name, parameter_version, parameters_json,
                    parameter_hash, enabled, created_by
                ) VALUES
                    (1, 1, 'analytics-openalex', 1, JSON_OBJECT(), REPEAT('1', 64), TRUE, 90),
                    (2, 2, 'analytics-crossref', 2, JSON_OBJECT(), REPEAT('2', 64), TRUE, 90)
                """);
        jdbcTemplate.update("""
                INSERT INTO crawl_run (
                    id, task_id, run_number, trigger_type, status, requested_by, parameter_hash
                ) VALUES
                    (1, 1, '00000000-0000-0000-0000-000000000301', 'MANUAL', 'SUCCEEDED', 90, REPEAT('1', 64)),
                    (2, 2, '00000000-0000-0000-0000-000000000302', 'MANUAL', 'SUCCEEDED', 90, REPEAT('2', 64))
                """);
        jdbcTemplate.update("""
                INSERT INTO raw_record (
                    id, source_id, run_id, external_record_id, source_url, fetched_at, payload_hash,
                    parser_version, parse_status, payload_expires_at, first_seen_at, last_seen_at
                ) VALUES
                    (1, 1, 1, 'W100', 'https://openalex.org/W100', UTC_TIMESTAMP(6), REPEAT('a', 64),
                     'test', 'PARSED', DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 90 DAY), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (2, 1, 1, 'W101', 'https://openalex.org/W101', UTC_TIMESTAMP(6), REPEAT('b', 64),
                     'test', 'PARSED', DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 90 DAY), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (3, 2, 2, '10.1000/merged', 'https://doi.org/10.1000/merged', UTC_TIMESTAMP(6), REPEAT('c', 64),
                     'test', 'PARSED', DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 90 DAY), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        jdbcTemplate.update("""
                INSERT INTO author (id, display_name) VALUES
                    (1, 'Alice'), (2, 'Bob'), (3, 'Carol')
                """);
        jdbcTemplate.update("""
                INSERT INTO organization (id, openalex_id, display_name) VALUES
                    (10, 'I10', 'North Lab'), (11, 'I11', 'South Lab')
                """);
        jdbcTemplate.update("""
                INSERT INTO subject (id, source_id, external_id, display_name) VALUES
                    (20, 1, 'T20', 'Software Security'), (21, 1, 'T21', 'Data Quality')
                """);
        jdbcTemplate.update("""
                INSERT INTO achievement (
                    id, title_normalized, match_fingerprint, achievement_type,
                    publication_date, first_seen_at, last_seen_at
                ) VALUES
                    (100, 'Paper 2025', REPEAT('d', 64), 'article', '2025-03-01', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (101, 'Dataset 2026', REPEAT('e', 64), 'dataset', '2026-04-01', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (102, 'Merged record', REPEAT('f', 64), 'article', '2025-03-01', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        jdbcTemplate.update("""
                INSERT INTO achievement_author (achievement_id, author_id, author_position) VALUES
                    (100, 1, 1), (100, 2, 2), (101, 1, 1), (101, 3, 2)
                """);
        jdbcTemplate.update("""
                INSERT INTO authorship_organization (achievement_id, author_id, organization_id) VALUES
                    (100, 1, 10), (100, 2, 11), (101, 1, 10), (101, 3, 11)
                """);
        jdbcTemplate.update("""
                INSERT INTO achievement_subject (achievement_id, subject_id, subject_position) VALUES
                    (100, 20, 1), (101, 21, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO achievement_source (
                    achievement_id, source_id, raw_record_id, external_record_id,
                    source_url, parser_version, first_seen_at, last_seen_at
                ) VALUES
                    (100, 1, 1, 'W100', 'https://openalex.org/W100', 'test', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (101, 1, 2, 'W101', 'https://openalex.org/W101', 'test', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (102, 2, 3, '10.1000/merged', 'https://doi.org/10.1000/merged', 'test', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        jdbcTemplate.update("""
                INSERT INTO canonical_entity_link (entity_type, entity_id, canonical_entity_id)
                VALUES ('ACHIEVEMENT', 102, 100)
                """);
    }

    @Test
    void aggregatesCanonicalAchievementsAndAppliesEveryControlledFilter() {
        AnalyticsQuery all = new AnalyticsQuery(null, null, null, null, null, null);

        var overview = repository.overview(all);
        assertEquals(2, overview.achievementCount());
        assertEquals(3, overview.authorCount());
        assertEquals(2, overview.organizationCount());
        assertEquals(2, overview.sourceCount());
        assertEquals(2, repository.trends(all).size());
        assertEquals(2, repository.distributions(all).achievementTypes().size());
        assertEquals(2, repository.distributions(all).sources().size());
        assertEquals(2, repository.distributions(all).organizations().size());
        assertEquals(2, repository.distributions(all).topics().size());
        assertEquals(2, repository.collaboration(all, 20).authors().size());
        assertEquals(1, repository.collaboration(all, 20).organizations().size());
        assertEquals(2, repository.collaboration(all, 20).organizations().getFirst().sharedAchievementCount());
        assertNotNull(repository.lastUpdatedAt(all));

        assertEquals(1, repository.overview(new AnalyticsQuery(2026, 2026, null, null, null, null))
                .achievementCount());
        assertEquals(1, repository.overview(new AnalyticsQuery(null, null, "dataset", null, null, null))
                .achievementCount());
        assertEquals(1, repository.overview(new AnalyticsQuery(null, null, null, SourceType.CROSSREF, null, null))
                .achievementCount());
        assertEquals(2, repository.overview(new AnalyticsQuery(null, null, null, null, 10L, null))
                .achievementCount());
        assertEquals(1, repository.overview(new AnalyticsQuery(null, null, null, null, null, 20L))
                .achievementCount());
    }
}
