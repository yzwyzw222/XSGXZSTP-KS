package com.aacv.system.source.infrastructure.openalex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlRunLaunchPort;
import com.aacv.system.crawl.application.port.CrawlSchedulePort;
import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlSchedule;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.ingestion.application.IngestionPageService;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenAlexBatchOrchestrationIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_batch_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @MockitoBean
    private OpenAlexHttpTransport transport;

    @Autowired
    private DataSourceRepository sourceRepository;

    @Autowired
    private CrawlRepository crawlRepository;

    @Autowired
    private CrawlScopeCodec scopeCodec;

    @Autowired
    private CrawlRunLaunchPort launchPort;

    @Autowired
    private CrawlSchedulePort schedulePort;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void launchesOnePageJobPersistsCheckpointAndSynchronizesQuartz() throws Exception {
        when(transport.fetchWorks(any(), any(), any())).thenReturn(new OpenAlexHttpResponse(
                200,
                terminalFixture(),
                null,
                Map.of("X-RateLimit-Remaining", "999")));

        long actorId = createActor();
        DataSourceConfiguration source = createSource();
        CrawlTask task = createTask(source.id(), actorId);
        CrawlRun run = crawlRepository.insertPendingRun(
                task, "00000000-0000-0000-0000-000000000201", actorId);

        launchPort.launchAfterCommit(run.id());

        CrawlRun completed = awaitTerminal(run.id());
        assertEquals(CrawlRunStatus.SUCCEEDED, completed.status());
        assertNotNull(completed.batchJobExecutionId());
        assertEquals(1, completed.readCount());
        assertEquals(1, completed.parsedCount());
        assertEquals(1, completed.createdCount());
        assertEquals(1, completed.requestCount());
        assertEquals(IngestionPageService.TERMINAL_CURSOR, completed.checkpoint());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM achievement", Integer.class));

        CrawlSchedule schedule = new CrawlSchedule(
                0,
                task.id(),
                "crawl-task-" + task.id(),
                LocalTime.of(8, 30),
                ZoneId.of("Asia/Shanghai"),
                Instant.parse("2026-09-03T00:30:00Z"),
                true,
                0);
        schedulePort.synchronizeAfterCommit(schedule);

        assertTrue(scheduler.checkExists(TriggerKey.triggerKey(schedule.scheduleKey(), "aacv-crawl")));

        when(transport.fetchWorks(any(), any(), any())).thenReturn(new OpenAlexHttpResponse(
                200, oversizedTitleFixture(), null, Map.of()));
        CrawlRun failedRecordRun = crawlRepository.insertPendingRun(
                task, "00000000-0000-0000-0000-000000000202", actorId);
        launchPort.launchAfterCommit(failedRecordRun.id());
        CrawlRun partial = awaitTerminal(failedRecordRun.id());
        assertEquals(CrawlRunStatus.PARTIAL_SUCCESS, partial.status());
        assertEquals(1, partial.failureCount());
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT retryable FROM crawl_failure WHERE run_id = ?",
                Boolean.class,
                partial.id()));

        CrawlRun retry = crawlRepository.insertPendingRun(
                task,
                "00000000-0000-0000-0000-000000000203",
                "RETRY_FAILURES",
                actorId,
                partial.id());
        launchPort.launchAfterCommit(retry.id());
        CrawlRun retryCompleted = awaitTerminal(retry.id());
        assertEquals(CrawlRunStatus.PARTIAL_SUCCESS, retryCompleted.status());
        assertEquals(partial.id(), retryCompleted.parentRunId());
        assertEquals(0, retryCompleted.requestCount());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM crawl_failure WHERE run_id = ?",
                Integer.class,
                partial.id()));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crawl_failure", Integer.class));
    }

    private CrawlRun awaitTerminal(long runId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(20);
        CrawlRun current;
        do {
            current = crawlRepository.findRunById(runId).orElseThrow();
            if (CrawlRunStatus.SUCCEEDED == current.status()
                    || CrawlRunStatus.PARTIAL_SUCCESS == current.status()
                    || CrawlRunStatus.FAILED == current.status()) {
                return current;
            }
            Thread.sleep(100);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("Batch运行未在时限内结束，最终状态=" + current.status());
    }

    private long createActor() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, status) VALUES (?, ?, 'ACTIVE')",
                "stage3-batch-user",
                "{noop}not-a-runtime-credential");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                "stage3-batch-user");
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
                        1, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 0, 2 * 1024 * 1024),
                "阶段3 Batch编排测试",
                null,
                null,
                0,
                0,
                now,
                now));
    }

    private CrawlTask createTask(long sourceId, long actorId) {
        Instant now = Instant.now();
        CrawlScope scope = new CrawlScope(null, null, null, List.of(), List.of(), 1, 100);
        return crawlRepository.insertTask(new CrawlTask(
                0,
                sourceId,
                "Batch编排闭环任务",
                scope,
                scopeCodec.hash(scope),
                true,
                0,
                actorId,
                now,
                now));
    }

    private byte[] terminalFixture() throws Exception {
        try (var stream = getClass().getResourceAsStream("/openalex/work-page-sample.json")) {
            String json = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return json.replaceFirst("\\\"next_cursor\\\"\\s*:\\s*\\\"[^\\\"]+\\\"", "\\\"next_cursor\\\": null")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private byte[] oversizedTitleFixture() {
        String json = """
                {"meta":{"count":1,"per_page":100,"next_cursor":null},
                 "results":[{"id":"https://openalex.org/W9000000001","title":"%s","type":"article",
                 "authorships":[],"topics":[],"referenced_works":[]}],"group_by":[]}
                """.formatted("x".repeat(1001));
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
