package com.aacv.system.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@SpringBootTest(properties = {"spring.quartz.auto-startup=false", "aacv.graph.outbox.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CatalogWorkEntitiesIntegrationTests {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42").withDatabaseName("catalog_work_test");
    @Container @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void prepareCatalog() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("INSERT INTO author (id, display_name) VALUES (1, '导师甲'), (2, '导师乙')");
        String[] types = {"patent", "master-thesis", "master-thesis", "doctoral-thesis", "article", "patent", "doctoral-thesis", "master-thesis"};
        for (int i = 0; i < types.length; i++) {
            jdbc.update("""
                    INSERT INTO achievement (id, title_normalized, match_fingerprint, achievement_type, first_seen_at, last_seen_at)
                    VALUES (?, ?, REPEAT('a', 64), ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """, 101 + i, "编目作品" + i, types[i]);
        }
        // 指导关系位于合并成员时仍只返回规范成果；多人指导不能造成重复。
        jdbc.update("INSERT INTO canonical_entity_link (entity_type, entity_id, canonical_entity_id) VALUES ('ACHIEVEMENT', 103, 102)");
        jdbc.update("INSERT INTO achievement_advisor (achievement_id, advisor_id) VALUES (103, 1), (103, 2), (104, 1)");
        jdbc.update("INSERT INTO achievement_author (achievement_id, author_id, author_position) VALUES (107, 1, 1), (108, 1, 1)");
        jdbc.update("INSERT INTO sys_user (id, username, password_hash, status) VALUES (90, 'catalog-test', 'not-a-real-password-hash', 'ACTIVE')");
        jdbc.update("INSERT INTO data_revision (id, entity_type, entity_id, revision_action, reason) VALUES (1, 'ACHIEVEMENT', 105, 'FIELD_OVERRIDE', '测试规范字段')");
        jdbc.update("""
                INSERT INTO manual_field_override (achievement_id, field_name, field_value, revision_id, actor_user_id, reason) VALUES
                (105, 'type', JSON_QUOTE('patent'), 1, 90, '测试'),
                (105, 'title', JSON_QUOTE('修订专利名称'), 1, 90, '测试'),
                (106, 'type', JSON_QUOTE('article'), 1, 90, '测试')
                """);
    }

    @Test @WithMockUser(authorities = "CATALOG_READ")
    void listsCanonicalWorkTypesAndRequiresActualSupervision() throws Exception {
        mvc.perform(get("/api/v1/catalog/patents")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/catalog/master-theses")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(102))
                .andExpect(jsonPath("$.items[0].displayName").value("编目作品1"))
                .andExpect(jsonPath("$.items[0].entityType").value("MASTER_THESIS"));
        mvc.perform(get("/api/v1/catalog/doctoral-theses")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(104))
                .andExpect(jsonPath("$.items[0].entityType").value("DOCTORAL_THESIS"));
        mvc.perform(get("/api/v1/catalog/authors")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test @WithMockUser(authorities = "CATALOG_READ")
    void searchesEffectiveTitleAndSupportsPaginationAndInvalidInput() throws Exception {
        mvc.perform(get("/api/v1/catalog/patents").param("name", "修订"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(105))
                .andExpect(jsonPath("$.items[0].displayName").value("修订专利名称"))
                .andExpect(jsonPath("$.items[0].entityType").value("PATENT"));
        mvc.perform(get("/api/v1/catalog/patents").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/catalog/patents").param("name", "不存在或' OR 1=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/catalog/patents").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/master-theses").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/catalog/unknown-theses")).andExpect(status().isNotFound());
    }

    @Test @WithMockUser(authorities = "GRAPH_READ")
    void preservesCatalogPermissionForAllNewCollections() throws Exception {
        for (String collection : new String[] {"patents", "master-theses", "doctoral-theses"}) {
            mvc.perform(get("/api/v1/catalog/" + collection)).andExpect(status().isForbidden());
        }
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/catalog/patents")).andExpect(status().isUnauthorized());
    }
}
