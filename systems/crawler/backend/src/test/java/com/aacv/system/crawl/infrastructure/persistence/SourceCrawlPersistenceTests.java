package com.aacv.system.crawl.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlSchedule;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SourceCrawlPersistenceTests {

    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_source_crawl_test");

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
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void sourceTaskRunAndScheduleRoundTripThroughMyBatis() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, status) VALUES (?, ?, 'ACTIVE')",
                "stage3-persistence-user",
                "{noop}not-a-runtime-credential");
        long actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                "stage3-persistence-user");

        DataSourceConfiguration source = sourceRepository.insert(new DataSourceConfiguration(
                0,
                DataSourceConfiguration.OPENALEX_CODE,
                SourceType.OPENALEX,
                DataSourceConfiguration.OPENALEX_BASE_URI,
                true,
                new SourceConnectionSettings(
                        2, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 2, 2 * 1024 * 1024),
                "阶段3持久化测试",
                null,
                null,
                0,
                0,
                NOW,
                NOW));
        CrawlScope scope = new CrawlScope(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 8, 31),
                "graph learning",
                List.of("A1"),
                List.of("I1"),
                5,
                500);
        CrawlTask task = crawlRepository.insertTask(new CrawlTask(
                0,
                source.id(),
                "持久化闭环任务",
                scope,
                scopeCodec.hash(scope),
                true,
                0,
                actorId,
                NOW,
                NOW));

        CrawlRun run = crawlRepository.insertPendingRun(task, "00000000-0000-0000-0000-000000000001", actorId);
        CrawlSchedule schedule = crawlRepository.saveSchedule(new CrawlSchedule(
                        0,
                        task.id(),
                        "crawl-task-" + task.id(),
                        LocalTime.of(8, 0),
                        ZoneId.of("Asia/Shanghai"),
                        Instant.parse("2026-09-03T00:00:00Z"),
                        true,
                        0),
                null).orElseThrow();

        assertEquals(DataSourceConfiguration.OPENALEX_CODE, sourceRepository.findById(source.id()).orElseThrow().sourceCode());
        assertEquals(scope, crawlRepository.findTaskById(task.id()).orElseThrow().scope());
        assertEquals(CrawlRunStatus.PENDING, run.status());
        assertTrue(crawlRepository.hasActiveConflict(source.id(), task.parameterHash()));
        assertEquals(ZoneId.of("Asia/Shanghai"), schedule.timeZone());
        assertEquals(schedule, crawlRepository.findScheduleByTaskId(task.id()).orElseThrow());
        assertTrue(crawlRepository.findRecoveryCandidates().stream().anyMatch(candidate -> candidate.runId() == run.id()));
        var window = new com.aacv.system.crawl.domain.CrawlWindow("ROLLING_PUBLICATION_DATE_WINDOW",
                NOW, NOW.plusSeconds(86400), scope);
        crawlRepository.insertRunWindow(run.id(), window);
        assertEquals(window, crawlRepository.findRunWindow(run.id()).orElseThrow());
        assertEquals(run.id(), crawlRepository.findLatestWindowRun(task.id(), window.mode()).orElseThrow());
        assertEquals(run.id(), crawlRepository.findRunPage(task.id(), 0, 20).items().getFirst().id());
        assertEquals(1, crawlRepository.findRunPage(task.id(), 0, 20).totalElements());
        var disabled = crawlRepository.saveSchedule(new CrawlSchedule(schedule.id(), task.id(), schedule.scheduleKey(),
                schedule.localTime(), schedule.timeZone(), schedule.incrementalMode(), null, false, schedule.version()),
                schedule.version()).orElseThrow();
        assertTrue(crawlRepository.findEnabledSchedules().stream().noneMatch(item -> item.taskId() == task.id()));
        assertTrue(!crawlRepository.deleteSchedule(task.id(), schedule.version()));
        assertTrue(crawlRepository.deleteSchedule(task.id(), disabled.version()));
        assertTrue(crawlRepository.findScheduleByTaskId(task.id()).isEmpty());
        jdbcTemplate.update("INSERT INTO BATCH_JOB_INSTANCE (JOB_INSTANCE_ID, VERSION, JOB_NAME, JOB_KEY) VALUES (123, 0, 'retry-test', 'retry-test')");
        jdbcTemplate.update("INSERT INTO BATCH_JOB_EXECUTION (JOB_EXECUTION_ID, VERSION, JOB_INSTANCE_ID, CREATE_TIME, STATUS) VALUES (123, 0, 123, UTC_TIMESTAMP(6), 'FAILED')");
        jdbcTemplate.update("UPDATE crawl_run SET status = 'FAILED', batch_job_execution_id = 123, finished_at = UTC_TIMESTAMP(6) WHERE id = ?", run.id());
        CrawlRun retry = crawlRepository.transitionRun(run.id(), CrawlRunStatus.FAILED, CrawlRunStatus.RUNNING,
                null, null, false, false).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(retry.batchJobExecutionId());
        org.junit.jupiter.api.Assertions.assertNull(retry.finishedAt());
    }
}
