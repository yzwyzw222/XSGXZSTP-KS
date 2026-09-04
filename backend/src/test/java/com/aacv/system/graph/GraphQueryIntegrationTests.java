package com.aacv.system.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.graph.application.GraphOperationsService;
import com.aacv.system.graph.application.GraphQueryService;
import com.aacv.system.graph.application.GraphRebuildInProgressException;
import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.graph.domain.GraphRelationshipType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
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
class GraphQueryIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_graph_query_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private GraphQueryService queryService;

    @Autowired
    private GraphOperationsService operationsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createGraph() {
        neo4jClient.query("""
                MERGE (achievement:Achievement {businessId: 1})
                SET achievement.aacvManaged = true, achievement.title = 'Root',
                    achievement.achievementType = 'article', achievement.publicationDate = date('2026-01-01')
                MERGE (author:Author {businessId: 2})
                SET author.aacvManaged = true, author.name = 'Author'
                MERGE (topic:Topic {businessId: 3})
                SET topic.aacvManaged = true, topic.name = 'Topic'
                MERGE (external:Author {businessId: 99})
                SET external.aacvManaged = false, external.name = 'External'
                MERGE (author)-[:AUTHORED {aacvManaged: true, achievementBusinessId: 1}]->(achievement)
                MERGE (achievement)-[:HAS_TOPIC {aacvManaged: true, achievementBusinessId: 1}]->(topic)
                MERGE (external)-[:AUTHORED {aacvManaged: false}]->(achievement)
                """).run();
    }

    @Test
    @WithMockUser(authorities = "GRAPH_READ")
    void boundedSubgraphAndDeterministicPathExcludeUnmanagedData() {
        var graph = queryService.subgraph(
                GraphNodeType.ACHIEVEMENT, 1, 2, 100,
                null, null, 2020, 2030, List.of("article"));
        assertEquals(3, graph.nodes().size());
        assertEquals(2, graph.edges().size());
        assertTrue(graph.nodes().stream().noneMatch(node -> node.businessId().equals("99")));

        var path = queryService.path(GraphNodeType.AUTHOR, 2, GraphNodeType.TOPIC, 3, 6);
        assertEquals(3, path.nodes().size());
        assertEquals(2, path.edges().size());

        var truncated = queryService.subgraph(
                GraphNodeType.ACHIEVEMENT, 1, 1, 1,
                List.of(GraphRelationshipType.AUTHORED), null, null, null, null);
        assertTrue(truncated.truncated());
        assertEquals(1, truncated.nodes().size());
        assertThrows(IllegalArgumentException.class, () -> queryService.path(
                GraphNodeType.AUTHOR, 2, GraphNodeType.TOPIC, 3, 7));
    }

    @Test
    @WithMockUser(authorities = "GRAPH_SYNC_READ")
    void syncStatusUsesMysqlCountersAndReportsReadySchema() {
        var status = operationsService.status();
        assertTrue(status.neo4jAvailable());
        assertEquals(1, status.schemaVersion());
        assertEquals(0, status.pendingCount());
        assertEquals(0, status.processingCount());
        assertEquals(0, status.deadCount());
    }

    @Test
    @WithMockUser(authorities = "GRAPH_READ")
    void graphQueryIsRejectedWhileFullRebuildIsActive() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, password_hash, status)
                VALUES (99, 'graph-query-rebuild-test', '{noop}not-a-runtime-credential', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO graph_maintenance_run (run_type, status, requested_by)
                VALUES ('FULL_REBUILD', 'RUNNING', 99)
                """);
        try {
            assertThrows(GraphRebuildInProgressException.class, () -> queryService.subgraph(
                    GraphNodeType.ACHIEVEMENT, 1, 1, 100,
                    null, null, null, null, null));
        } finally {
            jdbcTemplate.update("DELETE FROM graph_maintenance_run WHERE requested_by = 99");
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = 99");
        }
    }
}
