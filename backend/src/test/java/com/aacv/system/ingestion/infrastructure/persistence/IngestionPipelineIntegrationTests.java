package com.aacv.system.ingestion.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.catalog.application.port.CatalogRepository;
import com.aacv.system.catalog.domain.CatalogEntityKind;
import com.aacv.system.catalog.domain.CatalogQuery;
import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.ingestion.application.IngestionPageResult;
import com.aacv.system.ingestion.application.IngestionPageService;
import com.aacv.system.ingestion.application.RawPayloadRetentionService;
import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourcePage;
import com.aacv.system.source.domain.SourceType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IngestionPipelineIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_ingestion_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private DataSourceRepository sourceRepository;

    @Autowired
    private CrawlRepository crawlRepository;

    @Autowired
    private CrawlScopeCodec scopeCodec;

    @Autowired
    private IngestionPageService ingestionPageService;

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private RawPayloadRetentionService retentionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void pageTransactionSupportsIdempotencyRowFailureAndSystemRollback() throws Exception {
        long actorId = createActor();
        DataSourceConfiguration source = createSource();
        CrawlTask task = createTask(source.id(), actorId);
        String officialPayload = officialPayload();

        CrawlRun firstRun = createRun(task, actorId, "00000000-0000-0000-0000-000000000101");
        IngestionPageResult first = ingestionPageService.processOpenAlexPage(
                source.id(), firstRun.id(), page(raw("https://openalex.org/W2741809807", officialPayload), "cursor-2"));
        Instant firstSeen = jdbcTemplate.queryForObject(
                "SELECT first_seen_at FROM achievement", Instant.class);

        assertEquals(1, first.createdCount());
        assertEquals(1, count("achievement"));
        assertEquals(1, count("raw_record"));
        assertEquals(2, count("achievement_author"));
        assertEquals(1, count("organization"));
        assertEquals(1, count("topic"));
        assertEquals(2, count("achievement_reference"));
        assertEquals("cursor-2", checkpoint(firstRun.id()));

        CrawlRun duplicateRun = createRun(task, actorId, "00000000-0000-0000-0000-000000000102");
        IngestionPageResult duplicate = ingestionPageService.processOpenAlexPage(
                source.id(), duplicateRun.id(), page(raw("https://openalex.org/W2741809807", officialPayload), null));

        assertEquals(1, duplicate.duplicateCount());
        assertEquals(0, duplicate.createdCount());
        assertEquals(1, count("achievement"));
        assertEquals(firstSeen, jdbcTemplate.queryForObject(
                "SELECT first_seen_at FROM achievement", Instant.class));
        assertEquals(IngestionPageService.TERMINAL_CURSOR, checkpoint(duplicateRun.id()));

        CrawlRun rowFailureRun = createRun(task, actorId, "00000000-0000-0000-0000-000000000103");
        RawSourceRecord mismatched = raw(
                "https://openalex.org/W999",
                "{\"id\":\"https://openalex.org/W998\",\"authorships\":[],\"topics\":[],\"referenced_works\":[]}");
        IngestionPageResult rowFailure = ingestionPageService.processOpenAlexPage(
                source.id(), rowFailureRun.id(), page(mismatched, "after-row-failure"));

        assertEquals(1, rowFailure.failureCount());
        assertEquals(1, count("crawl_failure"));
        assertEquals("FAILED", jdbcTemplate.queryForObject(
                "SELECT parse_status FROM raw_record WHERE external_record_id = ?",
                String.class,
                "https://openalex.org/W999"));
        assertEquals("after-row-failure", checkpoint(rowFailureRun.id()));
        var failures = crawlRepository.findFailurePage(rowFailureRun.id(), 0, 20);
        assertEquals(1, failures.totalElements());
        assertTrue(failures.items().getFirst().retryable());

        CrawlRun systemFailureRun = createRun(task, actorId, "00000000-0000-0000-0000-000000000104");
        String duplicateAuthorPayload = """
                {
                  "id":"https://openalex.org/W777",
                  "title":"Duplicate authorship relation",
                  "type":"article",
                  "authorships":[
                    {"author":{"id":"https://openalex.org/A777","display_name":"Same Author"},"institutions":[]},
                    {"author":{"id":"https://openalex.org/A777","display_name":"Same Author"},"institutions":[]}
                  ],
                  "topics":[],
                  "referenced_works":[]
                }
                """;

        assertThrows(
                DataIntegrityViolationException.class,
                () -> ingestionPageService.processOpenAlexPage(
                        source.id(),
                        systemFailureRun.id(),
                        page(raw("https://openalex.org/W777", duplicateAuthorPayload), "must-not-commit")));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM raw_record WHERE external_record_id = ?",
                Integer.class,
                "https://openalex.org/W777"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crawl_checkpoint WHERE run_id = ?",
                Integer.class,
                systemFailureRun.id()));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT read_count = 0 FROM crawl_run WHERE id = ?",
                Boolean.class,
                systemFailureRun.id()));

        CatalogQuery combinedQuery = new CatalogQuery(
                "state of OA",
                "Heather",
                "OpenAlex",
                2018,
                "article",
                "OPENALEX",
                "PeerJ",
                "scientometrics",
                0,
                20);
        var catalogPage = catalogRepository.findAchievements(combinedQuery, null, null);
        assertEquals(1, catalogPage.totalElements());
        assertEquals(2, catalogPage.items().getFirst().authors().size());
        assertEquals(1, catalogPage.items().getFirst().topics().size());

        long achievementId = catalogPage.items().getFirst().id();
        var detail = catalogRepository.findAchievement(achievementId).orElseThrow();
        assertEquals(2, detail.authorships().size());
        assertEquals(1, detail.authorships().getFirst().organizations().size());
        assertEquals(2, detail.referencedWorkIds().size());
        assertEquals(1, detail.sources().size());
        assertEquals(IngestionPageService.PARSER_VERSION, detail.sources().getFirst().parserVersion());

        var authors = catalogRepository.findEntities(CatalogEntityKind.AUTHOR, "Heather", 0, 20);
        assertEquals(1, authors.totalElements());
        assertEquals(1, authors.items().getFirst().achievementCount());
        var related = catalogRepository.findAchievements(
                new CatalogQuery(null, null, null, null, null, null, null, null, 0, 20),
                CatalogEntityKind.AUTHOR,
                authors.items().getFirst().id());
        assertEquals(1, related.totalElements());
        assertEquals(1, catalogRepository.findEntities(CatalogEntityKind.ORGANIZATION, null, 0, 20).totalElements());
        assertEquals(1, catalogRepository.findEntities(CatalogEntityKind.VENUE, null, 0, 20).totalElements());
        assertEquals(1, catalogRepository.findEntities(CatalogEntityKind.TOPIC, null, 0, 20).totalElements());

        long rawRecordId = detail.sources().getFirst().rawRecordId();
        jdbcTemplate.update(
                "UPDATE raw_record SET payload_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                rawRecordId);
        var cleanup = retentionService.cleanupExpired(1);
        assertEquals(1, cleanup.clearedCount());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM raw_record WHERE id = ? AND payload IS NOT NULL",
                Integer.class,
                rawRecordId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM raw_record WHERE id = ? AND payload_hash IS NOT NULL",
                Integer.class,
                rawRecordId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM achievement_source WHERE raw_record_id = ?",
                Integer.class,
                rawRecordId));
        assertEquals(0, retentionService.cleanupExpired(1).clearedCount());
    }

    private long createActor() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, status) VALUES (?, ?, 'ACTIVE')",
                "stage3-ingestion-user",
                "{noop}not-a-runtime-credential");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                "stage3-ingestion-user");
    }

    private DataSourceConfiguration createSource() {
        Instant now = Instant.now();
        return sourceRepository.insert(new DataSourceConfiguration(
                0,
                DataSourceConfiguration.OPENALEX_CODE,
                SourceType.OPENALEX,
                DataSourceConfiguration.OPENALEX_BASE_URI,
                true,
                new SourceConnectionSettings(
                        2, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 2, 2 * 1024 * 1024),
                "阶段3采集事务测试",
                null,
                null,
                0,
                0,
                now,
                now));
    }

    private CrawlTask createTask(long sourceId, long actorId) {
        Instant now = Instant.now();
        CrawlScope scope = new CrawlScope(null, null, null, List.of(), List.of(), 5, 500);
        return crawlRepository.insertTask(new CrawlTask(
                0,
                sourceId,
                "采集事务任务",
                scope,
                scopeCodec.hash(scope),
                true,
                0,
                actorId,
                now,
                now));
    }

    private CrawlRun createRun(CrawlTask task, long actorId, String runNumber) {
        return crawlRepository.insertPendingRun(task, runNumber, actorId);
    }

    private SourcePage page(RawSourceRecord record, String nextCursor) {
        return new SourcePage(
                List.of(record),
                nextCursor == null ? null : new OpaqueCursor(nextCursor),
                1,
                Map.of());
    }

    private RawSourceRecord raw(String externalId, String payload) {
        return new RawSourceRecord(
                SourceType.OPENALEX,
                externalId,
                URI.create(externalId),
                payload,
                Instant.now());
    }

    private String officialPayload() throws Exception {
        try (var stream = getClass().getResourceAsStream("/openalex/work-page-sample.json")) {
            return objectMapper.writeValueAsString(
                    objectMapper.readTree(java.util.Objects.requireNonNull(stream)).get("results").get(0));
        }
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String checkpoint(long runId) {
        return jdbcTemplate.queryForObject(
                "SELECT cursor_value FROM crawl_checkpoint WHERE run_id = ?",
                String.class,
                runId);
    }
}
