package com.aacv.system.graph;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.graph.application.GraphOperationsService;
import com.aacv.system.graph.application.GraphQueryService;
import com.aacv.system.graph.application.GraphTypeService;
import com.aacv.system.graph.domain.GraphTypeDefinition;
import com.aacv.system.graph.domain.GraphTypeDefinition.Kind;
import com.aacv.system.graph.domain.GraphTypeDefinition.ReviewStatus;
import com.aacv.system.graph.application.GraphRebuildInProgressException;
import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.graph.domain.GraphRelationshipType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@AutoConfigureMockMvc
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

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private GraphTypeService typeService;

    @Test
    @WithMockUser(authorities = {"GRAPH_READ", "GRAPH_SYNC_MANAGE"})
    void graphResponseCombinesMysqlStylesWithNeo4jEvidence() throws Exception {
        GraphTypeDefinition author = typeService.list().stream()
                .filter(value -> value.kind() == Kind.NODE && value.code().equals("AUTHOR")).findFirst().orElseThrow();
        neo4jClient.query("""
                MATCH (work:Achievement {businessId: 1})
                MERGE (author:Author {businessId: 4})
                SET author.aacvManaged = true, author.name = 'Coauthor'
                MERGE (author)-[:AUTHORED {aacvManaged: true, achievementBusinessId: 1}]->(work)
                """).run();
        try {
            GraphTypeDefinition changed = typeService.update(new GraphTypeDefinition(Kind.NODE, "AUTHOR",
                    "学者", "#123456", 52, ReviewStatus.APPROVED, author.version()));
            assertEquals(author.version() + 1, changed.version());
            mvc.perform(get("/api/v1/graph/subgraph").param("centerType", "ACHIEVEMENT")
                    .param("centerId", "1").param("includeCoauthors", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nodes.length()").value(4))
                    .andExpect(jsonPath("$.edges[?(@.type == 'COAUTHORED')].properties.sharedWorkCount").value(1))
                    .andExpect(jsonPath("$.edges[?(@.type == 'COAUTHORED')].properties.derived").value(true))
                    .andExpect(jsonPath("$.typeDefinitions[?(@.code == 'AUTHOR')].displayName").value("学者"))
                    .andExpect(jsonPath("$.typeDefinitions[?(@.code == 'AUTHOR')].color").value("#123456"))
                    .andExpect(jsonPath("$.typeDefinitions[?(@.code == 'AUTHOR')].size").value(52));
            mvc.perform(get("/api/v1/graph/subgraph").param("centerType", "ACHIEVEMENT").param("centerId", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.edges.length()").value(3));
            mvc.perform(get("/api/v1/graph/path").param("sourceType", "AUTHOR").param("sourceId", "2")
                    .param("targetType", "AUTHOR").param("targetId", "4"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.edges.length()").value(2));
        } finally {
            SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
            neo4jClient.query("MATCH (author:Author {businessId: 4}) DETACH DELETE author").run();
            GraphTypeDefinition current = typeService.list().stream().filter(value -> value.code().equals("AUTHOR")).findFirst().orElseThrow();
            typeService.update(new GraphTypeDefinition(author.kind(), author.code(), author.displayName(),
                    author.color(), author.size(), author.reviewStatus(), current.version()));
        }
    }

    @Test
    @WithMockUser(authorities = {"GRAPH_READ", "GRAPH_SYNC_MANAGE"})
    void typeUpdatesValidateCsrfFieldsVersionAndRecordAudit() throws Exception {
        var value = typeService.list().stream().filter(item -> item.code().equals("COAUTHORED")).findFirst().orElseThrow();
        String body = """
                {"displayName":"合作","color":"#258ca3","size":2,"reviewStatus":"PENDING","version":%d}
                """.formatted(value.version());
        mvc.perform(put("/api/v1/graph/types/RELATIONSHIP/COAUTHORED").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/graph/types/RELATIONSHIP/COAUTHORED").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"size\":2", "\"size\":99")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/graph/types/RELATIONSHIP/COAUTHORED").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(value.version() + 1));
        mvc.perform(put("/api/v1/graph/types/RELATIONSHIP/COAUTHORED").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/graph/types/RELATIONSHIP/UNKNOWN").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        assertTrue(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'GRAPH_TYPE_UPDATED'", Long.class) > 0);
    }

    @Test
    @WithMockUser(authorities = "GRAPH_READ")
    void researcherCanReadTypesButCannotEdit() throws Exception {
        mvc.perform(get("/api/v1/graph/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[?(@.kind == 'RELATIONSHIP')].code",
                        hasItems("SUPERVISED", "PRODUCED_AT")));
        mvc.perform(put("/api/v1/graph/types/NODE/AUTHOR").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"作者","color":"#258ca3","size":30,"reviewStatus":"PENDING","version":0}
                        """)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CATALOG_READ")
    void missingGraphPermissionIsRejected() throws Exception {
        mvc.perform(get("/api/v1/graph/overview")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/graph/types")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousTypeAccessIsRejected() throws Exception {
        mvc.perform(get("/api/v1/graph/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/graph/types")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "GRAPH_READ")
    void overviewLoadsOnlyAuthorsAndWorksWithBoundedEvidence() throws Exception {
        neo4jClient.query("""
                MATCH (work:Achievement {businessId: 1})
                CREATE (coauthor:Author {businessId: 400, aacvManaged: true, name: '共同作者'})
                CREATE (coauthor)-[:AUTHORED {aacvManaged: true}]->(work)
                CREATE (:Author {businessId: 401, aacvManaged: true, name: '孤立作者'})
                """).run();
        try {
            mvc.perform(get("/api/v1/graph/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nodes.length()").value(4))
                    .andExpect(jsonPath("$.edges.length()").value(3))
                    .andExpect(jsonPath("$.edges[?(@.type == 'COAUTHORED')].properties.sharedWorkCount").value(1))
                    .andExpect(jsonPath("$.typeDefinitions[?(@.code == 'AUTHOR')].color").exists())
                    .andExpect(jsonPath("$.truncated").value(false));
            mvc.perform(get("/api/v1/graph/overview").param("nodeLimit", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(1))
                    .andExpect(jsonPath("$.edges.length()").value(0)).andExpect(jsonPath("$.truncated").value(true));
            mvc.perform(get("/api/v1/graph/overview").param("nodeLimit", "301")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/graph/overview").param("nodeLimit", "0")).andExpect(status().isBadRequest());
        } finally {
            neo4jClient.query("MATCH (author:Author) WHERE author.businessId IN [400, 401] DETACH DELETE author").run();
        }
    }

    @Test
    @WithMockUser(authorities = "GRAPH_READ")
    void authorWithManyWorksKeepsCoauthorEvidenceWithinNodeLimit() throws Exception {
        neo4jClient.query("""
                MATCH (author:Author {businessId: 2}), (work:Achievement {businessId: 1})
                CREATE (partner:Author {businessId: 900, name: '合作作者', aacvManaged: true})
                CREATE (partner)-[:AUTHORED {aacvManaged: true}]->(work)
                WITH author
                UNWIND range(901, 920) AS id
                CREATE (extra:Achievement {businessId: id, title: '大量成果', aacvManaged: true})
                CREATE (author)-[:AUTHORED {aacvManaged: true}]->(extra)
                """).run();
        try {
            mvc.perform(get("/api/v1/graph/subgraph").param("centerType", "AUTHOR").param("centerId", "2")
                    .param("depth", "2").param("nodeLimit", "10").param("includeCoauthors", "true"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nodes.length()").value(10))
                    .andExpect(jsonPath("$.edges[?(@.type == 'COAUTHORED')].properties.sharedWorkCount").value(1))
                    .andExpect(jsonPath("$.truncated").value(true));
        } finally {
            neo4jClient.query("MATCH (node) WHERE node.businessId >= 900 AND node.businessId <= 920 DETACH DELETE node").run();
        }
    }

    @Test
    @WithMockUser(authorities = "GRAPH_READ")
    void overviewReturnsAnEmptyGraphWhenNoManagedDomainNodesExist() throws Exception {
        neo4jClient.query("MATCH (node) WHERE node:Author OR node:Achievement SET node.aacvManaged = false").run();
        try {
            mvc.perform(get("/api/v1/graph/overview")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.nodes.length()").value(0))
                    .andExpect(jsonPath("$.edges.length()").value(0))
                    .andExpect(jsonPath("$.truncated").value(false));
        } finally {
            neo4jClient.query("MATCH (node) WHERE node.businessId IN [1, 2] SET node.aacvManaged = true").run();
        }
    }

    @BeforeEach
    void createGraph() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
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
