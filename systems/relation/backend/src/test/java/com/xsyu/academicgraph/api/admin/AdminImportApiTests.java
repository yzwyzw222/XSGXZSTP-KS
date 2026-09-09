package com.xsyu.academicgraph.api.admin;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据导入接口（POST /api/v1/admin/import/papers）的集成测试。
 *
 * 流程：用样例数据里的 admin 账号登录（导入是管理员功能）→ 带 CSRF 上传 JSON 文件：
 *  1. 未带 CSRF 上传 → 403（CSRF 防护对所有写接口生效）
 *  2. 合法文件 → 200，汇总计数正确（新建作者/机构/关键词/渠道各 1）
 *  3. 同一文件再传 → SKIPPED（DOI 判重，幂等）
 *  4. 论文类型非法 → 该条 FAILED、整批不中断（逐条隔离）
 *  5. 文件不是合法 JSON → 400 VALIDATION_FAILED
 *  6. 清理：按时间戳名称删除 MySQL 五实体 + 摘掉 Neo4j 投影节点
 *
 * 所有名字带时间戳，重复运行不会与上次残留数据冲突。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminImportApiTests {

    @Autowired
    private MockMvc mockMvc;

    /** 直接操作数据库做清理（按 id 精确删除，避开业务 API 的级联限制） */
    @Autowired
    private JdbcTemplate jdbc;

    /** Neo4j 驱动：清理已同步到图库的投影节点（MySQL 删除不会自动回删图库） */
    @Autowired
    private Driver neo4jDriver;

    /** 时间戳后缀：本类产生的所有实体名字都带它，清理时按名字定位 */
    private final String ts = String.valueOf(System.currentTimeMillis());
    private final String title = "导入测试论文" + ts;
    private final String doi = "10.9999/import-" + ts;
    private final String authorName = "导入测试作者" + ts;
    private final String institutionName = "导入测试机构" + ts;
    private final String keywordName = "导入测试词" + ts;
    private final String venueName = "导入测试刊" + ts;

    private MockHttpSession session;
    private String csrfToken;
    private jakarta.servlet.http.Cookie csrfCookie;

    /** 合法导入文件的内容（JSON 字符串转字节数组后走 multipart 上传） */
    private String validFileJson() {
        return """
                {"papers":[{"title":"%s","doi":"%s","paperType":"JOURNAL_ARTICLE","language":"zh",
                "publicationDate":"2026-01-15","citationCount":2,
                "venue":{"name":"%s","venueType":"journal"},
                "authors":[{"name":"%s","orcid":"0000-0009-0000-0001",
                "institution":{"name":"%s","countryCode":"CN"}}],
                "keywords":[{"name":"%s","fieldName":"计算机"}]}]}
                """.formatted(title, doi, venueName, authorName, institutionName, keywordName);
    }

    /** 1. admin 登录（导入接口要求 ADMIN 角色），取会话 + CSRF 令牌 */
    @Test
    @Order(1)
    void adminLogin() throws Exception {
        // /auth/csrf 本身不会新建会话，先手动预建一个再请求（登录是会话认证的入口）
        MockHttpSession preSession = new MockHttpSession();
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").session(preSession))
                .andExpect(status().isOk())
                .andReturn();
        csrfToken = csrfResult.getResponse().getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        // 登录成功后从响应取回会话：会话固定保护可能换发新会话 id，旧引用会失效
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .session(preSession).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItem("ADMIN")))
                .andReturn();
        session = (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    /** 2. 已登录但缺 CSRF 头 → 403（写接口的 CSRF 防护同样覆盖管理员接口） */
    @Test
    @Order(2)
    void importWithoutCsrfTokenReturns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.json",
                "application/json", validFileJson().getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/import/papers").file(file).session(session))
                .andExpect(status().isForbidden());
    }

    /** 3. 合法文件 → 200：1 篇导入成功，四类关联对象各新建 1 个 */
    @Test
    @Order(3)
    void importValidFileCreatesPaperAndEntities() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.json",
                "application/json", validFileJson().getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/import/papers").file(file)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.createdAuthors").value(1))
                .andExpect(jsonPath("$.createdInstitutions").value(1))
                .andExpect(jsonPath("$.createdKeywords").value(1))
                .andExpect(jsonPath("$.createdVenues").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("IMPORTED"))
                .andExpect(jsonPath("$.rows[0].paperId").isNumber());
    }

    /** 4. 同一文件重复导入 → SKIPPED（DOI 判重，幂等；不再新建关联对象） */
    @Test
    @Order(4)
    void reimportSameFileIsSkipped() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.json",
                "application/json", validFileJson().getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/import/papers").file(file)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("SKIPPED"));
    }

    /** 5. 论文类型非法 → 该条 FAILED 且不中断整批；复用的作者名不重复新建 */
    @Test
    @Order(5)
    void invalidPaperTypeFailsThatRowOnly() throws Exception {
        String json = """
                {"papers":[
                  {"title":"类型非法论文%s","doi":"10.9999/badtype-%s","paperType":"article",
                   "authors":[{"name":"%s"}]}
                ]}
                """.formatted(ts, ts, authorName);
        MockMultipartFile file = new MockMultipartFile("file", "import.json",
                "application/json", json.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/import/papers").file(file)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.createdAuthors").value(0))
                .andExpect(jsonPath("$.rows[0].status").value("FAILED"))
                .andExpect(jsonPath("$.rows[0].message").value(containsString("论文类型不合法")));
    }

    /** 6. 文件内容不是合法 JSON → 400 VALIDATION_FAILED（中文提示） */
    @Test
    @Order(6)
    void malformedJsonReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "import.json",
                "application/json", "这不是JSON".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/import/papers").file(file)
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(containsString("文件解析失败")));
    }

    /** 7. 清理本轮导入数据：MySQL 按 FK 安全顺序删行，Neo4j 摘掉投影节点 */
    @Test
    @Order(7)
    void cleanupImportedData() {
        Long paperId = jdbc.queryForObject("SELECT id FROM paper WHERE title = ?", Long.class, title);
        Long authorId = jdbc.queryForObject("SELECT id FROM author WHERE display_name = ?", Long.class, authorName);
        Long institutionId = jdbc.queryForObject("SELECT id FROM institution WHERE display_name = ?", Long.class, institutionName);
        Long keywordId = jdbc.queryForObject("SELECT id FROM keyword WHERE name = ?", Long.class, keywordName);
        Long venueId = jdbc.queryForObject("SELECT id FROM venue WHERE display_name = ?", Long.class, venueName);

        // 先删关系行再删主表行，避免外键约束拦截（paper_author/paper_keyword → paper → 其余实体）
        jdbc.update("DELETE FROM paper_author WHERE paper_id = ?", paperId);
        jdbc.update("DELETE FROM paper_keyword WHERE paper_id = ?", paperId);
        jdbc.update("DELETE FROM paper WHERE id = ?", paperId);
        jdbc.update("DELETE FROM author WHERE id = ?", authorId);
        jdbc.update("DELETE FROM institution WHERE id = ?", institutionId);
        jdbc.update("DELETE FROM keyword WHERE id = ?", keywordId);
        jdbc.update("DELETE FROM venue WHERE id = ?", venueId);

        // 图库侧：MySQL 行删掉后，尚未处理的同步事件会被 findById 跳过；
        // 已同步的节点用 DETACH DELETE 摘掉（节点不存在时是空操作，安全）
        try (var neoSession = neo4jDriver.session()) {
            neoSession.run("MATCH (n) WHERE n.businessId IN $ids DETACH DELETE n",
                    Values.parameters("ids", List.of(paperId, authorId, institutionId, keywordId, venueId)));
        }
    }
}
