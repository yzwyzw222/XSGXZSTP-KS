package com.aacv.system.graph.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import com.aacv.system.graph.application.port.GraphProjectionRequestPort;
import com.aacv.system.graph.application.GraphOutboxService;
import com.aacv.system.graph.domain.GraphOutboxEvent;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@SpringBootTest(properties = "spring.quartz.auto-startup=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GraphOutboxPersistenceTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_graph_outbox_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private GraphProjectionRequestPort graphProjectionRequestPort;

    @Autowired
    private GraphOutboxService graphOutboxService;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM graph_sync_dead_letter");
        jdbcTemplate.update("DELETE FROM graph_outbox_event");
        jdbcTemplate.update("DELETE FROM graph_projection_state");
        jdbcTemplate.update("DELETE FROM achievement");
    }

    @Test
    void emptySchemaContainsNoImplicitProjectionEvents() {
        assertEquals(0, count("graph_projection_state"));
        assertEquals(0, count("graph_outbox_event"));
        assertEquals(0, count("graph_sync_dead_letter"));
        assertEquals(0, count("graph_maintenance_run"));
    }

    @Test
    void databaseRejectsDuplicateVersionsInvalidStatesAndVersionRegression() {
        insertAchievement(1);
        jdbcTemplate.update("""
                INSERT INTO graph_projection_state (
                    achievement_id, desired_version, applied_version, last_enqueued_at
                ) VALUES (?, 1, 0, UTC_TIMESTAMP(6))
                """, 1L);
        jdbcTemplate.update("""
                INSERT INTO graph_outbox_event (
                    event_id, achievement_id, desired_version, status
                ) VALUES (?, ?, 1, 'PENDING')
                """, UUID.randomUUID().toString(), 1L);

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                INSERT INTO graph_outbox_event (
                    event_id, achievement_id, desired_version, status
                ) VALUES (?, ?, 1, 'PENDING')
                """, UUID.randomUUID().toString(), 1L));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE graph_projection_state SET applied_version = 2 WHERE achievement_id = 1
                """));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                UPDATE graph_outbox_event SET status = 'PROCESSING' WHERE achievement_id = 1
                """));
    }

    @Test
    void achievementProjectionStateAndOutboxRollbackWithBusinessTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IntentionalRollback.class, () -> transaction.executeWithoutResult(status -> {
            insertAchievement(2);
            graphProjectionRequestPort.requestAchievement(2);
            throw new IntentionalRollback();
        }));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM achievement WHERE id = 2", Integer.class));
        assertEquals(0, count("graph_projection_state"));
        assertEquals(0, count("graph_outbox_event"));
    }

    @Test
    void projectionRequestsAdvanceMonotonicVersionsInsideBusinessTransaction() {
        insertAchievement(3);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            graphProjectionRequestPort.requestAchievement(3);
            graphProjectionRequestPort.requestAchievement(3);
        });

        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT desired_version FROM graph_projection_state WHERE achievement_id = 3",
                Long.class));
        assertEquals(2, count("graph_outbox_event"));
    }

    @Test
    void outboxClaimsOneAggregateVersionAtATimeAndAdvancesAppliedVersionAfterSuccess() {
        insertAchievement(4);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            graphProjectionRequestPort.requestAchievement(4);
            graphProjectionRequestPort.requestAchievement(4);
        });

        List<GraphOutboxEvent> first = graphOutboxService.claim("worker-a", 50, Duration.ofMinutes(1));
        assertEquals(1, first.size());
        assertEquals(1, first.getFirst().desiredVersion());
        assertEquals(0, graphOutboxService.claim("worker-b", 50, Duration.ofMinutes(1)).size());

        graphOutboxService.succeed(first.getFirst(), "worker-a");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT applied_version FROM graph_projection_state WHERE achievement_id = 4",
                Long.class));
        assertEquals(2, graphOutboxService.claim("worker-b", 50, Duration.ofMinutes(1))
                .getFirst().desiredVersion());
    }

    @Test
    void expiredLeaseIsRecoveredAndFiveFailuresCreateReplayableDeadLetter() {
        insertAchievement(5);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String eventId = transaction.execute(status -> graphProjectionRequestPort.requestAchievement(5));

        GraphOutboxEvent event = graphOutboxService.claim("worker-a", 1, Duration.ofMinutes(1)).getFirst();
        jdbcTemplate.update(
                "UPDATE graph_outbox_event SET locked_until = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                event.id());
        event = graphOutboxService.claim("worker-b", 1, Duration.ofMinutes(1)).getFirst();
        for (int attempt = 1; attempt <= GraphOutboxService.MAX_ATTEMPTS; attempt++) {
            graphOutboxService.fail(event, "worker-b", "NEO4J_UNAVAILABLE");
            if (attempt < GraphOutboxService.MAX_ATTEMPTS) {
                jdbcTemplate.update(
                        "UPDATE graph_outbox_event SET next_attempt_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                        event.id());
                event = graphOutboxService.claim("worker-b", 1, Duration.ofMinutes(1)).getFirst();
            }
        }
        assertEquals("DEAD", jdbcTemplate.queryForObject(
                "SELECT status FROM graph_outbox_event WHERE event_id = ?", String.class, eventId));
        assertEquals(1, count("graph_sync_dead_letter"));

        String replayId = transaction.execute(status -> graphOutboxService.replay(eventId));
        assertEquals(replayId, jdbcTemplate.queryForObject(
                "SELECT replay_event_id FROM graph_sync_dead_letter WHERE event_id = ?",
                String.class, eventId));
    }

    private void insertAchievement(long id) {
        jdbcTemplate.update("""
                INSERT INTO achievement (
                    id, title_normalized, match_fingerprint, achievement_type,
                    first_seen_at, last_seen_at
                ) VALUES (?, ?, REPEAT(?, 64), 'article', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id, "graph-outbox-" + id, Long.toString(id));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static final class IntentionalRollback extends RuntimeException {
    }
}
