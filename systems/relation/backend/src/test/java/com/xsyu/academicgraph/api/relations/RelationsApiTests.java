package com.xsyu.academicgraph.api.relations;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关系分析板块五个接口的集成测试（真实 MySQL + Neo4j，local profile）。
 *
 * 与 ApiIntegrationTests 的"自建数据全流程"不同，本类只做只读查询，
 * 数据依赖库内已有的样例数据（docs/sql/sample-data.sql，id 100-130）——
 * 这些查询是读图库的，而图投影由 GraphSyncService 从 MySQL 同步，先有数据才能断言。
 * 断言只验证结构与"有数据"，不绑定具体条数（样例数据脚本可重灌，条数会变）。
 * 必填参数缺失的 400 契约与 GlobalExceptionHandler 的翻译逻辑一并覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RelationsApiTests {

    @Autowired
    private MockMvc mockMvc;

    /** 时间戳用户名：同一测试类重复运行时不会与上次残留数据冲突 */
    private final String username = "reltester" + System.currentTimeMillis();
    private MockHttpSession session;

    /** 1. 未登录访问关系接口 → 401（与全局 SecurityConfig 规则一致） */
    @Test
    @Order(1)
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/relations/field-partition"))
                .andExpect(status().isUnauthorized());
    }

    /** 2. 注册新用户建立会话（后续用例共用） */
    @Test
    @Order(2)
    void registerEstablishesSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"test123456","displayName":"关系测试员"}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        session = (MockHttpSession) result.getRequest().getSession(false);
    }

    /** 3. 合作者接口：缺 authorId → 400 + 中文提示（必填参数契约） */
    @Test
    @Order(3)
    void coauthorsMissingAuthorIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/relations/coauthors").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("authorId")));
    }

    /** 4. 合作者接口：样例作者李明（id 103）有合作者，返回字段完整 */
    @Test
    @Order(4)
    void coauthorsOfSampleAuthor() throws Exception {
        mockMvc.perform(get("/api/v1/relations/coauthors?authorId=103").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].authorName").isNotEmpty())
                .andExpect(jsonPath("$[0].paperCount").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].paperTitles").isArray())
                .andExpect(jsonPath("$[0].institutions").isArray());
    }

    /** 5. 领域分区接口：返回扁平行（领域 × 作者），按领域字段可分组 */
    @Test
    @Order(5)
    void fieldPartitionReturnsRows() throws Exception {
        mockMvc.perform(get("/api/v1/relations/field-partition?limit=50").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].field").isNotEmpty())
                .andExpect(jsonPath("$[0].authorId").isNumber())
                .andExpect(jsonPath("$[0].paperCount").isNumber());
    }

    /** 6. 作者主题画像：缺 authorId → 400；样例作者叶紫薇（id 101）有关键词画像 */
    @Test
    @Order(6)
    void authorTopicsContract() throws Exception {
        mockMvc.perform(get("/api/v1/relations/author-topics").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/relations/author-topics?authorId=101").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].keyword").isNotEmpty())
                .andExpect(jsonPath("$[0].fieldName").isNotEmpty())
                .andExpect(jsonPath("$[0].years").isArray());
    }

    /** 7. 机构内部作者：缺 institutionId → 400；西安石油大学（id 101）下有作者 */
    @Test
    @Order(7)
    void institutionAuthorsContract() throws Exception {
        mockMvc.perform(get("/api/v1/relations/institution-authors").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/relations/institution-authors?institutionId=101").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].authorName").isNotEmpty())
                .andExpect(jsonPath("$[0].paperCount").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].totalCitations").isNumber());
    }

    /** 8. 机构间合作：样例数据里有跨机构合著簇，返回边表且每对机构只出现一次 */
    @Test
    @Order(8)
    void institutionCollaborationsReturnsEdges() throws Exception {
        mockMvc.perform(get("/api/v1/relations/institution-collaborations?limit=50").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].inst1").isNotEmpty())
                .andExpect(jsonPath("$[0].inst2").isNotEmpty())
                .andExpect(jsonPath("$[0].paperCount").value(greaterThan(0)));
    }
}
