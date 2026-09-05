package com.aacv.system.source.infrastructure.openalex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.CrawlRunService;
import com.aacv.system.crawl.domain.CrawlCompletionReason;
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

    @Autowired
    private CrawlRunService runService;

    @Test
    @org.springframework.security.test.context.support.WithMockUser(authorities = "CRAWL_TASK_CONTROL")
    void launchesOnePageJobPersistsCheckpointAndSynchronizesQuartz() throws Exception {
        scheduler.standby();
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
        assertEquals(CrawlCompletionReason.SOURCE_EXHAUSTED, completed.completionReason());
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

        byte[] withNextCursor = new String(terminalFixture(), java.nio.charset.StandardCharsets.UTF_8)
                .replaceFirst("\"next_cursor\"\\s*:\\s*null", "\"next_cursor\":\"quota-next\"")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(transport.fetchWorks(any(), any(), any())).thenReturn(new OpenAlexHttpResponse(200, withNextCursor, null, Map.of()));
        CrawlRun limited = crawlRepository.insertPendingRun(task, java.util.UUID.randomUUID().toString(), actorId);
        launchPort.launchAfterCommit(limited.id());
        CrawlRun limitedCompleted = awaitTerminal(limited.id());
        assertEquals(CrawlRunStatus.PARTIAL_SUCCESS, limitedCompleted.status());
        assertEquals(CrawlCompletionReason.PAGE_LIMIT, limitedCompleted.completionReason());
        assertEquals(0, limitedCompleted.failureCount());

        CrawlScope quotaScope = new CrawlScope(null, null, null, List.of(), List.of(), 5, 500);
        CrawlTask quotaTask = crawlRepository.insertTask(new CrawlTask(0, source.id(), "额度恢复测试", quotaScope,
                scopeCodec.hash(quotaScope), true, 0, actorId, Instant.now(), Instant.now()));
        OpenAlexHttpResponse quotaResponse = new OpenAlexHttpResponse(429, new byte[0], null,
                Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset", "3600"));
        when(transport.fetchWorks(any(), any(), any())).thenReturn(
                new OpenAlexHttpResponse(200, withNextCursor, null, Map.of()), quotaResponse);
        CrawlRun deferred = crawlRepository.insertPendingRun(quotaTask, java.util.UUID.randomUUID().toString(), actorId);
        launchPort.launchAfterCommit(deferred.id());
        CrawlRun paused = awaitStatus(deferred.id(), CrawlRunStatus.PAUSED);
        assertEquals(CrawlCompletionReason.QUOTA_EXHAUSTED, paused.completionReason());
        assertEquals(1, paused.quotaDeferrals());
        assertEquals("quota-next", paused.checkpoint());
        assertEquals(1, paused.readCount());
        assertNull(paused.finishedAt());
        assertFalse(runService.resumeQuotaIfDue(paused.id()));

        makeQuotaDue(paused.id());
        assertTrue(crawlRepository.findDueQuotaRuns(Instant.now(), 20).contains(paused.id()));
        jdbcTemplate.update("UPDATE data_source SET enabled = FALSE WHERE id = ?", source.id());
        assertFalse(crawlRepository.findDueQuotaRuns(Instant.now(), 20).contains(paused.id()));
        jdbcTemplate.update("UPDATE data_source SET enabled = TRUE WHERE id = ?", source.id());
        when(transport.fetchWorks(any(), any(), any())).thenAnswer(invocation -> {
            assertEquals("quota-next", ((com.aacv.system.source.domain.OpaqueCursor) invocation.getArgument(2)).value());
            return new OpenAlexHttpResponse(200, terminalFixture(), null, Map.of());
        });
        assertTrue(runService.resumeQuotaIfDue(paused.id()));
        assertFalse(runService.resumeQuotaIfDue(paused.id()));
        CrawlRun resumed = awaitTerminal(paused.id());
        assertEquals(CrawlRunStatus.SUCCEEDED, resumed.status());
        assertEquals(2, resumed.readCount());
        assertEquals(1, resumed.quotaDeferrals());
        assertNull(resumed.deferredUntil());

        org.mockito.Mockito.doReturn(quotaResponse).when(transport).fetchWorks(any(), any(), any());
        CrawlRun exhausted = crawlRepository.insertPendingRun(quotaTask, java.util.UUID.randomUUID().toString(), actorId);
        launchPort.launchAfterCommit(exhausted.id());
        for (int attempt = 1; attempt <= 3; attempt++) {
            CrawlRun waiting = awaitStatus(exhausted.id(), CrawlRunStatus.PAUSED);
            assertEquals(attempt, waiting.quotaDeferrals());
            makeQuotaDue(waiting.id());
            assertTrue(runService.resumeQuotaIfDue(waiting.id()));
        }
        CrawlRun failedQuota = awaitTerminal(exhausted.id());
        assertEquals(CrawlCompletionReason.QUOTA_RETRY_LIMIT, failedQuota.completionReason());
        assertEquals(CrawlRunStatus.FAILED, failedQuota.status());
        assertEquals(3, failedQuota.quotaDeferrals());
        assertNull(failedQuota.deferredUntil());

        CrawlRun cancelled = crawlRepository.insertPendingRun(quotaTask, java.util.UUID.randomUUID().toString(), actorId);
        launchPort.launchAfterCommit(cancelled.id());
        awaitStatus(cancelled.id(), CrawlRunStatus.PAUSED);
        runService.requestPause(cancelled.id());
        CrawlRun manualPause = crawlRepository.findRunById(cancelled.id()).orElseThrow();
        assertEquals(CrawlCompletionReason.USER_PAUSED, manualPause.completionReason());
        assertNull(manualPause.deferredUntil());
        assertFalse(runService.resumeQuotaIfDue(cancelled.id()));
        CrawlRun cancelledResult = runService.requestCancel(cancelled.id());
        assertEquals(CrawlRunStatus.CANCELLED, cancelledResult.status());
        assertEquals(CrawlCompletionReason.USER_CANCELLED, cancelledResult.completionReason());
        assertNotNull(cancelledResult.finishedAt());
        assertTrue(scheduler.checkExists(TriggerKey.triggerKey("quota-resume", "aacv-crawl")));
    }

    private void makeQuotaDue(long runId) {
        jdbcTemplate.update("UPDATE crawl_run SET deferred_until = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?", runId);
    }

    private CrawlRun awaitStatus(long runId, CrawlRunStatus expected) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            CrawlRun run = crawlRepository.findRunById(runId).orElseThrow();
            if (run.status() == expected) return run;
            if (com.aacv.system.crawl.domain.CrawlRunStateMachine.isTerminal(run.status())) {
                throw new AssertionError("期待" + expected + "，实际为" + run.status());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("等待运行状态超时：" + expected);
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
