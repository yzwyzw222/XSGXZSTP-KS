package com.aacv.system.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.graph.application.GraphOutboxProcessor;
import com.aacv.system.graph.application.port.GraphProjectionRequestPort;
import com.aacv.system.graph.application.port.GraphProjectionWriter;
import com.aacv.system.graph.infrastructure.neo4j.GraphSchemaState;
import org.junit.jupiter.api.BeforeEach;
import org.neo4j.driver.Driver;
import org.neo4j.driver.exceptions.Neo4jException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
class GraphProjectionIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_graph_projection_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private GraphProjectionWriter projectionWriter;

    @Autowired
    private GraphSchemaState schemaState;

    @Autowired
    private GraphProjectionRequestPort projectionRequestPort;

    @Autowired
    private GraphOutboxProcessor outboxProcessor;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Driver driver;

    @BeforeEach
    void clearTestProjection() {
        neo4jClient.query("MATCH (node) WHERE node.aacvManaged = true DETACH DELETE node").run();
    }

    @Test
    void projectionIsSchemaReadyIdempotentCanonicalAwareAndVersionMonotonic() {
        insertMysqlAggregate();

        assertTrue(schemaState.isReady());
        assertEquals(5L, scalar("SHOW CONSTRAINTS YIELD name WHERE name STARTS WITH 'aacv_' RETURN count(*) AS value"));
        assertEquals(7L, scalar("""
                SHOW INDEXES YIELD name, type
                WHERE name IN [
                    'aacv_achievement_doi', 'aacv_achievement_publication_date',
                    'aacv_achievement_type', 'aacv_author_orcid',
                    'aacv_institution_standard_code', 'aacv_venue_issn', 'aacv_topic_code'
                ]
                RETURN count(*) AS value
                """));

        projectionWriter.projectAchievement(100, 1);
        projectionWriter.projectAchievement(100, 1);

        assertEquals(2L, scalar("MATCH (node:Achievement) WHERE node.aacvManaged = true RETURN count(node) AS value"));
        assertEquals(1L, scalar("MATCH (:Author)-[rel:AUTHORED]->(:Achievement {businessId: 100}) RETURN count(rel) AS value"));
        assertEquals(1L, scalar("MATCH (:Author)-[rel:AFFILIATED_WITH]->(:Institution) WHERE rel.achievementBusinessId = 100 RETURN count(rel) AS value"));
        assertEquals(1L, scalar("MATCH (:Achievement {businessId: 100})-[:PUBLISHED_IN]->(:Venue) RETURN count(*) AS value"));
        assertEquals(1L, scalar("MATCH (:Achievement {businessId: 100})-[:HAS_TOPIC]->(:Topic) RETURN count(*) AS value"));
        assertEquals(1L, scalar("MATCH (:Achievement {businessId: 100})-[:CITES]->(:Achievement {businessId: 101}) RETURN count(*) AS value"));
        assertEquals("0000-0001-2345-6789", value(
                "MATCH (node:Author {businessId: 1}) RETURN node.orcid AS value", String.class));
        assertEquals("https://ror.org/012345678", value(
                "MATCH (node:Institution {businessId: 20}) RETURN node.standardCode AS value", String.class));
        assertEquals("CN", value(
                "MATCH (node:Institution {businessId: 20}) RETURN node.countryCode AS value", String.class));
        assertEquals("1234-5678", value(
                "MATCH (node:Venue {businessId: 10}) RETURN node.issn AS value", String.class));
        assertEquals("T30", value(
                "MATCH (node:Topic {businessId: 30}) RETURN node.code AS value", String.class));

        jdbcTemplate.update("UPDATE author SET display_name = 'Canonical Author' WHERE id = 1");
        jdbcTemplate.update("DELETE FROM achievement_subject WHERE achievement_id = 100");
        projectionWriter.projectAchievement(100, 2);
        assertEquals("Canonical Author", value(
                "MATCH (node:Author {businessId: 1}) RETURN node.name AS value", String.class));
        assertEquals(0L, scalar("MATCH (:Achievement {businessId: 100})-[:HAS_TOPIC]->() RETURN count(*) AS value"));
        assertEquals(2L, scalar("MATCH (node:Achievement {businessId: 100}) RETURN node.projectionVersion AS value"));

        jdbcTemplate.update("UPDATE achievement SET title_normalized = 'Older event must not win' WHERE id = 100");
        projectionWriter.projectAchievement(100, 1);
        assertEquals("Projection Center", value(
                "MATCH (node:Achievement {businessId: 100}) RETURN node.title AS value", String.class));
    }

    @Test
    void mysqlCommitSurvivesNeo4jOutageAndPendingEventIsCompensatedAfterRecovery() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO achievement (
                    id, title_normalized, match_fingerprint, achievement_type,
                    first_seen_at, last_seen_at
                ) VALUES (200, 'Outage Recovery', REPEAT('c', 64), 'article',
                          UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> projectionRequestPort.requestAchievement(200));

        var dockerClient = NEO4J.getDockerClient();
        String containerId = NEO4J.getContainerId();
        String networkId = dockerClient.inspectContainerCmd(containerId).exec()
                .getNetworkSettings().getNetworks().keySet().iterator().next();
        dockerClient.disconnectFromNetworkCmd()
                .withContainerId(containerId).withNetworkId(networkId).withForce(true).exec();
        ReflectionTestUtils.invokeMethod(schemaState, "markUnavailable");
        try {
            assertEquals(1, outboxProcessor.processBatch());
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM achievement WHERE id = 200", Long.class));
            assertEquals("PENDING", jdbcTemplate.queryForObject(
                    "SELECT status FROM graph_outbox_event WHERE achievement_id = 200",
                    String.class));
        } finally {
            dockerClient.connectToNetworkCmd()
                    .withContainerId(containerId).withNetworkId(networkId).exec();
            waitForNeo4j();
            ReflectionTestUtils.invokeMethod(schemaState, "markReady");
        }

        jdbcTemplate.update("""
                UPDATE graph_outbox_event
                SET next_attempt_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND
                WHERE achievement_id = 200
                """);
        assertEquals(1, outboxProcessor.processBatch());
        assertEquals("SUCCEEDED", jdbcTemplate.queryForObject(
                "SELECT status FROM graph_outbox_event WHERE achievement_id = 200",
                String.class));
        assertEquals(1L, scalar("""
                MATCH (node:Achievement {businessId: 200})
                WHERE node.aacvManaged = true RETURN count(node) AS value
                """));
    }

    private void waitForNeo4j() throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            try {
                driver.verifyConnectivity();
                return;
            } catch (Neo4jException exception) {
                Thread.sleep(250);
            }
        }
        throw new IllegalStateException("Neo4j测试容器未在限定时间内恢复");
    }

    private void insertMysqlAggregate() {
        jdbcTemplate.update("""
                INSERT INTO data_source (
                    id, source_code, source_type, base_url, adapter_code, enabled,
                    requests_per_second, max_concurrency, connect_timeout_seconds,
                    response_timeout_seconds, max_retries, max_response_bytes, compliance_note
                ) VALUES (1, 'OPENALEX', 'OPENALEX', 'https://api.openalex.org', 'OPENALEX_REST_V1', TRUE,
                          1, 1, 10, 30, 3, 1048576, 'test')
                """);
        jdbcTemplate.update("""
                INSERT INTO venue (id, display_name, venue_type, issn_l)
                VALUES (10, 'Venue A', 'journal', '1234-5678')
                """);
        jdbcTemplate.update("INSERT INTO author (id, display_name) VALUES (1, 'Author A')");
        jdbcTemplate.update("""
                INSERT INTO author_external_id (author_id, id_type, external_id)
                VALUES (1, 'ORCID', '0000-0001-2345-6789')
                """);
        jdbcTemplate.update("""
                INSERT INTO organization (id, openalex_id, display_name, country_code, organization_type)
                VALUES (20, 'https://openalex.org/I20', 'Institution A', 'CN', 'education')
                """);
        jdbcTemplate.update("""
                INSERT INTO organization_external_id (organization_id, id_type, external_id)
                VALUES (20, 'ROR', 'https://ror.org/012345678')
                """);
        jdbcTemplate.update("""
                INSERT INTO subject (id, source_id, external_id, display_name, subject_path)
                VALUES (30, 1, 'T30', 'Topic A', 'Field > Topic A')
                """);
        jdbcTemplate.update("""
                INSERT INTO achievement (
                    id, title_normalized, doi_normalized, match_fingerprint, achievement_type,
                    language, publication_date, primary_venue_id, first_seen_at, last_seen_at
                ) VALUES
                    (100, 'Projection Center', '10.1000/center', REPEAT('a', 64), 'article',
                     'en', '2026-01-02', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                    (101, 'Referenced Work', '10.1000/cited', REPEAT('b', 64), 'article',
                     'en', '2025-01-02', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        jdbcTemplate.update("INSERT INTO achievement_author VALUES (100, 1, 1, UTC_TIMESTAMP(6))");
        jdbcTemplate.update("INSERT INTO authorship_organization VALUES (100, 1, 20, UTC_TIMESTAMP(6))");
        jdbcTemplate.update("INSERT INTO achievement_subject VALUES (100, 30, 1, UTC_TIMESTAMP(6))");
        jdbcTemplate.update("""
                INSERT INTO achievement_reference (
                    citing_achievement_id, referenced_external_work_id, referenced_id_type,
                    referenced_id_value, cited_achievement_id
                ) VALUES (100, 'https://openalex.org/W101', 'OPENALEX', 'https://openalex.org/W101', 101)
                """);
    }

    private long scalar(String cypher) {
        return value(cypher, Long.class);
    }

    private <T> T value(String cypher, Class<T> type) {
        return neo4jClient.query(cypher)
                .fetchAs(type)
                .one()
                .orElseThrow();
    }
}
