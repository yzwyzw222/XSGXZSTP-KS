package com.aacv.system.graph;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aacv.system.graph.application.GraphPresentationService;
import com.aacv.system.graph.application.GraphQueryService;
import com.aacv.system.graph.domain.AuthorGraphView;
import com.aacv.system.graph.domain.AuthorGraphView.WorkCategory;
import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.graph.domain.GraphView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {"spring.quartz.auto-startup=false", "aacv.graph.outbox.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthorGraphIntegrationTests {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42").withDatabaseName("aacv_author_graph_test");
    @Container @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();

    @Autowired Neo4jClient neo4j;
    @Autowired GraphQueryService queries;
    @Autowired GraphPresentationService presentation;
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void createIsolatedGraph() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // 此连接仅指向本测试类新建的临时容器，不接触业务图谱。
        neo4j.query("MATCH (node) DETACH DELETE node").run();
        neo4j.query("""
                CREATE (root:Author {businessId: 1, name: '中心作者', orcid: 'test-orcid', aacvManaged: true})
                CREATE (partner:Author {businessId: 2, name: '共同作者', aacvManaged: true})
                CREATE (third:Author {businessId: 3, name: '第三作者', aacvManaged: true})
                CREATE (student:Author {businessId: 4, name: '学位论文学生', aacvManaged: true})
                CREATE (:Author {businessId: 5, name: '孤立作者', aacvManaged: true})
                CREATE (external:Author {businessId: 6, name: '非托管作者', aacvManaged: false})
                CREATE (paper:Achievement {businessId: 11, title: '共同论文', achievementType: 'article', publicationDate: date('2020-01-01'), aacvManaged: true})
                CREATE (solo:Achievement {businessId: 12, title: '独立论文', achievementType: 'review', publicationDate: date('2020-01-01'), aacvManaged: true})
                CREATE (patent:Achievement {businessId: 13, title: '共同专利', achievementType: 'patent', publicationDate: date('2024-02-03'), aacvManaged: true})
                CREATE (master:Achievement {businessId: 14, title: '指导硕论', achievementType: 'master-thesis', publicationDate: date('2022-06-01'), aacvManaged: true})
                CREATE (doctor:Achievement {businessId: 15, title: '指导博论', achievementType: 'doctoral-thesis', aacvManaged: true})
                CREATE (own:Achievement {businessId: 16, title: '本人学位论文', achievementType: 'master-thesis', aacvManaged: true})
                CREATE (science:Achievement {businessId: 17, title: '科技成果', achievementType: 'scientific-result', aacvManaged: true})
                CREATE (unmanaged:Achievement {businessId: 18, title: '非托管论文', achievementType: 'article', aacvManaged: false})
                CREATE (untrusted:Achievement {businessId: 19, title: '非托管关系论文', achievementType: 'article', aacvManaged: true})
                CREATE (root)-[:AUTHORED {aacvManaged: true}]->(paper)
                CREATE (root)-[:AUTHORED {aacvManaged: true}]->(solo)
                CREATE (root)-[:AUTHORED {aacvManaged: true}]->(patent)
                CREATE (root)-[:SUPERVISED {aacvManaged: true}]->(master)
                CREATE (root)-[:SUPERVISED {aacvManaged: true}]->(doctor)
                CREATE (root)-[:AUTHORED {aacvManaged: true}]->(own)
                CREATE (root)-[:AUTHORED {aacvManaged: true}]->(science)
                CREATE (root)-[:AUTHORED {aacvManaged: true}]->(unmanaged)
                CREATE (root)-[:AUTHORED {aacvManaged: false}]->(untrusted)
                CREATE (partner)-[:AUTHORED {aacvManaged: true}]->(paper)
                CREATE (third)-[:AUTHORED {aacvManaged: true}]->(paper)
                CREATE (partner)-[:AUTHORED {aacvManaged: true}]->(patent)
                CREATE (student)-[:AUTHORED {aacvManaged: true}]->(master)
                CREATE (external)-[:AUTHORED {aacvManaged: true}]->(solo)
                """).run();
    }

    @Test @WithMockUser(authorities = "GRAPH_READ")
    void achievementsSeparateAuthorshipFromSupervisionAndExcludeOtherEntities() {
        AuthorGraphView result = queries.authorGraph(1, null, false, false, 0, 20);
        assertEquals(5, result.totalWorks());
        assertEquals(List.of("13", "14", "11", "12", "15"), workIds(result));
        assertEquals(6, result.graph().nodes().size());
        assertEquals(2, result.graph().edges().stream().filter(edge -> edge.type().equals("SUPERVISED")).count());
        assertEquals(List.of("14"), workIds(queries.authorGraph(1, WorkCategory.MASTER_THESIS, false, false, 0, 20)));
        assertEquals(List.of("15"), workIds(queries.authorGraph(1, WorkCategory.DOCTORAL_THESIS, false, false, 0, 20)));
        assertEquals(List.of("13"), workIds(queries.authorGraph(1, WorkCategory.PATENT, false, false, 0, 20)));
    }

    @Test @WithMockUser(authorities = "GRAPH_READ")
    void collaborationsOnlyIncludeTheSelectedAuthorAndActualSharedWorks() {
        var result = queries.authorGraph(1, null, true, false, 0, 20);
        assertEquals(2, result.totalWorks());
        GraphView graph = presentation.presentAuthor(result.graph(), true);
        var collaborations = graph.edges().stream().filter(edge -> edge.type().equals("COAUTHORED")).toList();
        assertEquals(2, collaborations.size());
        assertTrue(collaborations.stream().allMatch(edge -> edge.source().equals("AUTHOR:1") || edge.target().equals("AUTHOR:1")));
        assertTrue(graph.edges().stream().noneMatch(edge -> edge.type().equals("SUPERVISED")));
        assertTrue(graph.nodes().stream().noneMatch(node -> Set.of("AUTHOR:4", "AUTHOR:6", "ACHIEVEMENT:12", "ACHIEVEMENT:14").contains(node.id())));
        var partner = collaborations.stream().filter(edge -> edge.target().equals("AUTHOR:2")).findFirst().orElseThrow();
        assertEquals(2, partner.properties().get("sharedWorkCount"));
        assertEquals(Set.of("ACHIEVEMENT:11", "ACHIEVEMENT:13"), new HashSet<>((List<?>) partner.properties().get("sharedWorkIds")));
    }

    @Test @WithMockUser(authorities = "GRAPH_READ")
    void timelineUsesStableDateOrderAndKeepsUnknownDatesLastAcrossPages() {
        assertEquals(List.of("11", "12"), workIds(queries.authorGraph(1, null, false, true, 0, 2)));
        assertEquals(List.of("14", "13"), workIds(queries.authorGraph(1, null, false, true, 1, 2)));
        assertEquals(List.of("15"), workIds(queries.authorGraph(1, null, false, true, 2, 2)));
        var beyond = queries.authorGraph(1, null, false, true, 10, 2);
        assertEquals(5, beyond.totalWorks());
        assertEquals(1, beyond.graph().nodes().size());
        assertTrue(beyond.graph().edges().isEmpty());
    }

    @Test @WithMockUser(authorities = "GRAPH_READ")
    void authorsWithMoreThan300WorksCanVisitEveryPageWithoutDroppingWorks() {
        neo4j.query("""
                MATCH (root:Author {businessId: 1})
                UNWIND range(100, 409) AS id
                CREATE (work:Achievement {businessId: id, title: '分页论文', achievementType: 'article', aacvManaged: true})
                CREATE (root)-[:AUTHORED {aacvManaged: true}]->(work)
                """).run();
        Set<String> ids = new HashSet<>();
        for (int page = 0; page < 7; page++) {
            var result = queries.authorGraph(1, null, false, true, page, 50);
            assertEquals(315, result.totalWorks());
            assertFalse(result.graph().truncated());
            assertTrue(result.graph().nodes().size() <= 51);
            for (String id : workIds(result)) assertTrue(ids.add(id), "成果不能跨页重复");
        }
        assertEquals(315, ids.size());
    }

    @Test @WithMockUser(authorities = "GRAPH_READ")
    void denseCoauthorsKeepAllPageWorksAndReportPartialEvidenceWithoutDanglingEdges() {
        neo4j.query("""
                MATCH (work:Achievement {businessId: 11})
                UNWIND range(100, 409) AS id
                CREATE (author:Author {businessId: id, name: '共同作者', aacvManaged: true})
                CREATE (author)-[:AUTHORED {aacvManaged: true}]->(work)
                """).run();
        var result = queries.authorGraph(1, WorkCategory.PAPER, true, true, 0, 20);
        assertTrue(result.graph().truncated());
        assertEquals(300, result.graph().nodes().size());
        assertEquals(List.of("11"), workIds(result));
        Set<String> ids = new HashSet<>(result.graph().nodes().stream().map(GraphView.Node::id).toList());
        assertTrue(result.graph().edges().stream().allMatch(edge -> ids.contains(edge.source()) && ids.contains(edge.target())));
        assertEquals(298, presentation.presentAuthor(result.graph(), true).edges().stream()
                .filter(edge -> edge.type().equals("COAUTHORED")).count());
    }

    @Test @WithMockUser(authorities = "GRAPH_READ")
    void endpointReportsEmptyAuthorMissingAuthorAndInvalidParameters() throws Exception {
        mvc.perform(get("/api/v1/graph/authors/5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWorks").value(0)).andExpect(jsonPath("$.graph.nodes.length()").value(1));
        mvc.perform(get("/api/v1/graph/authors/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/graph/authors/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/graph/authors/1").param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/graph/authors/1").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/graph/authors/1").param("category", "UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/graph/authors/1").param("category", "PAPER").param("collaborationsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalWorks").value(1))
                .andExpect(jsonPath("$.graph.typeDefinitions").isArray())
                .andExpect(jsonPath("$.graph.edges[?(@.type == 'COAUTHORED')]", hasSize(2)));
    }

    @Test @WithMockUser(authorities = "CATALOG_READ")
    void graphPermissionIsRequired() throws Exception {
        mvc.perform(get("/api/v1/graph/authors/1")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousAccessIsRejected() throws Exception {
        mvc.perform(get("/api/v1/graph/authors/1")).andExpect(status().isUnauthorized());
    }

    private static List<String> workIds(AuthorGraphView result) {
        return result.graph().nodes().stream().filter(node -> node.type() == GraphNodeType.ACHIEVEMENT)
                .map(GraphView.Node::businessId).toList();
    }
}
