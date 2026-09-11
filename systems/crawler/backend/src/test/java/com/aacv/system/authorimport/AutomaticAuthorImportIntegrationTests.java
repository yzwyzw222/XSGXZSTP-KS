package com.aacv.system.authorimport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.aacv.system.authorimport.application.AuthorImportService;
import com.aacv.system.authorimport.application.ScholarImportParser;
import com.aacv.system.authorimport.application.ScholarBundleParser;
import com.aacv.system.authorimport.domain.ImportBundle;
import com.aacv.system.authorimport.domain.ImportOptions;
import com.aacv.system.authorimport.domain.ImportSummary;
import com.aacv.system.shared.application.ResourceConflictException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {"spring.quartz.auto-startup=false", "aacv.graph.outbox.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AutomaticAuthorImportIntegrationTests {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42").withDatabaseName("automatic_author_import_test");
    @Container @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community").withoutAuthentication();
    @Autowired ScholarImportParser parser;
    @Autowired ScholarBundleParser bundleParser;
    @Autowired AuthorImportService imports;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WebApplicationContext context;

    @BeforeEach
    void useSecurityTestContext() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void automaticallyImportsOneScholarAcrossFilesAndReplaysWithoutDuplicateWrites() throws Exception {
        var options = new ImportBundle.Options("", List.of(new ImportBundle.FileSettings(0, 1, Map.of()), new ImportBundle.FileSettings(0, 1, Map.of())));
        var main = new MockMultipartFile("files", "自动成果.csv", "text/csv", "SrcDatabase,Title,Author,Organ\n期刊,自动主表论文,自动学者,主表大学".getBytes(StandardCharsets.UTF_8));
        var degrees = new MockMultipartFile("files", "自动硕博.csv", "text/csv", "SrcDatabase,Title,Author,Organ\n硕士,自动硕论,硕士学生,硕士大学\n博士,自动博论,博士学生,博士大学".getBytes(StandardCharsets.UTF_8));
        long before = count("SELECT COUNT(*) FROM author_import_batch");
        var response = mvc.perform(multipart("/api/v1/author-import/files/preview").file(degrees).file(main).param("options", json.writeValueAsString(options)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var preview = json.readValue(response.getContentAsByteArray(), ImportBundle.Preview.class);
        assertTrue(preview.canConfirm());
        assertEquals("自动学者", preview.scholarName());
        String key = preview.previewKey();
        for (int i = 0; i < 2; i++) mvc.perform(multipart("/api/v1/author-import/files/confirm").file(degrees).file(main)
                .param("options", json.writeValueAsString(options)).param("previewKey", key).with(csrf())).andExpect(status().isOk());
        assertEquals(before + 3, count("SELECT COUNT(*) FROM author_import_batch"));
        assertEquals(1, count("SELECT COUNT(*) FROM author WHERE display_name = '自动学者'"));
        assertEquals(2, count("SELECT COUNT(*) FROM achievement_advisor r JOIN author a ON a.id = r.advisor_id WHERE a.display_name = '自动学者'"));
        assertEquals(1, count("SELECT COUNT(*) FROM achievement_author r JOIN author a ON a.id = r.author_id WHERE a.display_name = '自动学者'"));
        assertEquals(0, count("SELECT COUNT(*) FROM author_import_affiliation r JOIN author a ON a.id = r.author_id WHERE a.display_name = '自动学者'"));
        mvc.perform(multipart("/api/v1/author-import/files/confirm").file(degrees).file(main).param("options", json.writeValueAsString(options))
                .param("previewKey", "stale-key").with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void laterFileFailureRollsBackEarlierFileAndAutomaticIdentity() {
        save("SrcDatabase,Title,Author,DOI\n期刊,既有期刊冲突,原作者,10.1234/bundle-conflict", new ImportOptions("原作者", "", null, 0, 1, "AUTHOR", Map.of()));
        var files = List.of(new ScholarBundleParser.FileData("主表.csv", "SrcDatabase,Title,Author\n期刊,整批应回滚的论文,整批回滚学者".getBytes(StandardCharsets.UTF_8)),
                new ScholarBundleParser.FileData("硕论.csv", "SrcDatabase,Title,Author,DOI\n硕士,后续冲突硕论,学生,10.1234/bundle-conflict".getBytes(StandardCharsets.UTF_8)));
        var bundle = bundleParser.parse(files, new ImportBundle.Options("", List.of(new ImportBundle.FileSettings(0, 1, Map.of()), new ImportBundle.FileSettings(0, 1, Map.of()))));
        assertTrue(bundle.preview().canConfirm());
        long batches = count("SELECT COUNT(*) FROM author_import_batch");
        long events = count("SELECT COUNT(*) FROM graph_outbox_event");
        assertThrows(ResourceConflictException.class, () -> imports.saveBundle(bundle, bundle.preview().previewKey()));
        assertEquals(0, count("SELECT COUNT(*) FROM achievement WHERE title_original = '整批应回滚的论文'"));
        assertEquals(0, count("SELECT COUNT(*) FROM author WHERE display_name = '整批回滚学者'"));
        assertEquals(batches, count("SELECT COUNT(*) FROM author_import_batch"));
        assertEquals(events, count("SELECT COUNT(*) FROM graph_outbox_event"));
    }

    @Test
    @WithMockUser(authorities = "AUTHOR_IMPORT")
    void recordsCommaCoauthorsAndReusesTheSamePersonForIndependentResearchWithoutFalseAuthorship() {
        String rows = "SrcDatabase,Title,Author\n科技成果,独立署名成果,合作人甲\n期刊,多人合作证据,\"合作主学者,合作人甲,合作人乙\"\n期刊,单人识别证据,合作主学者\n博士,指导证据,指导学生";
        var files = List.of(new ScholarBundleParser.FileData("混合.csv", rows.getBytes(StandardCharsets.UTF_8)));
        var options = new ImportBundle.Options("", List.of(new ImportBundle.FileSettings(0, 1, Map.of())));
        var bundle = bundleParser.parse(files, options);
        var summary = imports.saveBundle(bundle, bundle.preview().previewKey());
        assertEquals(4, summary.importedCount());
        assertEquals(1, count("SELECT COUNT(*) FROM author WHERE display_name = '合作人甲'"));
        assertEquals(3, count("SELECT COUNT(*) FROM achievement_author r JOIN achievement w ON w.id = r.achievement_id WHERE w.title_original = '多人合作证据'"));
        assertEquals(0, count("SELECT COUNT(*) FROM achievement_author r JOIN achievement w ON w.id = r.achievement_id JOIN author a ON a.id = r.author_id WHERE w.title_original IN ('独立署名成果', '指导证据') AND a.display_name = '合作主学者'"));
        assertEquals(1, count("SELECT COUNT(*) FROM achievement_advisor r JOIN achievement w ON w.id = r.achievement_id WHERE w.title_original = '指导证据'"));
        long people = count("SELECT COUNT(*) FROM author");
        imports.saveBundle(bundle, bundle.preview().previewKey());
        assertEquals(people, count("SELECT COUNT(*) FROM author"));
    }

    @Test
    @WithMockUser(authorities = "CATALOG_READ")
    void automaticImportEndpointsKeepPermissionAndCsrfBoundaries() throws Exception {
        var file = new MockMultipartFile("files", "资料.csv", "text/csv", new byte[] {1});
        for (String action : List.of("preview", "confirm")) mvc.perform(multipart("/api/v1/author-import/files/" + action).file(file)
                .param("options", "{}").param("previewKey", "test").with(csrf())).andExpect(status().isForbidden());
    }

    private ImportSummary save(String text, ImportOptions options) {
        var parsed = parser.parse(text.getBytes(StandardCharsets.UTF_8), "信息.csv", options);
        return imports.save(options, parsed, parsed.preview().previewKey(), "信息.csv");
    }
    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
}
