package com.aacv.system.governance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.governance.application.port.GovernanceRepository;
import com.aacv.system.governance.domain.CandidateStatus;
import com.aacv.system.governance.domain.DuplicateCandidate;
import com.aacv.system.governance.domain.FieldOverride;
import com.aacv.system.governance.domain.GovernedEntityType;
import com.aacv.system.governance.domain.MergeDecision;
import com.aacv.system.graph.application.port.GraphProjectionRequestPort;
import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.shared.application.ResourceConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GovernanceServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-02T04:00:00Z");

    private GovernanceRepository repository;
    private GovernanceService service;
    private GraphProjectionRequestPort graphProjectionRequestPort;

    @BeforeEach
    void setUp() {
        repository = mock(GovernanceRepository.class);
        CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
        when(actorProvider.currentUserId()).thenReturn(OptionalLong.of(7));
        graphProjectionRequestPort = mock(GraphProjectionRequestPort.class);
        service = new GovernanceService(
                repository,
                actorProvider,
                mock(AuditService.class),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                graphProjectionRequestPort);
    }

    @Test
    void acceptCreatesLogicalLinkRevisionAndDecision() {
        DuplicateCandidate candidate = candidate(CandidateStatus.PENDING, 2);
        when(repository.lockCandidate(11)).thenReturn(Optional.of(candidate));
        when(repository.entityExists(GovernedEntityType.ACHIEVEMENT, 20)).thenReturn(true);
        when(repository.entityExists(GovernedEntityType.ACHIEVEMENT, 30)).thenReturn(true);
        when(repository.isCanonicalEntity(GovernedEntityType.ACHIEVEMENT, 20)).thenReturn(true);
        when(repository.isCanonicalEntity(GovernedEntityType.ACHIEVEMENT, 30)).thenReturn(true);
        when(repository.insertRevision(
                any(), anyLong(), eq("MERGE"), any(), any(), eq(7L), eq("确认同一成果"), eq(true), eq(NOW)))
                .thenReturn(41L);
        when(repository.updateCandidateStatus(11, CandidateStatus.ACCEPTED, 2))
                .thenReturn(Optional.of(candidate(CandidateStatus.ACCEPTED, 3)));
        MergeDecision stored = new MergeDecision(
                51, 11, "ACCEPT", 20L, 41, 7, "确认同一成果", 0, NOW);
        when(repository.insertDecision(11, "ACCEPT", 20L, 41, 7, "确认同一成果", NOW))
                .thenReturn(stored);

        MergeDecision result = service.acceptCandidate(11, 20, " 确认同一成果 ", 2);

        assertEquals(stored, result);
        verify(repository).createCanonicalLink(GovernedEntityType.ACHIEVEMENT, 30, 20, 41);
        verify(graphProjectionRequestPort).requestRelated(GraphNodeType.ACHIEVEMENT, java.util.List.of(20L, 30L));
    }

    @Test
    void acceptRejectsCanonicalEntityOutsideCandidatePair() {
        when(repository.lockCandidate(11)).thenReturn(Optional.of(candidate(CandidateStatus.PENDING, 2)));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.acceptCandidate(11, 99, "目标错误", 2));

        verify(repository, never()).createCanonicalLink(any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void staleCandidateCannotBeAcceptedTwice() {
        when(repository.lockCandidate(11)).thenReturn(Optional.of(candidate(CandidateStatus.ACCEPTED, 3)));

        assertThrows(
                ResourceConflictException.class,
                () -> service.acceptCandidate(11, 20, "重复处理", 2));
    }

    @Test
    void rejectPreservesEntitiesAndCreatesReversibleDecision() {
        DuplicateCandidate candidate = candidate(CandidateStatus.PENDING, 2);
        when(repository.lockCandidate(11)).thenReturn(Optional.of(candidate));
        when(repository.insertRevision(
                any(), anyLong(), eq("REJECT"), any(), any(), eq(7L), eq("证据不足"), eq(true), eq(NOW)))
                .thenReturn(42L);
        when(repository.updateCandidateStatus(11, CandidateStatus.REJECTED, 2))
                .thenReturn(Optional.of(candidate(CandidateStatus.REJECTED, 3)));
        MergeDecision stored = new MergeDecision(
                52, 11, "REJECT", null, 42, 7, "证据不足", 0, NOW);
        when(repository.insertDecision(11, "REJECT", null, 42, 7, "证据不足", NOW))
                .thenReturn(stored);

        assertEquals(stored, service.rejectCandidate(11, "证据不足", 2));
        verify(repository, never()).createCanonicalLink(any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void mergeWithLaterDependencyCannotBeReverted() {
        MergeDecision decision = new MergeDecision(
                51, 11, "ACCEPT", 20L, 41, 7, "确认", 0, NOW);
        when(repository.lockDecision(51)).thenReturn(Optional.of(decision));
        when(repository.isLatestDecision(11, 51)).thenReturn(true);
        when(repository.lockCandidate(11)).thenReturn(Optional.of(candidate(CandidateStatus.ACCEPTED, 3)));
        when(repository.hasMergeDependency(GovernedEntityType.ACHIEVEMENT, 30, 41)).thenReturn(true);

        assertThrows(
                ResourceConflictException.class,
                () -> service.revertDecision(51, "撤销", 0));

        verify(repository, never()).removeCanonicalLink(any(), anyLong(), anyLong());
    }

    @Test
    void currentMergeWithoutDependencyCanBeReverted() {
        MergeDecision decision = new MergeDecision(
                51, 11, "ACCEPT", 20L, 41, 7, "确认", 0, NOW);
        DuplicateCandidate accepted = candidate(CandidateStatus.ACCEPTED, 3);
        when(repository.lockDecision(51)).thenReturn(Optional.of(decision));
        when(repository.isLatestDecision(11, 51)).thenReturn(true);
        when(repository.lockCandidate(11)).thenReturn(Optional.of(accepted));
        when(repository.hasMergeDependency(GovernedEntityType.ACHIEVEMENT, 30, 41)).thenReturn(false);
        when(repository.insertRevision(
                any(), eq(30L), eq("REVERT"), any(), any(), eq(7L), eq("撤销误合并"), eq(false), eq(NOW)))
                .thenReturn(43L);
        when(repository.updateCandidateStatus(11, CandidateStatus.PENDING, 3))
                .thenReturn(Optional.of(candidate(CandidateStatus.PENDING, 4)));
        MergeDecision reverted = new MergeDecision(
                52, 11, "REVERT", 20L, 43, 7, "撤销误合并", 0, NOW);
        when(repository.insertDecision(11, "REVERT", 20L, 43, 7, "撤销误合并", NOW))
                .thenReturn(reverted);

        assertEquals(reverted, service.revertDecision(51, "撤销误合并", 0));
        verify(repository).removeCanonicalLink(GovernedEntityType.ACHIEVEMENT, 30, 41);
        verify(graphProjectionRequestPort).requestRelated(GraphNodeType.ACHIEVEMENT, java.util.List.of(20L, 30L));
    }

    @Test
    void fieldOverrideValidatesAllowlistAndStableVenue() {
        when(repository.entityExists(GovernedEntityType.ACHIEVEMENT, 20)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.overrideAchievementField(20, "abstract", "文本", "修正", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.overrideAchievementField(20, "publicationDate", "2026-02", "修正", 0));
    }

    @Test
    void fieldOverrideIsStoredSeparatelyWithOptimisticVersion() {
        when(repository.entityExists(GovernedEntityType.ACHIEVEMENT, 20)).thenReturn(true);
        when(repository.lockFieldOverride(20, "title")).thenReturn(Optional.empty());
        when(repository.insertRevision(
                any(), eq(20L), eq("FIELD_OVERRIDE"), any(), any(), eq(7L), eq("登记更正"), eq(true), eq(NOW)))
                .thenReturn(61L);
        FieldOverride stored = new FieldOverride(
                71, 20, "title", "更正标题", 61, 7, "登记更正", true, 0, NOW, NOW);
        when(repository.saveFieldOverride(
                20, "title", "\"更正标题\"", 61, 7, "登记更正", null, NOW))
                .thenReturn(stored);

        FieldOverride result = service.overrideAchievementField(
                20, "title", " 更正标题 ", "登记更正", 0);

        assertEquals(stored, result);
        verify(graphProjectionRequestPort).requestAchievement(20);
    }

    private DuplicateCandidate candidate(CandidateStatus status, long version) {
        return new DuplicateCandidate(
                11, GovernedEntityType.ACHIEVEMENT, 20, 30, "FINGERPRINT",
                Map.of("title", "示例"), status, 1L, 1, version, NOW, NOW);
    }
}
