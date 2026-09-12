package com.aacv.system.authorimport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aacv.system.authorimport.application.AuthorImportService;
import com.aacv.system.authorimport.application.ScholarImportParser;
import com.aacv.system.authorimport.domain.ImportOptions;
import com.aacv.system.authorimport.domain.ImportSummary;
import com.aacv.system.authororcid.infrastructure.OrcidQuartzJob;
import com.aacv.system.catalog.application.CatalogService;
import com.aacv.system.catalog.domain.CatalogQuery;
import com.aacv.system.graph.application.GraphOutboxProcessor;
import com.aacv.system.graph.application.GraphQueryService;
import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.shared.application.ResourceConflictException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {"spring.quartz.auto-startup=false", "aacv.graph.outbox.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthorImportIntegrationTests {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42").withDatabaseName("author_import_test");
    @Container @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();
    @Autowired ScholarImportParser parser;
    @Autowired AuthorImportService imports;
    @Autowired GraphOutboxProcessor outbox;
    @Autowired GraphQueryService graph;
    @Autowired CatalogService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WebApplicationContext context;
    @Autowired com.aacv.system.graph.infrastructure.persistence.MyBatisGraphSnapshotReader snapshots;
    @Autowired com.aacv.system.graph.infrastructure.neo4j.Neo4jProjectionInspector inspector;
    @Autowired org.springframework.data.neo4j.core.Neo4jClient neo4j;
    @Autowired com.aacv.system.analytics.application.AnalyticsService analytics;
    @Autowired Scheduler scheduler;
    @Autowired JdbcTransactionManager transactionManager;

    @BeforeEach
    void useSecurityTestContext() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(authorities = {"AUTHOR_IMPORT", "CATALOG_READ", "GRAPH_READ", "ANALYTICS_READ"})
    void importsPapersPatentsAndBothDegreesThroughMysqlOutboxAndNeo4j() {
        long initialOrcidTasks = count("SELECT COUNT(*) FROM author_orcid_task");
        var options = new ImportOptions("图谱学者", "图谱测试大学", null, 0, 1, "AUTHOR", Map.of());
        String authored = "SrcDatabase-来源库,Title-题名,Author-作者,Organ-单位,Keyword-关键词,Summary-摘要,Year-年\n"
                + "期刊,图谱测试论文,图谱学者;合作作者,图谱测试大学,知识图谱,论文摘要保留,2025\n"
                + "中国专利,图谱测试专利,图谱学者,图谱测试大学,专利分析,专利摘要保留,2024";
        var first = save(authored, options);
        assertEquals(2, first.importedCount());
        var master = save("SrcDatabase-来源库,Title-题名,Author-作者,Organ-单位,Summary-摘要\n硕士学位论文,指导硕论测试,硕士学生,学生所在大学,硕论摘要",
                new ImportOptions("图谱学者", "图谱测试大学", first.authorId(), 0, 1, "MASTER_SUPERVISION", Map.of()));
        var doctor = save("SrcDatabase-来源库,Title-题名,Author-作者,Organ-单位,Summary-摘要\n博士学位论文,指导博论测试,博士学生,学生所在大学,博论摘要",
                new ImportOptions("图谱学者", "图谱测试大学", first.authorId(), 0, 1, "DOCTOR_SUPERVISION", Map.of()));
        assertEquals(first.authorId(), master.authorId()); assertEquals(first.authorId(), doctor.authorId());
        long events = count("SELECT COUNT(*) FROM graph_outbox_event");
        long orcidTasks = count("SELECT COUNT(*) FROM author_orcid_task");
        assertEquals(initialOrcidTasks, orcidTasks);
        assertEquals(first.id(), save(authored, options).id());
        assertEquals(events, count("SELECT COUNT(*) FROM graph_outbox_event"));
        assertEquals(orcidTasks, count("SELECT COUNT(*) FROM author_orcid_task"));
        var duplicate = save(authored + "\n", options);
        assertEquals(0, duplicate.importedCount()); assertEquals(2, duplicate.skippedCount());
        assertTrue(outbox.processBatch() >= 4);
        var view = graph.subgraph(GraphNodeType.AUTHOR, first.authorId(), 2, 100, List.of(), List.of(), null, null, List.of());
        assertEquals(2, view.edges().stream().filter(edge -> edge.type().equals("SUPERVISED")).count());
        assertEquals(2, view.edges().stream().filter(edge -> edge.type().equals("AUTHORED") && edge.source().equals("AUTHOR:" + first.authorId())).count());
        assertTrue(view.edges().stream().anyMatch(edge -> edge.type().equals("PRODUCED_AT")));
        assertTrue(view.edges().stream().anyMatch(edge -> edge.type().equals("AFFILIATED_WITH")));
        assertTrue(view.nodes().stream().anyMatch(node -> "硕论摘要".equals(node.properties().get("abstractText"))));
        assertEquals(4, catalog.findAchievements(new CatalogQuery(null, null, null, null, null, null, null, null,
                0, 20, first.authorId(), null, null, null)).totalElements());
        assertEquals(1, catalog.findAchievements(new CatalogQuery("图谱测试论文", null, "图谱测试大学", null, null, null, null, null, 0, 20)).totalElements());
        Long workId = jdbc.queryForObject("SELECT id FROM achievement WHERE title_original = '图谱测试论文'", Long.class);
        assertEquals("论文摘要保留", catalog.requireAchievement(workId).abstractText());
        var degreeQuery = new com.aacv.system.analytics.domain.AnalyticsQuery(null, null, "master-thesis", null, null, null);
        assertEquals(1, analytics.overview(degreeQuery).value().sourceCount());
        assertEquals(2, analytics.overview(degreeQuery).value().authorCount());
        assertEquals(2, analytics.overview(degreeQuery).value().organizationCount());
        assertTrue(analytics.collaboration(degreeQuery, 20).value().authors().isEmpty());
        Long degreeId = jdbc.queryForObject("SELECT id FROM achievement WHERE title_original = '指导硕论测试'", Long.class);
        Long version = jdbc.queryForObject("SELECT desired_version FROM graph_projection_state WHERE achievement_id = ?", Long.class, degreeId);
        assertTrue(inspector.matches(snapshots.load(degreeId), version));
        neo4j.query("MATCH ()-[r:SUPERVISED {achievementBusinessId: $id}]->() DELETE r").bind(degreeId).to("id").run();
        assertFalse(inspector.matches(snapshots.load(degreeId), version));
        assertEquals("图谱学者;合作作者", ((Map<?, ?>) imports.evidence(workId).getFirst().get("originalColumns")).get("Author-作者"));
        assertEquals(0, count("SELECT COUNT(*) FROM crawl_task"));
        assertEquals(0, count("SELECT COUNT(*) FROM crawl_run"));
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void retiresPersistedOrcidSchedulesWithoutDeletingIdentifiersOrHistory() throws Exception {
        long before = count("SELECT COUNT(*) FROM author_orcid_task");
        var imported = save("Title-题名,Author-作者\n停用查询验证论文,停用查询验证作者",
                new ImportOptions("停用查询验证作者", "", null, 0, 1, "AUTHOR", Map.of()));
        assertEquals(before, count("SELECT COUNT(*) FROM author_orcid_task"));
        jdbc.update("INSERT INTO author_external_id(author_id, id_type, external_id) VALUES (?, 'ORCID', ?)",
                imported.authorId(), "0000-0002-1825-0097");
        jdbc.update("INSERT INTO author_orcid_task(author_id, candidates) VALUES (?, JSON_ARRAY())", imported.authorId());
        var history = jdbc.queryForMap("SELECT * FROM author_orcid_task WHERE author_id = ?", imported.authorId());
        var identifiers = jdbc.queryForList("SELECT * FROM author_external_id WHERE author_id = ?", imported.authorId());
        var previousJobs = scheduler.getJobKeys(GroupMatcher.anyJobGroup());
        var key = new JobKey("author-orcid", "aacv-author-orcid");
        var triggerKey = new TriggerKey(key.getName(), key.getGroup());
        var cleanup = context.getBean("orcidQuartzCleanup", ApplicationRunner.class);

        for (boolean missingDetail : List.of(false, true)) {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                try {
                    var job = JobBuilder.newJob(OrcidQuartzJob.class).withIdentity(key).storeDurably().build();
                    var trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).forJob(job)
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(5).repeatForever()).build();
                    scheduler.scheduleJob(job, trigger);
                } catch (SchedulerException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            if (missingDetail) {
                // 模拟旧启动日志中的主触发器存在、SIMPLE 明细缺失。
                jdbc.update("DELETE FROM QRTZ_SIMPLE_TRIGGERS WHERE SCHED_NAME = ? AND TRIGGER_NAME = ? AND TRIGGER_GROUP = ?",
                        scheduler.getSchedulerName(), triggerKey.getName(), triggerKey.getGroup());
            }
            cleanup.run(new DefaultApplicationArguments());
            cleanup.run(new DefaultApplicationArguments());
            assertFalse(scheduler.checkExists(key));
            assertFalse(scheduler.checkExists(triggerKey));
            assertEquals(previousJobs, scheduler.getJobKeys(GroupMatcher.anyJobGroup()));
            assertEquals(history, jdbc.queryForMap("SELECT * FROM author_orcid_task WHERE author_id = ?", imported.authorId()));
            assertEquals(identifiers, jdbc.queryForList("SELECT * FROM author_external_id WHERE author_id = ?", imported.authorId()));
        }
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void removedOrcidEndpointsCannotQueryOrEnqueueWork() throws Exception {
        for (String path : List.of("", "/overview")) {
            mvc.perform(get("/api/v1/author-orcids" + path)).andExpect(status().isNotFound());
        }
        for (String path : List.of("/backfill", "/apply", "/1/retry", "/1/confirm")) {
            mvc.perform(post("/api/v1/author-orcids" + path).with(csrf())
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void laterIdentityConflictRollsBackEarlierRowsBatchAndOutbox() {
        var original = new ImportOptions("既有作者", "回滚大学", null, 0, 1, "AUTHOR", Map.of());
        save("Title-题名,Author-作者,DOI-DOI\n已有论文,既有作者,10.1234/rollback", original);
        long before = count("SELECT COUNT(*) FROM author_import_batch");
        long events = count("SELECT COUNT(*) FROM graph_outbox_event");
        long orcidTasks = count("SELECT COUNT(*) FROM author_orcid_task");
        var options = new ImportOptions("回滚学者", "回滚大学", null, 0, 1, "AUTHOR", Map.of());
        String text = "Title-题名,Author-作者,DOI-DOI\n必须回滚的新论文,回滚学者,\n已有论文,回滚学者,10.1234/rollback";
        assertThrows(ResourceConflictException.class, () -> save(text, options));
        assertEquals(0, count("SELECT COUNT(*) FROM achievement WHERE title_original = '必须回滚的新论文'"));
        assertEquals(0, count("SELECT COUNT(*) FROM author WHERE display_name = '回滚学者'"));
        assertEquals(before, count("SELECT COUNT(*) FROM author_import_batch"));
        assertEquals(events, count("SELECT COUNT(*) FROM graph_outbox_event"));
        assertEquals(orcidTasks, count("SELECT COUNT(*) FROM author_orcid_task"));
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void concurrentDuplicateRequestsCommitExactlyOnce() throws Exception {
        var options = new ImportOptions("并发学者", "并发大学", null, 0, 1, "AUTHOR", Map.of());
        Callable<ImportSummary> work = () -> save("Title-题名,Author-作者\n并发论文,并发学者", options);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(new DelegatingSecurityContextCallable<>(work));
            var second = pool.submit(new DelegatingSecurityContextCallable<>(work));
            assertEquals(first.get(30, TimeUnit.SECONDS).id(), second.get(30, TimeUnit.SECONDS).id());
        }
        assertEquals(1, count("SELECT COUNT(*) FROM achievement WHERE title_original = '并发论文'"));
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void invalidRowsAndStalePreviewsCannotWrite() {
        var options = new ImportOptions("校验学者", "", null, 0, 1, "AUTHOR", Map.of());
        var invalid = parser.parse("Title-题名,Author-作者\n,校验学者".getBytes(StandardCharsets.UTF_8), "信息.csv", options);
        long before = count("SELECT COUNT(*) FROM author_import_batch");
        assertThrows(IllegalArgumentException.class, () -> imports.save(options, invalid, invalid.preview().previewKey(), "信息.csv"));
        var valid = parser.parse("Title-题名,Author-作者\n测试论文,校验学者".getBytes(StandardCharsets.UTF_8), "信息.csv", options);
        assertThrows(IllegalArgumentException.class, () -> imports.save(options, valid, "old-preview", "信息.csv"));
        assertEquals(before, count("SELECT COUNT(*) FROM author_import_batch"));
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void multipartPreviewAndConfirmUseTheSameReviewedContent() throws Exception {
        var file = new MockMultipartFile("file", "接口.csv", "text/csv", "Title-题名,Author-作者\n接口测试论文,接口学者".getBytes(StandardCharsets.UTF_8));
        String options = json.writeValueAsString(new ImportOptions("接口学者", "", null, 0, 1, "AUTHOR", Map.of()));
        var response = mvc.perform(multipart("/api/v1/author-import/preview").file(file).param("options", options).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        String key = json.readTree(response.getContentAsByteArray()).path("previewKey").asText();
        assertFalse(key.isBlank());
        mvc.perform(multipart("/api/v1/author-import/confirm").file(file).param("options", options).param("previewKey", key).with(csrf()))
                .andExpect(status().isOk());
        assertEquals(1, count("SELECT COUNT(*) FROM achievement WHERE title_original = '接口测试论文'"));
        mvc.perform(multipart("/api/v1/author-import/preview").file(file).param("options", "{broken}").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void aDegreeCannotBeAttachedToAnExistingJournalPaperByDoi() {
        save("Title,Author,DOI\n类型测试期刊论文,类型学者,10.1234/type", new ImportOptions("类型学者", "", null, 0, 1, "AUTHOR", Map.of()));
        assertThrows(ResourceConflictException.class, () -> save("SrcDatabase,Title,Author,DOI\n硕士学位论文,学位论文,学生,10.1234/type",
                new ImportOptions("导师类型测试", "", null, 0, 1, "MASTER_SUPERVISION", Map.of())));
        assertEquals(0, count("SELECT COUNT(*) FROM author WHERE display_name = '导师类型测试'"));
    }

    @Test
    @WithMockUser(authorities = "CATALOG_READ")
    void researchersCannotUploadOrReadImportHistory() throws Exception {
        var file = new MockMultipartFile("file", "信息.csv", "text/csv", "Title,Author\n论文,张三".getBytes(StandardCharsets.UTF_8));
        String options = json.writeValueAsString(ScholarImportParserTests.options("AUTHOR"));
        mvc.perform(multipart("/api/v1/author-import/preview").file(file)
                .param("options", options).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/author-import")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void retiredEndpointsAreAbsentAndCsrfRemainsRequired() throws Exception {
        for (String path : List.of("/sources", "/crawl/tasks", "/duplicate-candidates", "/quality-metrics")) {
            mvc.perform(get("/api/v1" + path)).andExpect(status().isNotFound());
        }
        mvc.perform(multipart("/api/v1/author-import/preview")
                .file(new MockMultipartFile("file", "信息.csv", "text/csv", new byte[] {1}))
                .param("options", "{}")).andExpect(status().isForbidden());
    }

    private ImportSummary save(String text, ImportOptions options) {
        var parsed = parser.parse(text.getBytes(StandardCharsets.UTF_8), "信息.csv", options);
        return imports.save(options, parsed, parsed.preview().previewKey(), "信息.csv");
    }
    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
}
