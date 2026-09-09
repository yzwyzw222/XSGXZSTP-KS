package com.xsyu.academicgraph.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsyu.academicgraph.application.extraction.ExtractionException;
import com.xsyu.academicgraph.domain.academic.Paper;
import com.xsyu.academicgraph.domain.academic.PaperRepository;
import com.xsyu.academicgraph.infrastructure.llm.OpenAiCompatibleClient;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LLM 实体抽取接口的集成测试（真实 MySQL + Neo4j，local profile）。
 * OpenAiCompatibleClient 用 @MockitoBean 替换——不依赖真实 LLM 服务，只验证平台链路：
 *  1. 未登录 → 401（管理接口与只读 graph-data 都要登录）
 *  2. 已登录缺 CSRF → 403
 *  3. paperIds 含不存在的论文 → 400 VALIDATION_FAILED
 *  4. 成功全链路：异步抽取 → 轮询到 COMPLETED → 台账/关系/研究实体/追加署名全部落库
 *  5. graph-data：节点按 resolved 类型命名（research_、author_ 前缀），边分 extracted/llm 两类
 *  6. 无摘要论文 → 直接 FAILED，且不调用 LLM
 *  7. LLM 连续失败 3 次 → FAILED（重试耗尽）
 *  8. 清理本轮数据（MySQL 按 FK 顺序 + Neo4j 投影节点）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExtractionApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Driver neo4jDriver;

    @Autowired
    private PaperRepository paperRepository;

    /** LLM 客户端换成 Mock：抽取"结果"是内嵌的 fixture JSON，链路其余部分全真实 */
    @MockitoBean
    private OpenAiCompatibleClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String ts = String.valueOf(System.currentTimeMillis());
    private final String toolName = "抽取测试工具" + ts;
    private final String personName = "抽取测试人物" + ts;
    private final String topicName = "抽取测试主题" + ts;
    private final String orgName = "抽取测试机构" + ts;
    private final String paperTitle = "抽取测试论文" + ts;

    private MockHttpSession session;
    private String csrfToken;
    private jakarta.servlet.http.Cookie csrfCookie;

    private Long paperId;
    private Long noAbstractPaperId;

    @BeforeEach
    void resetLlmMock() {
        reset(llmClient);
    }

    /** 模拟 LLM 返回：2 类业务实体 + 1 个研究实体 + 1 个机构 + 1 条关系 */
    private String fixtureLlmResponse() {
        return """
                {
                  "entities": [
                    {"name": "%s", "type": "TOOL", "properties": {"vendor": "test"}},
                    {"name": "%s", "type": "PERSON", "properties": {}},
                    {"name": "%s", "type": "TOPIC", "properties": {}},
                    {"name": "%s", "type": "ORGANIZATION", "properties": {}}
                  ],
                  "relationships": [
                    {"source": "%s", "target": "%s", "type": "USES",
                     "evidence": "the person used the tool in experiments"}
                  ]
                }
                """.formatted(toolName, personName, topicName, orgName, personName, toolName);
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

    /** 2. 未登录：管理接口与只读 graph-data 都 401（GET 先被认证入口拦下） */
    @Test
    @Order(2)
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/extraction/status/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
        mockMvc.perform(get("/api/v1/extraction/graph-data"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    /** 3. 已登录缺 CSRF 头 → 403（POST 先被 CSRF 过滤器拦下） */
    @Test
    @Order(3)
    void triggerWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/extraction/trigger").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paperIds\":[1]}"))
                .andExpect(status().isForbidden());
    }

    /** 4. paperIds 含不存在的论文 → 400 VALIDATION_FAILED（同步校验，异步前拦截） */
    @Test
    @Order(4)
    void unknownPaperIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/extraction/trigger")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paperIds\":[999999999]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    /** 5. 成功全链路：触发 → 202 → 轮询到 COMPLETED → 各表落库断言 */
    @Test
    @Order(5)
    void extractionCompletesAndPersists() throws Exception {
        Paper paper = new Paper();
        paper.setTitle(paperTitle);
        paper.setPaperType(Paper.TYPE_JOURNAL);
        paper.setAbstractText("我们使用 " + toolName + " 构建知识图谱，研究了 " + topicName + "。");
        paper = paperRepository.save(paper);
        paperId = paper.getId();

        when(llmClient.chat(anyString(), anyString())).thenReturn(fixtureLlmResponse());

        mockMvc.perform(post("/api/v1/admin/extraction/trigger")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paperIds\":[%d]}".formatted(paperId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.queuedCount").value(1));

        JsonNode status = pollUntil(paperId, "COMPLETED");
        assertThat(status.path("extractedEntityCount").asLong()).isEqualTo(4);
        assertThat(status.path("extractedRelationshipCount").asLong()).isEqualTo(1);

        // LLM 只调用了一次（无重试）
        verify(llmClient, times(1)).chat(anyString(), anyString());

        // 台账：4 条，各自归并类型正确
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM extracted_entities WHERE paper_id = ?", Integer.class, paperId))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT resolved_entity_type FROM extracted_entities WHERE paper_id = ? AND entity_name = ?",
                String.class, paperId, toolName)).isEqualTo("RESEARCH_ENTITY");
        assertThat(jdbc.queryForObject(
                "SELECT resolved_entity_type FROM extracted_entities WHERE paper_id = ? AND entity_name = ?",
                String.class, paperId, personName)).isEqualTo("AUTHOR");
        // 研究实体表：新建 METHOD/TOOL 行 + 论文关联
        Long toolEntityId = jdbc.queryForObject(
                "SELECT id FROM research_entity WHERE name = ?", Long.class, toolName);
        assertThat(jdbc.queryForObject(
                "SELECT entity_type FROM research_entity WHERE id = ?", String.class, toolEntityId))
                .isEqualTo("TOOL");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM paper_research_entity WHERE paper_id = ? AND research_entity_id = ?",
                Integer.class, paperId, toolEntityId)).isEqualTo(1);
        // PERSON → 追加署名（位次从 1 开始，满足 CHECK > 0 与唯一约束）
        Long personAuthorId = jdbc.queryForObject(
                "SELECT id FROM author WHERE display_name = ?", Long.class, personName);
        assertThat(jdbc.queryForObject(
                "SELECT author_position FROM paper_author WHERE paper_id = ? AND author_id = ?",
                Integer.class, paperId, personAuthorId)).isEqualTo(1);
        // TOPIC → 追加关键词
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM paper_keyword WHERE paper_id = ? AND keyword_id = "
                        + "(SELECT id FROM keyword WHERE name = ?)", Integer.class, paperId, topicName))
                .isEqualTo(1);
        // 关系：端点命中台账，evidence 落库
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM entity_relationship WHERE paper_id = ?", Integer.class, paperId))
                .isEqualTo(1);
    }

    /** 6. graph-data：节点命名规则 + 两类边（登录即可，不需要 ADMIN） */
    @Test
    @Order(6)
    void graphDataReturnsResolvedEndpoints() throws Exception {
        Long toolEntityId = jdbc.queryForObject(
                "SELECT id FROM research_entity WHERE name = ?", Long.class, toolName);

        MvcResult result = mockMvc.perform(get("/api/v1/extraction/graph-data")
                        .param("paperIds", String.valueOf(paperId)).session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        JsonNode nodes = data.path("nodes");
        assertThat(nodes.isArray()).isTrue();
        // research 节点带 entityType，author/机构/关键词节点不带
        JsonNode toolNode = findNode(nodes, "research_" + toolEntityId);
        assertThat(toolNode).isNotNull();
        assertThat(toolNode.path("type").asText()).isEqualTo("research");
        assertThat(toolNode.path("entityType").asText()).isEqualTo("TOOL");

        JsonNode edges = data.path("edges");
        assertThat(edges.isArray()).isTrue();
        // EXTRACTED_FROM：research 节点 → 论文节点
        assertThat(findEdge(edges, "research_" + toolEntityId, "paper_" + paperId, "extracted")).isNotNull();
        // LLM 关系边：person → tool，USES
        Long personAuthorId = jdbc.queryForObject(
                "SELECT id FROM author WHERE display_name = ?", Long.class, personName);
        JsonNode llmEdge = findEdge(edges, "author_" + personAuthorId, "research_" + toolEntityId, "llm");
        assertThat(llmEdge).isNotNull();
        assertThat(llmEdge.path("label").asText()).isEqualTo("USES");
        assertThat(llmEdge.path("confidence").asDouble()).isEqualTo(1.0);
    }

    /** 7. 无摘要论文 → 直接 FAILED，LLM 一次都不调用 */
    @Test
    @Order(7)
    void paperWithoutAbstractFailsFast() throws Exception {
        Paper paper = new Paper();
        paper.setTitle("无摘要论文" + ts);
        paper.setPaperType(Paper.TYPE_JOURNAL);
        paper = paperRepository.save(paper);
        noAbstractPaperId = paper.getId();

        mockMvc.perform(post("/api/v1/admin/extraction/trigger")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paperIds\":[%d]}".formatted(noAbstractPaperId)))
                .andExpect(status().isAccepted());

        pollUntil(noAbstractPaperId, "FAILED");
        verify(llmClient, never()).chat(anyString(), anyString());
    }

    /** 8. LLM 连续失败 3 次 → 重试耗尽 FAILED */
    @Test
    @Order(8)
    void llmRetriesExhaustedThenFailed() throws Exception {
        Paper paper = new Paper();
        paper.setTitle("重试测试论文" + ts);
        paper.setPaperType(Paper.TYPE_JOURNAL);
        paper.setAbstractText("这是一段有摘要但 LLM 服务不可用的测试文本。");
        paper = paperRepository.save(paper);
        Long retryPaperId = paper.getId();

        when(llmClient.chat(anyString(), anyString()))
                .thenThrow(new ExtractionException("模拟 LLM 服务不可用"));

        mockMvc.perform(post("/api/v1/admin/extraction/trigger")
                        .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paperIds\":[%d]}".formatted(retryPaperId)))
                .andExpect(status().isAccepted());

        pollUntil(retryPaperId, "FAILED");
        verify(llmClient, times(3)).chat(anyString(), anyString());
        // 失败不产生台账数据
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM extracted_entities WHERE paper_id = ?", Integer.class, retryPaperId))
                .isZero();
    }

    /** 9. 清理本轮数据：MySQL 按 FK 顺序删行，Neo4j 摘掉投影节点 */
    @Test
    @Order(9)
    void cleanupExtractionData() {
        List<Long> paperIds = new java.util.ArrayList<>();
        if (paperId != null) {
            paperIds.add(paperId);
        }
        if (noAbstractPaperId != null) {
            paperIds.add(noAbstractPaperId);
        }
        Long retryPaperId = jdbc.queryForObject(
                "SELECT id FROM paper WHERE title = ?", Long.class, "重试测试论文" + ts);

        Long personAuthorId = jdbc.queryForObject(
                "SELECT id FROM author WHERE display_name = ?", Long.class, personName);
        Long topicKeywordId = jdbc.queryForObject(
                "SELECT id FROM keyword WHERE name = ?", Long.class, topicName);
        Long orgInstitutionId = jdbc.queryForObject(
                "SELECT id FROM institution WHERE display_name = ?", Long.class, orgName);
        Long toolEntityId = jdbc.queryForObject(
                "SELECT id FROM research_entity WHERE name = ?", Long.class, toolName);

        // 清抽取专用表与 outbox 事件（paper 删除会级联 paper_author/paper_keyword/paper_research_entity 等）
        // 注意：各业务表 AUTO_INCREMENT 各自独立，entity_id 会跨表撞号，必须带 entity_type 一起删
        for (Long pid : paperIds) {
            jdbc.update("DELETE FROM extracted_entities WHERE paper_id = ?", pid);
            jdbc.update("DELETE FROM entity_relationship WHERE paper_id = ?", pid);
            jdbc.update("DELETE FROM graph_sync_event WHERE entity_type = 'PAPER' AND entity_id = ?", pid);
        }
        jdbc.update("DELETE FROM graph_sync_event WHERE entity_type = 'AUTHOR' AND entity_id = ?", personAuthorId);
        jdbc.update("DELETE FROM graph_sync_event WHERE entity_type = 'KEYWORD' AND entity_id = ?", topicKeywordId);
        jdbc.update("DELETE FROM graph_sync_event WHERE entity_type = 'INSTITUTION' AND entity_id = ?", orgInstitutionId);
        jdbc.update("DELETE FROM graph_sync_event WHERE entity_type = 'RESEARCH_ENTITY' AND entity_id = ?", toolEntityId);
        jdbc.update("DELETE FROM paper WHERE id IN (?, ?)", paperId, retryPaperId);
        if (noAbstractPaperId != null) {
            jdbc.update("DELETE FROM paper WHERE id = ?", noAbstractPaperId);
        }
        jdbc.update("DELETE FROM author WHERE id = ?", personAuthorId);
        jdbc.update("DELETE FROM keyword WHERE id = ?", topicKeywordId);
        jdbc.update("DELETE FROM institution WHERE id = ?", orgInstitutionId);
        jdbc.update("DELETE FROM research_entity WHERE id = ?", toolEntityId);

        try (var neoSession = neo4jDriver.session()) {
            neoSession.run("MATCH (n) WHERE n.businessId IN $ids DETACH DELETE n",
                    Values.parameters("ids", List.of(
                            paperId, retryPaperId, personAuthorId, topicKeywordId, orgInstitutionId, toolEntityId)));
        }
    }

    /** 轮询抽取状态直到变成期望值（异步执行，最坏 30 × 200ms = 6 秒） */
    private JsonNode pollUntil(Long paperId, String expected) throws Exception {
        for (int i = 0; i < 30; i++) {
            MvcResult result = mockMvc.perform(get("/api/v1/admin/extraction/status/" + paperId)
                            .session(session).cookie(csrfCookie).header("X-CSRF-TOKEN", csrfToken))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            if (expected.equals(node.path("status").asText())) {
                return node;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("论文 " + paperId + " 的状态在 6 秒内未变为 " + expected);
    }

    private static JsonNode findNode(JsonNode nodes, String id) {
        for (JsonNode node : nodes) {
            if (id.equals(node.path("id").asText())) {
                return node;
            }
        }
        return null;
    }

    private static JsonNode findEdge(JsonNode edges, String source, String target, String kind) {
        for (JsonNode edge : edges) {
            if (source.equals(edge.path("source").asText())
                    && target.equals(edge.path("target").asText())
                    && kind.equals(edge.path("kind").asText())) {
                return edge;
            }
        }
        return null;
    }
}
