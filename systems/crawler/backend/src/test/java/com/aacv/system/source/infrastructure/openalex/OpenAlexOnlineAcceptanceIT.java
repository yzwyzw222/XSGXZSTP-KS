package com.aacv.system.source.infrastructure.openalex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlRunLaunchPort;
import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenAlexOnlineAcceptanceIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_openalex_online_acceptance");

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
    private CrawlRunLaunchPort launchPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anonymousOnePageScopeCanBeRepeatedWithoutDuplicateCatalogData() throws Exception {
        long actorId = createActor();
        DataSourceConfiguration source = createSource(SourceType.OPENALEX);
        CrawlTask task = createOpenAlexTask(source.id(), actorId);

        CrawlRun first = run(task, actorId, "00000000-0000-0000-0000-000000000301");
        assertTrue(first.status() == CrawlRunStatus.SUCCEEDED
                || first.status() == CrawlRunStatus.PARTIAL_SUCCESS);
        assertTrue(first.readCount() > 0 && first.readCount() <= 5);
        assertTrue(first.requestCount() >= 1);
        assertNotNull(first.checkpoint());
        Map<String, Integer> firstCounts = catalogCounts();
        assertEquals(first.createdCount(), firstCounts.get("achievement").longValue());

        CrawlRun second = run(task, actorId, "00000000-0000-0000-0000-000000000302");
        assertTrue(second.status() == CrawlRunStatus.SUCCEEDED
                || second.status() == CrawlRunStatus.PARTIAL_SUCCESS);
        assertEquals(first.readCount(), second.readCount());
        assertEquals(0, second.createdCount());
        assertEquals(firstCounts, catalogCounts());
        assertEquals(first.readCount(), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM raw_record", Integer.class).longValue());
        assertEquals(first.readCount(), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM achievement_source", Integer.class).longValue());

        String sharedDoi = jdbcTemplate.queryForObject(
                "SELECT doi_normalized FROM achievement WHERE doi_normalized IS NOT NULL ORDER BY id LIMIT 1",
                String.class);
        DataSourceConfiguration crossref = createSource(SourceType.CROSSREF);
        CrawlTask crossrefTask = createCrossrefTask(crossref.id(), actorId, sharedDoi);
        int achievementsBeforeCrossref = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM achievement", Integer.class);

        CrawlRun crossrefFirst = run(
                crossrefTask, actorId, "00000000-0000-0000-0000-000000000401");
        assertTrue(crossrefFirst.status() == CrawlRunStatus.SUCCEEDED
                || crossrefFirst.status() == CrawlRunStatus.PARTIAL_SUCCESS);
        assertEquals(1, crossrefFirst.readCount());
        assertEquals(0, crossrefFirst.createdCount());
        assertEquals(achievementsBeforeCrossref, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM achievement", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM achievement_source source_value
                JOIN achievement ON achievement.id = source_value.achievement_id
                WHERE achievement.doi_normalized = ?
                """, Integer.class, sharedDoi));

        CrawlRun crossrefSecond = run(
                crossrefTask, actorId, "00000000-0000-0000-0000-000000000402");
        assertTrue(crossrefSecond.status() == CrawlRunStatus.SUCCEEDED
                || crossrefSecond.status() == CrawlRunStatus.PARTIAL_SUCCESS);
        assertEquals(0, crossrefSecond.createdCount());
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM achievement_source source_value
                JOIN achievement ON achievement.id = source_value.achievement_id
                WHERE achievement.doi_normalized = ?
                """, Integer.class, sharedDoi));
    }

    private CrawlRun run(CrawlTask task, long actorId, String runNumber) throws InterruptedException {
        CrawlRun pending = crawlRepository.insertPendingRun(task, runNumber, actorId);
        launchPort.launchAfterCommit(pending.id());
        Instant deadline = Instant.now().plusSeconds(45);
        CrawlRun current;
        do {
            current = crawlRepository.findRunById(pending.id()).orElseThrow();
            if (CrawlRunStatus.SUCCEEDED == current.status()
                    || CrawlRunStatus.PARTIAL_SUCCESS == current.status()
                    || CrawlRunStatus.FAILED == current.status()) {
                return current;
            }
            Thread.sleep(100);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("在线OpenAlex运行未在时限内结束，最终状态=" + current.status());
    }

    private long createActor() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, status) VALUES (?, ?, 'ACTIVE')",
                "stage3-online-user",
                "{noop}not-a-runtime-credential");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                "stage3-online-user");
    }

    private DataSourceConfiguration createSource(SourceType sourceType) {
        Instant now = Instant.now();
        return sourceRepository.insert(new DataSourceConfiguration(
                0,
                DataSourceConfiguration.sourceCode(sourceType),
                sourceType,
                DataSourceConfiguration.baseUri(sourceType),
                true,
                new SourceConnectionSettings(
                        1, 1, Duration.ofSeconds(10), Duration.ofSeconds(30), 2, 2 * 1024 * 1024),
                "阶段4双源匿名单页在线验收",
                null,
                null,
                0,
                0,
                now,
                now));
    }

    private CrawlTask createOpenAlexTask(long sourceId, long actorId) {
        Instant now = Instant.now();
        CrawlScope scope = new CrawlScope(
                LocalDate.of(2018, 2, 13),
                LocalDate.of(2018, 2, 13),
                null,
                List.of("A5048491430"),
                List.of(),
                1,
                5);
        return crawlRepository.insertTask(new CrawlTask(
                0,
                sourceId,
                "OpenAlex匿名在线验收",
                scope,
                scopeCodec.hash(scope),
                true,
                0,
                actorId,
                now,
                now));
    }

    private CrawlTask createCrossrefTask(long sourceId, long actorId, String doi) {
        Instant now = Instant.now();
        CrawlScope scope = new CrawlScope(
                null, null, null, List.of(), List.of(),
                List.of(doi), List.of(), List.of(), null, null, 1, 5);
        return crawlRepository.insertTask(new CrawlTask(
                0,
                sourceId,
                "Crossref匿名在线验收",
                scope,
                2,
                scopeCodec.hash(scope),
                true,
                0,
                actorId,
                now,
                now));
    }

    private Map<String, Integer> catalogCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : List.of(
                "achievement", "author", "organization", "venue", "topic",
                "achievement_author", "authorship_organization", "achievement_topic")) {
            counts.put(table, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
        return counts;
    }
}
