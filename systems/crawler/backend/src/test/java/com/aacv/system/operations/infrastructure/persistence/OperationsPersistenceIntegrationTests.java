package com.aacv.system.operations.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.operations.application.port.OperationsRepository;
import com.aacv.system.operations.domain.AlertEvent;
import com.aacv.system.operations.domain.AlertSeverity;
import com.aacv.system.operations.domain.AlertStatus;
import com.aacv.system.operations.domain.AlertSubjectType;
import com.aacv.system.operations.domain.AlertType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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
@SpringBootTest(properties = {
        "aacv.graph.outbox.enabled=false",
        "aacv.operations.alerts-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OperationsPersistenceIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_operations_test")
            .withUrlParam("serverTimezone", "UTC");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private OperationsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void readsOperationalSignalsAndPersistsIdempotentAlertLifecycle() {
        insertFixture();
        Instant now = Instant.parse("2026-09-03T00:00:00Z");

        assertEquals(1, repository.countActiveCrawlRuns());
        assertEquals(1, repository.countRecentUnresolvedCrawlFailures(now.minusSeconds(86_400)));
        assertEquals(4, repository.findSourceFailureSignals(3).getFirst().consecutiveFailures());
        var parseSignal = repository.findParseRateSignals(20, new BigDecimal("0.80")).getFirst();
        assertEquals(10, parseSignal.taskId());
        assertEquals(new BigDecimal("0.250000"), parseSignal.successRate());
        assertEquals(Instant.parse("2026-09-02T23:50:00Z"), repository.latestGraphSignalAt());

        AlertEvent event = new AlertEvent(
                0, AlertType.CRAWL_CONSECUTIVE_FAILURES, AlertSeverity.WARNING, AlertStatus.OPEN,
                AlertSubjectType.SOURCE, "1", "数据源连续采集失败达到阈值", Map.of("count", 4),
                Instant.parse("2026-09-02T23:40:00Z"), now, now, 1, null, null, null, 0);
        String dedupKey = "CRAWL_CONSECUTIVE_FAILURES:SOURCE:1";
        repository.insertOpen(event, dedupKey);
        repository.insertOpen(event, dedupKey);

        AlertEvent open = repository.findOpenByDedupKey(dedupKey).orElseThrow();
        assertEquals(1, repository.countOpenAlerts());
        assertTrue(repository.updateDetection(
                open.id(), AlertSeverity.CRITICAL, open.summary(), Map.of("count", 6),
                now.plusSeconds(1), now.plusSeconds(1), open.version()));
        AlertEvent updated = repository.findOpenByDedupKey(dedupKey).orElseThrow();
        assertEquals(AlertSeverity.CRITICAL, updated.severity());
        assertEquals(2, updated.occurrenceCount());
        assertTrue(repository.acknowledge(updated.id(), 90, "已核查来源状态", now.plusSeconds(2), updated.version()));

        AlertEvent acknowledged = repository.findById(updated.id()).orElseThrow();
        assertEquals(AlertStatus.ACKNOWLEDGED, acknowledged.status());
        assertEquals("已核查来源状态", acknowledged.acknowledgementReason());
        assertEquals(1, repository.findAlerts(AlertStatus.ACKNOWLEDGED, null, 0, 20).totalElements());
    }

    private void insertFixture() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password_hash, status)
                VALUES (90, 'operations-user', 'not-a-real-password-hash', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO data_source (
                    id, source_code, source_type, base_url, adapter_code, compliance_note,
                    last_failure_at, consecutive_failures
                ) VALUES (
                    1, 'OPENALEX', 'OPENALEX', 'https://api.openalex.org', 'OPENALEX_REST_V1',
                    '运维测试', '2026-09-02 23:40:00', 4
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO crawl_task (
                    id, source_id, task_name, parameters_json, parameter_hash, created_by
                ) VALUES (10, 1, '运维测试任务', JSON_OBJECT(), REPEAT('a', 64), 90)
                """);
        jdbcTemplate.update("""
                INSERT INTO crawl_run (
                    id, task_id, run_number, trigger_type, status, parameter_hash,
                    read_count, parsed_count, failure_count, finished_at
                ) VALUES
                    (20, 10, '00000000-0000-0000-0000-000000000020', 'MANUAL', 'FAILED', REPEAT('a', 64),
                     40, 10, 1, '2026-09-02 23:45:00'),
                    (21, 10, '00000000-0000-0000-0000-000000000021', 'MANUAL', 'PENDING', REPEAT('b', 64),
                     0, 0, 0, NULL)
                """);
        jdbcTemplate.update("""
                INSERT INTO crawl_failure (
                    run_id, failure_stage, error_category, safe_message, created_at
                ) VALUES (20, 'PARSE', 'INVALID_INPUT', '有限测试摘要', '2026-09-02 23:46:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO achievement (
                    id, title_normalized, match_fingerprint, achievement_type, first_seen_at, last_seen_at
                ) VALUES (100, 'operations', REPEAT('c', 64), 'article', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        jdbcTemplate.update("""
                INSERT INTO graph_outbox_event (
                    event_id, achievement_id, desired_version, status, created_at, updated_at
                ) VALUES (
                    '00000000-0000-0000-0000-000000000100', 100, 1, 'PENDING',
                    '2026-09-02 23:30:00', '2026-09-02 23:50:00'
                )
                """);
    }
}
