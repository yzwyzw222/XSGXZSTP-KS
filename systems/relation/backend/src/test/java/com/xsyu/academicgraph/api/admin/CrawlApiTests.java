package com.xsyu.academicgraph.api.admin;

import com.xsyu.academicgraph.infrastructure.openalex.OpenAlexClientException;
import com.xsyu.academicgraph.infrastructure.openalex.OpenAlexHttpTransport;
import com.xsyu.academicgraph.infrastructure.openalex.OpenAlexHttpTransport.OpenAlexHttpResponse;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在线爬取接口（POST /api/v1/admin/crawl/openalex）的集成测试。
 * OpenAlexHttpTransport 用 @MockitoBean 替换——不依赖真实网络，只验证本平台链路：
 *  1. 未登录 → 401（/api/v1/admin/** 需要 ADMIN）
 *  2. 已登录但缺 CSRF → 403
 *  3. 空关键词 / 条数超上限 → 400 VALIDATION_FAILED
 *  4. 正常爬取 → 200，论文 + 作者/机构/关键词/渠道落库，引用线索写 paper_reference
 *  5. 同一批数据再爬 → SKIPPED（DOI 判重，幂等）
 *  6. OpenAlex 上游 500 → 502 UPSTREAM_ERROR（不可重试状态直接放弃）
 *  7. 清理：按时间戳名称删除 MySQL 五实体 + 引用行 + Neo4j 投影节点
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrawlApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Driver neo4jDriver;

    /** 传输层换成 Mock：本测试不访问真实 OpenAlex，响应体是内嵌的 fixture JSON */
    @MockitoBean
    private OpenAlexHttpTransport transport;

    private final String ts = String.valueOf(System.currentTimeMillis());
    private final String doi = "10.9999/crawl-" + ts;
    private final String title = "爬取测试论文" + ts;
    private final String authorA = "爬取测试作者A" + ts;
    private final String authorB = "爬取测试作者B" + ts;
    private final String institution = "爬取测试机构" + ts;
    private final String keyword = "crawl topic " + ts;
    private final String venue = "爬取测试刊" + ts;

    private MockHttpSession session;
    private String csrfToken;
    private jakarta.servlet.http.Cookie csrfCookie;

    /** 模拟 OpenAlex /works 搜索响应：结构对齐真实 API（id 必须匹配 https://openalex.org/X数字） */
    private byte[] fixtureBody() {
        String json = """
                {"results":[{"id":"https://openalex.org/W%s","doi":"https://doi.org/%s","title":"%s",
                "type":"article","language":"en","publication_date":"2026-01-15","cited_by_count":7,
                "primary_location":{"source":{"id":"https://openalex.org/S%s","display_name":"%s",
                "issn_l":"9999-9999","type":"journal"}},
                "authorships":[
                  {"author":{"id":"https://openalex.org/A%s1","display_name":"%s","orcid":null},
                   "institutions":[{"id":"https://openalex.org/I%s","display_name":"%s","country_code":"CN","type":"education"}]},
                  {"author":{"id":"https://openalex.org/A%s2","display_name":"%s","orcid":null},"institutions":[]}
                ],
                "topics":[{"id":"https://openalex.org/T%s","display_name":"%s",
                  "subfield":{"display_name":"x"},"field":{"display_name":"爬取测试领域%s"}}],
                "referenced_works":["https://openalex.org/W%s1","https://openalex.org/W%s2"],
                "abstract_inverted_index":{"Hello":[0],"world":[1]}}]}
                """.formatted(ts, doi, title,
                ts, venue,
                ts, authorA, ts, institution,
                ts, authorB,
                ts, keyword, ts,
                ts, ts);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private OpenAlexHttpResponse okResponse() {
        return new OpenAlexHttpResponse(200, fixtureBody(), null, Map.of());
    }

    @Test
    @Order(1)
    void adminLogin() throws Exception {
        MockHttpSession preSession = new MockHttpSession();
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").session(preSession))
                .andExpect(status().isOk())
                .andReturn();
        csrfToken = csrfResult.getResponse().getContentAsString()
                .replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .session(preSession).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        session = (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    /** 2. 未登录访问 → 401（GET 不带 CSRF 先被认证入口拦下；POST 会先被 CSRF 过滤器拦成 403） */
    @Test
    @Order(2)
    void crawlWithoutLoginReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    /** 3. 已登录但缺 CSRF 头 → 403 */
    @Test
    @Order(3)
    void crawlWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/crawl/openalex").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"knowledge graph\",\"maxRecords\":10}"))
                .andExpect(status().isForbidden());
    }

    /** 4. 空关键词 → 400 VALIDATION_FAILED；条数超 25 同样 400（DTO 约束） */
    @Test
    @Order(4)
    void invalidRequestReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/crawl/openalex")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"   \",\"maxRecords\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/admin/crawl/openalex")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"x\",\"maxRecords\":30}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    /** 5. 正常爬取 → 200：1 篇导入，2 作者/1 机构/1 关键词/1 渠道新建，引用线索落库 */
    @Test
    @Order(5)
    void crawlImportsPaperAndReferences() throws Exception {
        when(transport.fetchWorks(anyString(), anyInt())).thenReturn(okResponse());

        mockMvc.perform(post("/api/v1/admin/crawl/openalex")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"crawl test\",\"maxRecords\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.createdAuthors").value(2))
                .andExpect(jsonPath("$.createdInstitutions").value(1))
                .andExpect(jsonPath("$.createdKeywords").value(1))
                .andExpect(jsonPath("$.createdVenues").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("IMPORTED"));

        // 论文类型映射：article + journal → JOURNAL_ARTICLE
        assertThat(jdbc.queryForObject("SELECT paper_type FROM paper WHERE doi = ?", String.class, doi))
                .isEqualTo("JOURNAL_ARTICLE");
        // 引用线索：两条 OpenAlex URL 写进 paper_reference（cited_paper_id 留空）
        Integer refCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM paper_reference WHERE citing_paper_id = "
                        + "(SELECT id FROM paper WHERE doi = ?)", Integer.class, doi);
        assertThat(refCount).isEqualTo(2);
    }

    /** 6. 同一批数据再爬 → SKIPPED（DOI 判重，幂等；不再新建任何关联对象） */
    @Test
    @Order(6)
    void recrawlSameDataIsSkipped() throws Exception {
        when(transport.fetchWorks(anyString(), anyInt())).thenReturn(okResponse());

        mockMvc.perform(post("/api/v1/admin/crawl/openalex")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"crawl test\",\"maxRecords\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.createdAuthors").value(0))
                .andExpect(jsonPath("$.rows[0].status").value("SKIPPED"));
    }

    /** 7. OpenAlex 上游 500（不可重试状态）→ 502 UPSTREAM_ERROR，中文提示 */
    @Test
    @Order(7)
    void upstreamFailureReturns502() throws Exception {
        when(transport.fetchWorks(anyString(), anyInt()))
                .thenThrow(new OpenAlexClientException("HTTP_500", false, 500,
                        "OpenAlex 请求返回 HTTP 500，已放弃"));

        mockMvc.perform(post("/api/v1/admin/crawl/openalex")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"crawl test\",\"maxRecords\":10}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("UPSTREAM_ERROR"));
    }

    /** 8. 清理本轮数据：MySQL 按 FK 安全顺序删行，Neo4j 摘掉投影节点 */
    @Test
    @Order(8)
    void cleanupCrawledData() {
        Long paperId = jdbc.queryForObject("SELECT id FROM paper WHERE doi = ?", Long.class, doi);
        Long authorAId = jdbc.queryForObject("SELECT id FROM author WHERE display_name = ?", Long.class, authorA);
        Long authorBId = jdbc.queryForObject("SELECT id FROM author WHERE display_name = ?", Long.class, authorB);
        Long institutionId = jdbc.queryForObject("SELECT id FROM institution WHERE display_name = ?", Long.class, institution);
        Long keywordId = jdbc.queryForObject("SELECT id FROM keyword WHERE name = ?", Long.class, keyword);
        Long venueId = jdbc.queryForObject("SELECT id FROM venue WHERE display_name = ?", Long.class, venue);

        jdbc.update("DELETE FROM paper_reference WHERE citing_paper_id = ?", paperId);
        jdbc.update("DELETE FROM paper_author WHERE paper_id = ?", paperId);
        jdbc.update("DELETE FROM paper_keyword WHERE paper_id = ?", paperId);
        jdbc.update("DELETE FROM paper WHERE id = ?", paperId);
        jdbc.update("DELETE FROM author WHERE id IN (?, ?)", authorAId, authorBId);
        jdbc.update("DELETE FROM institution WHERE id = ?", institutionId);
        jdbc.update("DELETE FROM keyword WHERE id = ?", keywordId);
        jdbc.update("DELETE FROM venue WHERE id = ?", venueId);

        try (var neoSession = neo4jDriver.session()) {
            neoSession.run("MATCH (n) WHERE n.businessId IN $ids DETACH DELETE n",
                    Values.parameters("ids",
                            List.of(paperId, authorAId, authorBId, institutionId, keywordId, venueId)));
        }
    }
}
