package com.aacv.system.governance.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.governance.application.port.GovernanceRepository;
import com.aacv.system.catalog.application.port.CatalogRepository;
import com.aacv.system.catalog.domain.CatalogQuery;
import com.aacv.system.governance.domain.CandidateStatus;
import com.aacv.system.governance.domain.DuplicateCandidate;
import com.aacv.system.governance.domain.DuplicateCandidateQuery;
import com.aacv.system.governance.domain.FieldOverride;
import com.aacv.system.governance.domain.GovernedEntityType;
import com.aacv.system.governance.domain.MergeDecision;
import com.aacv.system.shared.domain.PageResult;
import java.time.Instant;
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
class GovernancePersistenceIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_governance_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private GovernanceRepository repository;

    @Autowired
    private CatalogRepository catalogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.mybatis.spring.SqlSessionTemplate sqlSessionTemplate;

    @Test
    @Transactional
    void candidateDecisionLinkRevisionAndOverrideRoundTrip() {
        long actorId = insertActor();
        long sourceId = insertSource();
        long leftId = insertAchievement("a");
        long rightId = insertAchievement("b");
        jdbcTemplate.update("""
                INSERT INTO duplicate_candidate (
                    entity_type, left_entity_id, right_entity_id, match_basis,
                    source_id, rule_version, evidence_json, evidence_hash
                ) VALUES ('ACHIEVEMENT', ?, ?, 'FINGERPRINT', ?, 1, JSON_OBJECT('title', '同名成果'), ?)
                """, leftId, rightId, sourceId, "c".repeat(64));
        long candidateId = jdbcTemplate.queryForObject(
                "SELECT id FROM duplicate_candidate WHERE left_entity_id = ? AND right_entity_id = ?",
                Long.class, leftId, rightId);

        PageResult<DuplicateCandidate> page = repository.findCandidates(
                new DuplicateCandidateQuery(
                        GovernedEntityType.ACHIEVEMENT, CandidateStatus.PENDING,
                        sourceId, 1, null, null),
                0, 20);
        assertEquals(1, page.totalElements());
        assertEquals("同名成果", page.items().getFirst().evidence().get("title"));
        assertTrue(repository.entityExists(GovernedEntityType.ACHIEVEMENT, leftId));
        assertTrue(repository.isCanonicalEntity(GovernedEntityType.ACHIEVEMENT, rightId));

        var comparedLeft = repository.findEntityComparison(GovernedEntityType.ACHIEVEMENT, leftId).orElseThrow();
        assertEquals("成果a", comparedLeft.get("displayName"));
        assertEquals(0, ((Number) comparedLeft.get("sourceCount")).intValue());
        assertTrue(comparedLeft.containsKey("doi"));
        assertFalse(repository.hasExplicitVersionRelation(leftId, rightId));

        Instant now = Instant.parse("2026-09-02T05:00:00Z");
        long mergeRevisionId = repository.insertRevision(
                GovernedEntityType.ACHIEVEMENT, leftId, "MERGE", "{}", "{}",
                actorId, "集成测试接受", true, now);
        repository.createCanonicalLink(
                GovernedEntityType.ACHIEVEMENT, leftId, rightId, mergeRevisionId);
        assertFalse(repository.isCanonicalEntity(GovernedEntityType.ACHIEVEMENT, leftId));
        assertTrue(repository.updateCandidateStatus(
                candidateId, CandidateStatus.ACCEPTED, 0).isPresent());
        MergeDecision decision = repository.insertDecision(
                candidateId, "ACCEPT", rightId, mergeRevisionId,
                actorId, "集成测试接受", now);
        assertEquals("ACCEPT", repository.lockDecision(decision.id()).orElseThrow().decision());
        assertTrue(repository.isLatestDecision(candidateId, decision.id()));
        assertEquals(1, catalogRepository.findAchievements(emptyCatalogQuery(), null, null).totalElements());
        assertEquals(
                rightId,
                catalogRepository.findAchievement(leftId).orElseThrow().summary().id());

        long overrideRevisionId = repository.insertRevision(
                GovernedEntityType.ACHIEVEMENT, rightId, "FIELD_OVERRIDE", "{}", "{}",
                actorId, "集成测试修正", true, now);
        FieldOverride override = repository.saveFieldOverride(
                rightId, "title", "\"人工标题\"", overrideRevisionId,
                actorId, "集成测试修正", null, now);
        assertEquals("人工标题", override.value());
        var comparedOverride = repository.findEntityComparison(GovernedEntityType.ACHIEVEMENT, rightId).orElseThrow();
        assertEquals("人工标题", comparedOverride.get("displayName"));
        assertEquals("成果b", comparedOverride.get("sourceTitle"));
        var overriddenDetail = catalogRepository.findAchievement(rightId).orElseThrow();
        assertEquals("人工标题", overriddenDetail.summary().title());
        assertTrue(overriddenDetail.fields().stream()
                .anyMatch(field -> field.fieldName().equals("title") && field.manualOverride()));
        long revertRevisionId = repository.insertRevision(
                GovernedEntityType.ACHIEVEMENT, rightId, "REVERT", "{}", "{}",
                actorId, "集成测试撤销", false, now);
        FieldOverride reverted = repository.deactivateFieldOverride(
                override.id(), revertRevisionId, actorId, "集成测试撤销", override.version(), now);
        assertFalse(reverted.active());
        assertEquals(
                "成果b",
                catalogRepository.findAchievement(rightId).orElseThrow().summary().title());

        repository.removeCanonicalLink(
                GovernedEntityType.ACHIEVEMENT, leftId, mergeRevisionId);
        assertTrue(repository.isCanonicalEntity(GovernedEntityType.ACHIEVEMENT, leftId));
    }

    private CatalogQuery emptyCatalogQuery() {
        return new CatalogQuery(null, null, null, null, null, null, null, null, 0, 20);
    }

    @Test
    @Transactional
    void comparisonSupportsAllGovernedEntityTypesAndAbsentEntities() {
        jdbcTemplate.update("INSERT INTO author (display_name) VALUES ('对照作者')");
        long authorId = jdbcTemplate.queryForObject("SELECT id FROM author WHERE display_name = '对照作者'", Long.class);
        jdbcTemplate.update("INSERT INTO author_external_id (author_id, id_type, external_id) VALUES (?, 'ORCID', '0000-0001-2345-6789')", authorId);
        assertEquals("0000-0001-2345-6789", repository.findEntityComparison(GovernedEntityType.AUTHOR, authorId)
                .orElseThrow().get("orcid"));
        jdbcTemplate.update("INSERT INTO organization (openalex_id, display_name, country_code) VALUES ('https://openalex.org/I9090', '对照机构', 'CN')");
        long organizationId = jdbcTemplate.queryForObject("SELECT id FROM organization WHERE display_name = '对照机构'", Long.class);
        assertEquals("CN", repository.findEntityComparison(GovernedEntityType.ORGANIZATION, organizationId)
                .orElseThrow().get("countryCode"));
        jdbcTemplate.update("INSERT INTO venue (display_name, issn_l) VALUES ('对照期刊', '1234-5678')");
        long venueId = jdbcTemplate.queryForObject("SELECT id FROM venue WHERE display_name = '对照期刊'", Long.class);
        assertEquals("1234-5678", repository.findEntityComparison(GovernedEntityType.VENUE, venueId)
                .orElseThrow().get("issnL"));
        for (var type : GovernedEntityType.values()) {
            assertTrue(repository.findEntityComparison(type, Long.MAX_VALUE).isEmpty());
        }
        long firstWork = insertAchievement("c");
        long lastWork = insertAchievement("d");
        long unknownWork = insertAchievement("e");
        jdbcTemplate.update("UPDATE achievement SET publication_date = '2018-01-01', date_precision = 'YEAR' WHERE id = ?", firstWork);
        jdbcTemplate.update("UPDATE achievement SET publication_date = '2022-07-02', date_precision = 'DAY' WHERE id = ?", lastWork);
        for (long workId : new long[] {firstWork, lastWork, unknownWork}) {
            jdbcTemplate.update("INSERT INTO achievement_author (achievement_id, author_id, author_position) VALUES (?, ?, 1)", workId, authorId);
            jdbcTemplate.update("INSERT INTO authorship_organization (achievement_id, author_id, organization_id) VALUES (?, ?, ?)", workId, authorId, organizationId);
        }
        var beforeMerge = catalogRepository.findEntityEvidence(com.aacv.system.catalog.domain.CatalogEntityKind.AUTHOR,
                authorId).orElseThrow().affiliations().getFirst();
        assertEquals(2018, beforeMerge.firstPublicationYear());
        assertEquals(2022, beforeMerge.lastPublicationYear());
        assertEquals(3, beforeMerge.achievementCount());
        assertEquals(2, beforeMerge.datedAchievementCount());
        long actorId = insertActor();
        long revisionId = repository.insertRevision(GovernedEntityType.ACHIEVEMENT, firstWork, "MERGE", "{}", "{}",
                actorId, "验证规范计数", true, Instant.now());
        repository.createCanonicalLink(GovernedEntityType.ACHIEVEMENT, firstWork, lastWork, revisionId);
        var afterMerge = catalogRepository.findEntityEvidence(com.aacv.system.catalog.domain.CatalogEntityKind.AUTHOR,
                authorId).orElseThrow().affiliations().getFirst();
        assertEquals(2022, afterMerge.firstPublicationYear());
        assertEquals(2, afterMerge.achievementCount());
        assertEquals(1, afterMerge.datedAchievementCount());
        jdbcTemplate.update("UPDATE achievement SET publication_date = NULL WHERE id = ?", lastWork);
        // 测试直接通过JDBC改写数据，需清理同一事务内MyBatis的一级查询缓存。
        sqlSessionTemplate.clearCache();
        var undated = catalogRepository.findEntityEvidence(com.aacv.system.catalog.domain.CatalogEntityKind.AUTHOR,
                authorId).orElseThrow().affiliations().getFirst();
        org.junit.jupiter.api.Assertions.assertNull(undated.firstPublicationYear());
        org.junit.jupiter.api.Assertions.assertNull(undated.lastPublicationYear());
        assertEquals(0, undated.datedAchievementCount());
        assertTrue(catalogRepository.findEntityEvidence(com.aacv.system.catalog.domain.CatalogEntityKind.AUTHOR,
                Long.MAX_VALUE).isEmpty());
    }

    private long insertActor() {
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, status) VALUES (?, ?, 'ACTIVE')",
                "stage4-governance-user", "{noop}integration-only");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'stage4-governance-user'", Long.class);
    }

    private long insertSource() {
        jdbcTemplate.update("""
                INSERT INTO data_source (
                    source_code, source_type, base_url, adapter_code, compliance_note
                ) VALUES ('OPENALEX', 'OPENALEX', 'https://api.openalex.org',
                          'OPENALEX_REST_V1', '阶段4治理集成测试')
                """);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM data_source WHERE source_code = 'OPENALEX'", Long.class);
    }

    private long insertAchievement(String suffix) {
        jdbcTemplate.update("""
                INSERT INTO achievement (
                    title_original, title_normalized, match_fingerprint,
                    achievement_type, first_seen_at, last_seen_at
                ) VALUES (?, ?, ?, 'article', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, "成果" + suffix, "成果" + suffix, suffix.repeat(64));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM achievement WHERE match_fingerprint = ?", Long.class, suffix.repeat(64));
    }
}
