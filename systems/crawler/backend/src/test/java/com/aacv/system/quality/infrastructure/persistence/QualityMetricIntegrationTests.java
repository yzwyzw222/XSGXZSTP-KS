package com.aacv.system.quality.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.ingestion.application.IngestionPageService;
import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.quality.application.port.QualityMetricRepository;
import com.aacv.system.quality.domain.QualityMetric;
import com.aacv.system.quality.domain.QualityMetricCode;
import com.aacv.system.quality.domain.QualityMetricDetail;
import com.aacv.system.quality.domain.QualityMetricQuery;
import com.aacv.system.shared.domain.PageResult;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourcePage;
import com.aacv.system.source.domain.SourceType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class QualityMetricIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_quality_test");

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
    private QualityMetricRepository qualityMetricRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ingestionAccumulatesRunMetricsAndReturnsLimitedEvidenceWithoutPayload() {
        long actorId = insertActor();
        DataSourceConfiguration source = insertSource();
        CrawlTask task = insertTask(source.id(), actorId);
        CrawlRun run = crawlRepository.insertPendingRun(task, UUID.randomUUID().toString(), actorId);
        RawSourceRecord record = new RawSourceRecord(
                SourceType.OPENALEX,
                "https://openalex.org/W9001",
                URI.create("https://api.openalex.org/works/W9001"),
                """
                {"id":"https://openalex.org/W9001","type":"article","authorships":[],"topics":[],"referenced_works":[]}
                """,
                Instant.parse("2026-09-02T06:00:00Z"));

        ingestionPageService.processPage(
                SourceType.OPENALEX, source.id(), run.id(),
                new SourcePage(List.of(record), null, 1, Map.of()));

        PageResult<QualityMetric> metrics = qualityMetricRepository.findPage(
                new QualityMetricQuery(source.id(), run.id(), null), 0, 20);
        assertEquals(11, metrics.totalElements());
        QualityMetric missingTitle = metrics.items().stream()
                .filter(metric -> metric.metricCode() == QualityMetricCode.MISSING_TITLE)
                .findFirst()
                .orElseThrow();
        assertEquals(1, missingTitle.numerator());
        assertEquals(1, missingTitle.denominator());
        QualityMetric noAuthors = metrics.items().stream()
                .filter(metric -> metric.metricCode() == QualityMetricCode.AUTHORS_WITHOUT_STABLE_ID)
                .findFirst()
                .orElseThrow();
        assertEquals(0, noAuthors.denominator());
        assertNull(noAuthors.metricValue());

        QualityMetricDetail detail = qualityMetricRepository.findDetail(missingTitle.id(), 1).orElseThrow();
        assertEquals(1, detail.samples().size());
        assertEquals(record.externalRecordId(), detail.samples().getFirst().externalRecordId());
        assertEquals(Map.of(), detail.samples().getFirst().evidence());
    }

    private long insertActor() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, status) VALUES (?, ?, 'ACTIVE')",
                "stage4-quality-user", "{noop}integration-only");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'stage4-quality-user'", Long.class);
    }

    private DataSourceConfiguration insertSource() {
        Instant now = Instant.now();
        return sourceRepository.insert(new DataSourceConfiguration(
                0,
                DataSourceConfiguration.OPENALEX_CODE,
                SourceType.OPENALEX,
                DataSourceConfiguration.OPENALEX_BASE_URI,
                true,
                new SourceConnectionSettings(
                        1, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 1, 2 * 1024 * 1024),
                "阶段4质量指标集成测试",
                null,
                null,
                0,
                0,
                now,
                now));
    }

    private CrawlTask insertTask(long sourceId, long actorId) {
        Instant now = Instant.now();
        CrawlScope scope = new CrawlScope(null, null, null, List.of(), List.of(), 1, 1);
        return crawlRepository.insertTask(new CrawlTask(
                0, sourceId, "质量指标任务", scope, 1, scopeCodec.hash(scope),
                true, 0, actorId, now, now));
    }
}
