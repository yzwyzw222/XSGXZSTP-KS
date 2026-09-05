package com.aacv.system.ingestion.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.ingestion.application.IngestionPageService;
import com.aacv.system.ingestion.domain.RawSourceRecord;
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
class CrossrefFusionIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_crossref_fusion_test");

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.aacv.system.catalog.application.port.CatalogRepository catalogRepository;

    @Autowired
    private com.aacv.system.governance.application.port.GovernanceRepository governanceRepository;

    @Test
    void deterministicIdentifiersFuseRegardlessOfOrderAndTextOnlyValuesStayCandidates() {
        long actorId = createActor();
        DataSourceConfiguration openAlex = createSource(SourceType.OPENALEX);
        DataSourceConfiguration crossref = createSource(SourceType.CROSSREF);
        CrawlTask openAlexTask = createTask(openAlex.id(), actorId, 1);
        CrawlTask crossrefTask = createTask(crossref.id(), actorId, 2);

        ingest(openAlex, run(openAlexTask, actorId), openAlexRecord(
                "https://openalex.org/W1001", "10.1000/order-a", "OpenAlex A", "OpenAlex abstract A",
                "https://openalex.org/A1001", "https://openalex.org/I1001"));
        ingest(crossref, run(crossrefTask, actorId), crossrefRecord(
                "10.1000/order-a", "Crossref A", "<jats:p>Crossref abstract A</jats:p>",
                "Ada", "Lovelace", "Example University", true));

        ingest(crossref, run(crossrefTask, actorId), crossrefRecord(
                "10.1000/order-b", "Crossref B", "<jats:p>Crossref abstract B</jats:p>",
                "Ada", "Lovelace", "Example University", true));
        ingest(openAlex, run(openAlexTask, actorId), openAlexRecord(
                "https://openalex.org/W1002", "10.1000/order-b", "OpenAlex B", "OpenAlex abstract B",
                "https://openalex.org/A1002", "https://openalex.org/I1002"));

        assertEquals(2, count("achievement"));
        assertEquals(1, count("author"));
        assertEquals(1, count("organization"));
        assertEquals(1, count("venue"));
        assertCanonical("10.1000/order-a", "Crossref A", "OpenAlex abstract A");
        assertCanonical("10.1000/order-b", "Crossref B", "OpenAlex abstract B");
        assertEquals(4, count("achievement_source"));
        long workId = jdbcTemplate.queryForObject("SELECT id FROM achievement WHERE doi_normalized = '10.1000/order-a'", Long.class);
        var sourceSignals = catalogRepository.findAchievement(workId).orElseThrow().sources();
        var openAlexSignals = sourceSignals.stream().filter(value -> value.sourceCode().equals("OPENALEX"))
                .findFirst().orElseThrow().scholarlyMetadata();
        var crossrefSignals = sourceSignals.stream().filter(value -> value.sourceCode().equals("CROSSREF"))
                .findFirst().orElseThrow().scholarlyMetadata();
        assertEquals(12L, openAlexSignals.citedByCount());
        assertEquals(8L, crossrefSignals.citedByCount());
        assertEquals(false, openAlexSignals.retracted());
        org.junit.jupiter.api.Assertions.assertNull(crossrefSignals.retracted());
        org.junit.jupiter.api.Assertions.assertNotNull(openAlexSignals.observedAt());
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM entity_field_provenance provenance
                JOIN data_source source ON source.id = provenance.source_id
                WHERE provenance.entity_type = 'ACHIEVEMENT'
                  AND provenance.field_name = 'title'
                  AND provenance.selected = TRUE
                  AND source.source_type = 'CROSSREF'
                """, Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM entity_field_provenance provenance
                JOIN data_source source ON source.id = provenance.source_id
                WHERE provenance.entity_type = 'ACHIEVEMENT'
                  AND provenance.field_name = 'abstract'
                  AND provenance.selected = TRUE
                  AND source.source_type = 'OPENALEX'
                """, Integer.class));

        ingest(openAlex, run(openAlexTask, actorId), openAlexNoDoiRecord(
                "https://openalex.org/W2001", "Candidate Work", "https://openalex.org/A2001"));
        ingest(openAlex, run(openAlexTask, actorId), openAlexNoDoiRecord(
                "https://openalex.org/W2002", "Candidate Work", "https://openalex.org/A2002"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM achievement WHERE doi_normalized IS NULL", Integer.class));
        assertEquals(1, candidateCount("ACHIEVEMENT", "FINGERPRINT"));
        assertEquals(1, candidateCount("AUTHOR", "TEXT_NAME"));

        ingest(crossref, run(crossrefTask, actorId), crossrefRecord(
                "10.1000/text-org-a", "Text Org A", null,
                "Same", "Name", "Text Only Institute", false));
        ingest(crossref, run(crossrefTask, actorId), crossrefRecord(
                "10.1000/text-org-b", "Text Org B", null,
                "Same", "Name", "Text Only Institute", false));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM organization WHERE display_name = 'Text Only Institute'", Integer.class));
        assertEquals(1, candidateCount("ORGANIZATION", "TEXT_NAME"));

        int achievementsBeforeReplay = count("achievement");
        int candidatesBeforeReplay = count("duplicate_candidate");
        ingest(crossref, run(crossrefTask, actorId), crossrefRecord(
                "10.1000/text-org-b", "Text Org B", null,
                "Same", "Name", "Text Only Institute", false));
        assertEquals(achievementsBeforeReplay, count("achievement"));
        assertEquals(candidatesBeforeReplay, count("duplicate_candidate"));

        long knownOrganizationId = jdbcTemplate.queryForObject(
                "SELECT organization_id FROM organization_external_id WHERE id_type = 'ROR' AND external_id = 'https://ror.org/03yrm5c26'", Long.class);
        var originalNames = catalogRepository.findEntityEvidence(
                com.aacv.system.catalog.domain.CatalogEntityKind.ORGANIZATION, knownOrganizationId).orElseThrow().names();
        assertEquals(2, originalNames.size());
        org.junit.jupiter.api.Assertions.assertTrue(originalNames.stream().allMatch(name -> name.displayName().equals("Example University")));
        ingest(crossref, run(crossrefTask, actorId), crossrefRecord(
                "10.1000/order-a", "Crossref A", "<jats:p>Crossref abstract A</jats:p>",
                "Ada", "Lovelace", "Renamed Research University", true));
        var renamedNames = catalogRepository.findEntityEvidence(
                com.aacv.system.catalog.domain.CatalogEntityKind.ORGANIZATION, knownOrganizationId).orElseThrow().names();
        assertEquals(3, renamedNames.size());
        org.junit.jupiter.api.Assertions.assertTrue(renamedNames.stream().anyMatch(name -> name.displayName().equals("Example University")));
        org.junit.jupiter.api.Assertions.assertTrue(renamedNames.stream().allMatch(name -> !name.firstObservedAt().isAfter(name.lastObservedAt())));
        assertEquals(1, catalogRepository.findEntities(com.aacv.system.catalog.domain.CatalogEntityKind.ORGANIZATION,
                "Renamed Research University", 0, 20).totalElements());
        assertEquals(2, catalogRepository.findAchievements(new com.aacv.system.catalog.domain.CatalogQuery(
                null, null, "Example University", null, null, null, null, null, 0, 20), null, null).totalElements());
        var textOrganizations = catalogRepository.findEntities(com.aacv.system.catalog.domain.CatalogEntityKind.ORGANIZATION,
                "Text Only Institute", 0, 20).items();
        for (var textOrganization : textOrganizations) {
            assertEquals(1, catalogRepository.findEntityEvidence(com.aacv.system.catalog.domain.CatalogEntityKind.ORGANIZATION,
                    textOrganization.id()).orElseThrow().names().size());
        }

        RawSourceRecord citingRecord = crossrefRecord(
                "10.1000/citing", "Citing Work", null,
                "Reference", "Author", "Reference Institute", false);
        ingest(crossref, run(crossrefTask, actorId), withDoiReference(citingRecord, "10.1000/cited"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM achievement_reference WHERE cited_achievement_id IS NOT NULL",
                Integer.class));
        ingest(crossref, run(crossrefTask, actorId), crossrefRecord(
                "10.1000/cited", "Cited Work", null,
                "Cited", "Author", "Cited Institute", false));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM achievement_reference reference_value
                JOIN achievement cited ON cited.id = reference_value.cited_achievement_id
                WHERE reference_value.referenced_id_type = 'DOI'
                  AND reference_value.referenced_id_value = '10.1000/cited'
                  AND cited.doi_normalized = '10.1000/cited'
                """, Integer.class));
        long otherWorkId = jdbcTemplate.queryForObject("SELECT id FROM achievement WHERE doi_normalized = '10.1000/order-b'", Long.class);
        org.junit.jupiter.api.Assertions.assertFalse(governanceRepository.hasExplicitVersionRelation(workId, otherWorkId));
        ingest(crossref, run(crossrefTask, actorId), withVersionRelation(crossrefRecord(
                "10.1000/order-a", "Crossref A", "<jats:p>Crossref abstract A</jats:p>",
                "Ada", "Lovelace", "Renamed Research University", true), "is-preprint-of", "10.1000/order-b"));
        org.junit.jupiter.api.Assertions.assertTrue(governanceRepository.hasExplicitVersionRelation(workId, otherWorkId));
        org.junit.jupiter.api.Assertions.assertTrue(governanceRepository.hasExplicitVersionRelation(otherWorkId, workId));
        org.junit.jupiter.api.Assertions.assertFalse(governanceRepository.hasExplicitVersionRelation(workId, Long.MAX_VALUE));
        ingest(crossref, run(crossrefTask, actorId), withVersionRelation(crossrefRecord(
                "10.1000/order-a", "Crossref A", "<jats:p>Crossref abstract A</jats:p>",
                "Ada", "Lovelace", "Renamed Research University", true), "cites", "10.1000/order-b"));
        org.junit.jupiter.api.Assertions.assertFalse(governanceRepository.hasExplicitVersionRelation(workId, otherWorkId));
    }

    private long createActor() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, status) VALUES (?, ?, 'ACTIVE')",
                "stage4-fusion-user",
                "{noop}not-a-runtime-credential");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'stage4-fusion-user'", Long.class);
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
                        1, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 1, 2 * 1024 * 1024),
                "阶段4确定性融合测试",
                null,
                null,
                0,
                0,
                now,
                now));
    }

    private CrawlTask createTask(long sourceId, long actorId, int parameterVersion) {
        Instant now = Instant.now();
        CrawlScope scope = new CrawlScope(null, null, null, List.of(), List.of(), 5, 500);
        return crawlRepository.insertTask(new CrawlTask(
                0,
                sourceId,
                "融合任务-" + sourceId,
                scope,
                parameterVersion,
                scopeCodec.hash(scope),
                true,
                0,
                actorId,
                now,
                now));
    }

    private CrawlRun run(CrawlTask task, long actorId) {
        return crawlRepository.insertPendingRun(task, UUID.randomUUID().toString(), actorId);
    }

    private void ingest(DataSourceConfiguration source, CrawlRun run, RawSourceRecord record) {
        ingestionPageService.processPage(
                source.sourceType(),
                source.id(),
                run.id(),
                new SourcePage(List.of(record), null, 1, Map.of()));
    }

    private RawSourceRecord openAlexRecord(
            String id,
            String doi,
            String title,
            String abstractWord,
            String authorId,
            String organizationId) {
        String payload = """
                {
                  "id":"%s",
                  "doi":"https://doi.org/%s",
                  "cited_by_count":12,"is_retracted":false,"open_access":{"is_oa":true,"oa_status":"gold"},
                  "title":"%s",
                  "type":"article",
                  "language":"en",
                  "publication_date":"2026-08-01",
                  "primary_location":{"source":{
                    "id":"https://openalex.org/S1001",
                    "display_name":"Journal of Metadata",
                    "issn_l":"2049-3630",
                    "type":"journal"
                  }},
                  "authorships":[{"author":{
                    "id":"%s",
                    "display_name":"Ada Lovelace",
                    "orcid":"https://orcid.org/0000-0003-1613-5981"
                  },"institutions":[{
                    "id":"%s",
                    "display_name":"Example University",
                    "ror":"https://ror.org/03yrm5c26"
                  }]}],
                  "topics":[],
                  "referenced_works":[],
                  "abstract_inverted_index":{"%s":[0]}
                }
                """.formatted(id, doi, title, authorId, organizationId, abstractWord);
        return new RawSourceRecord(SourceType.OPENALEX, id, URI.create(id), payload, Instant.now());
    }

    private RawSourceRecord openAlexNoDoiRecord(String id, String title, String authorId) {
        String payload = """
                {
                  "id":"%s",
                  "title":"%s",
                  "type":"article",
                  "publication_date":"2026-08-01",
                  "authorships":[{"author":{"id":"%s","display_name":"Same Name"},"institutions":[]}],
                  "topics":[],
                  "referenced_works":[]
                }
                """.formatted(id, title, authorId);
        return new RawSourceRecord(SourceType.OPENALEX, id, URI.create(id), payload, Instant.now());
    }

    private RawSourceRecord crossrefRecord(
            String doi,
            String title,
            String abstractText,
            String given,
            String family,
            String organizationName,
            boolean includeIdentifiers) {
        String orcid = includeIdentifiers
                ? "\"ORCID\":\"https://orcid.org/0000-0003-1613-5981\"," : "";
        String ror = includeIdentifiers
                ? "\"id\":[{\"id-type\":\"ROR\",\"id\":\"https://ror.org/03yrm5c26\"}]" : "";
        String abstractField = abstractText == null ? "" : ",\"abstract\":" + quote(abstractText);
        String payload = """
                {
                  "DOI":"%s",
                  "is-referenced-by-count":8,
                  "title":["%s"],
                  "type":"journal-article",
                  "language":"en",
                  "published":{"date-parts":[[2026,8,1]]},
                  "container-title":["Journal of Metadata"],
                  "ISSN":["2049-3630"],
                  "author":[{
                    "given":"%s",
                    "family":"%s",
                    %s
                    "affiliation":[{"name":"%s"%s}]
                  }],
                  "subject":[],
                  "reference":[]%s
                }
                """.formatted(
                doi, title, given, family, orcid, organizationName,
                ror.isEmpty() ? "" : "," + ror,
                abstractField);
        return new RawSourceRecord(
                SourceType.CROSSREF,
                doi,
                URI.create("https://api.crossref.org/works/" + doi),
                payload,
                Instant.now());
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private RawSourceRecord withDoiReference(RawSourceRecord record, String referencedDoi) {
        return new RawSourceRecord(
                record.sourceType(),
                record.externalRecordId(),
                record.sourceLocation(),
                record.payload().replace(
                        "\"reference\":[]",
                        "\"reference\":[{\"DOI\":\"" + referencedDoi + "\"}]"),
                record.fetchedAt());
    }

    private RawSourceRecord withVersionRelation(RawSourceRecord record, String relationType, String targetDoi) {
        return new RawSourceRecord(record.sourceType(), record.externalRecordId(), record.sourceLocation(),
                record.payload().replace("\"reference\":[]", "\"reference\":[],\"relation\":{\"" + relationType
                        + "\":[{\"id-type\":\"doi\",\"id\":\"" + targetDoi + "\"}]}"), record.fetchedAt());
    }

    private void assertCanonical(String doi, String title, String abstractText) {
        assertEquals(title, jdbcTemplate.queryForObject(
                "SELECT title_original FROM achievement WHERE doi_normalized = ?", String.class, doi));
        assertEquals(abstractText, jdbcTemplate.queryForObject("""
                SELECT detail.abstract_text
                FROM paper_detail detail
                JOIN achievement ON achievement.id = detail.achievement_id
                WHERE achievement.doi_normalized = ?
                """, String.class, doi));
    }

    private int candidateCount(String entityType, String matchBasis) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM duplicate_candidate WHERE entity_type = ? AND match_basis = ?",
                Integer.class,
                entityType,
                matchBasis);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
