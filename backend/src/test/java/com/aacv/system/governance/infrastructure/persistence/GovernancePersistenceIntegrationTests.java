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
