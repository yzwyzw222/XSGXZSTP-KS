package com.aacv.system.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aacv.system.graph.application.GraphMaintenanceService;
import com.aacv.system.graph.application.GraphOutboxProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.job.Job;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@SpringBootTest(properties = {
        "spring.quartz.auto-startup=false",
        "aacv.graph.outbox.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GraphMaintenanceIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_graph_maintenance_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private GraphMaintenanceService maintenanceService;

    @Autowired
    private GraphOutboxProcessor outboxProcessor;

    @Autowired
    @Qualifier("graphMaintenanceJob")
    private Job maintenanceJob;

    @Test
    void backfillReconciliationAndManagedOnlyRebuildAreRecoverable() {
        assertNotNull(maintenanceJob);
        insertMysqlSource();

        long backfill = insertRun("INITIAL_BACKFILL");
        maintenanceService.execute(backfill);
        assertRun(backfill, "SUCCEEDED", 2, 2, 2);
        assertEquals(2, count("graph_outbox_event"));
        assertEquals(2, outboxProcessor.processBatch());
        assertEquals(2L, neo4jLong(
                "MATCH (node:Achievement) WHERE node.aacvManaged = true RETURN count(node) AS value"));

        long cleanReconcile = insertRun("RECONCILE");
        maintenanceService.execute(cleanReconcile);
        assertRun(cleanReconcile, "SUCCEEDED", 2, 0, 0);

        neo4jClient.query("MATCH (node:Achievement {businessId: 1}) DETACH DELETE node").run();
        long driftReconcile = insertRun("RECONCILE");
        maintenanceService.execute(driftReconcile);
        assertRun(driftReconcile, "SUCCEEDED", 2, 1, 1);

        neo4jClient.query("CREATE (:ExternalData {name: 'must-survive'})").run();
        long rebuild = insertRun("FULL_REBUILD");
        maintenanceService.execute(rebuild);
        assertRun(rebuild, "SUCCEEDED", 2, 2, 2);
        assertEquals(0L, neo4jLong(
                "MATCH (node) WHERE node.aacvManaged = true RETURN count(node) AS value"));
        assertEquals(1L, neo4jLong("MATCH (node:ExternalData) RETURN count(node) AS value"));
        assertEquals(2, outboxProcessor.processBatch());
        assertEquals(2L, neo4jLong(
                "MATCH (node:Achievement) WHERE node.aacvManaged = true RETURN count(node) AS value"));
    }

    private void insertMysqlSource() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password_hash, status)
                VALUES (10, 'graph-maintenance-test', '{noop}not-a-runtime-credential', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO achievement (
                    id, title_normalized, match_fingerprint, achievement_type,
                    first_seen_at, last_seen_at
                ) VALUES
                    (1, 'First', REPEAT('a', 64), 'article', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (2, 'Second', REPEAT('b', 64), 'article', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
    }

    private long insertRun(String type) {
        jdbcTemplate.update(
                "INSERT INTO graph_maintenance_run (run_type, status, requested_by) VALUES (?, 'PENDING', 10)",
                type);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM graph_maintenance_run", Long.class);
    }

    private void assertRun(long runId, String status, long scanned, long repaired, long differences) {
        assertEquals(status, jdbcTemplate.queryForObject(
                "SELECT status FROM graph_maintenance_run WHERE id = ?", String.class, runId));
        assertEquals(scanned, jdbcTemplate.queryForObject(
                "SELECT scanned_count FROM graph_maintenance_run WHERE id = ?", Long.class, runId));
        assertEquals(repaired, jdbcTemplate.queryForObject(
                "SELECT repaired_count FROM graph_maintenance_run WHERE id = ?", Long.class, runId));
        assertEquals(differences, jdbcTemplate.queryForObject(
                "SELECT difference_count FROM graph_maintenance_run WHERE id = ?", Long.class, runId));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private long neo4jLong(String cypher) {
        return neo4jClient.query(cypher).fetchAs(Long.class).one().orElseThrow();
    }
}
