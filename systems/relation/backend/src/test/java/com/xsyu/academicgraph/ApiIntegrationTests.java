package com.xsyu.academicgraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全链路集成测试（真实 MySQL + Neo4j，local profile）。
 * 用 @TestMethodOrder 串成一条"注册 → 建数据 → 改/删 → 退出"的完整业务流，
 * 每个用例验证一个契约点：401/403/409/400 状态码、problem+json 错误体、CSRF 防护。
 * 说明：测试直连本机数据库，测试产生的数据在用例末尾自行清理。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    /** 时间戳用户名：同一测试类重复运行时不会与上次残留数据冲突 */
    private final String username = "tester" + System.currentTimeMillis();
    private final String password = "test123456";
    /** DOI 与关键词名带时间戳：避免上次失败运行残留的数据污染本轮（关键词有唯一约束） */
    private final String doi = "10.9999/it-" + System.currentTimeMillis();
    private final String keywordName = "集成测试词" + System.currentTimeMillis();

    private MockHttpSession session;
    private Cookie csrfCookie;
    private String csrfToken;

    private long authorId;
    private long institutionId;
    private long venueId;
    private long keywordId;
    private long paperId;

    /** 1. 未登录访问受保护接口 → 401 problem+json（AUTH_REQUIRED） */
    @Test
    @Order(1)
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/papers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.title").exists());
    }

    /** 2. 注册成功 → 201 并建立会话；/me 能取回当前用户与角色 */
    @Test
    @Order(2)
    void registerEstablishesSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","displayName":"集成测试员"}
                                """.formatted(username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles").value(hasItem("ANALYST")))
                .andReturn();
        session = (MockHttpSession) result.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    /** 3. 已登录但写请求缺 X-CSRF-TOKEN 头 → 403（CSRF 防护生效） */
    @Test
    @Order(3)
    void writeWithoutCsrfTokenReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/authors")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"无令牌作者\"}"))
                .andExpect(status().isForbidden());
    }

    /** 4. 从 /auth/csrf 取令牌后，带 Cookie + 头创建作者 → 201 */
    @Test
    @Order(4)
    void createAuthorWithCsrfToken() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        csrfToken = csrfResult.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MvcResult result = mockMvc.perform(post("/api/v1/authors")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"测试作者\",\"orcid\":\"0000-0001-2345-6789\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("测试作者"))
                .andReturn();
        authorId = extractId(result);
    }

    /** 5. 创建机构/渠道/关键词（写操作全部走 CSRF 校验） */
    @Test
    @Order(5)
    void createInstitutionVenueKeyword() throws Exception {
        MvcResult inst = mockMvc.perform(post("/api/v1/institutions")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"测试大学\",\"countryCode\":\"CN\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        institutionId = extractId(inst);

        MvcResult venue = mockMvc.perform(post("/api/v1/venues")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"测试学报\",\"venueType\":\"journal\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        venueId = extractId(venue);

        MvcResult keyword = mockMvc.perform(post("/api/v1/keywords")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + keywordName + "\",\"fieldName\":\"计算机\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        keywordId = extractId(keyword);
    }

    /** 6. 创建带署名/机构/渠道/关键词关系的论文 → 201 */
    @Test
    @Order(6)
    void createPaperWithRelations() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/papers")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"集成测试论文","doi":"%s","paperType":"JOURNAL_ARTICLE",
                                 "language":"zh","publicationDate":"2026-01-15","citationCount":3,
                                 "venueId":%d,
                                 "authors":[{"authorId":%d,"position":1,"institutionId":%d}],
                                 "keywordIds":[%d]}
                                """.formatted(doi, venueId, authorId, institutionId, keywordId)))
                .andReturn();
        // 断言失败时把响应体带进异常消息，方便直接看到服务端报错原因
        org.junit.jupiter.api.Assertions.assertEquals(201, result.getResponse().getStatus(),
                "创建论文失败，响应: " + result.getResponse().getContentAsString());
        paperId = extractId(result);
    }

    /** 7. 论文详情与分页列表 → 200 */
    @Test
    @Order(7)
    void getPaperAndList() throws Exception {
        mockMvc.perform(get("/api/v1/papers/" + paperId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doi").value(doi));

        mockMvc.perform(get("/api/v1/papers?page=0&size=5").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    /** 8. 乐观锁：带当前 version 更新成功，再用同一旧 version 更新 → 409 CONFLICT */
    @Test
    @Order(8)
    void updateWithStaleVersionReturns409() throws Exception {
        MvcResult fetched = mockMvc.perform(get("/api/v1/papers/" + paperId).session(session))
                .andExpect(status().isOk())
                .andReturn();
        long currentVersion = readJson(fetched).get("version").asLong();

        mockMvc.perform(put("/api/v1/papers/" + paperId)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"第一次更新\",\"doi\":\"" + doi + "\",\"version\":" + currentVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("第一次更新"));

        mockMvc.perform(put("/api/v1/papers/" + paperId)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"被乐观锁拦截的更新\",\"version\":" + currentVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    /** 9. 重复 DOI 创建 → 400（唯一性校验） */
    @Test
    @Order(9)
    void duplicateDoiReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/papers")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"抢注同一DOI\",\"doi\":\"" + doi + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    /** 10. 非法论文类型 → 400（服务层白名单校验，挡住 ck_paper_type 检查约束的 500） */
    @Test
    @Order(10)
    void invalidPaperTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/papers")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"类型非法的论文\",\"paperType\":\"article\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    /** 11. 普通用户（ANALYST）访问后台管理 → 403 FORBIDDEN（角色隔离） */
    @Test
    @Order(11)
    void analystCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    /** 12. 删除论文 → 204，随后详情 → 404 */
    @Test
    @Order(12)
    void deletePaperThen404() throws Exception {
        mockMvc.perform(delete("/api/v1/papers/" + paperId)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/papers/" + paperId).session(session))
                .andExpect(status().isNotFound());
    }

    /** 13. 清理本轮测试数据（作者/机构/渠道/关键词），保持库内干净 */
    @Test
    @Order(13)
    void cleanupTestData() throws Exception {
        mockMvc.perform(delete("/api/v1/authors/" + authorId)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/institutions/" + institutionId)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/keywords/" + keywordId)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/venues/" + venueId)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());
    }

    /** 14. 退出登录 → 204，原会话再访问 → 401 */
    @Test
    @Order(14)
    void logoutInvalidatesSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    /** 响应体解析为 JSON 树（提取业务 id / version 字段用） */
    private JsonNode readJson(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString());
    }

    private long extractId(MvcResult result) throws Exception {
        return readJson(result).get("id").asLong();
    }
}
