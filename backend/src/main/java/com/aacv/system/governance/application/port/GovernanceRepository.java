package com.aacv.system.governance.application.port;

import com.aacv.system.governance.domain.CandidateStatus;
import com.aacv.system.governance.domain.DuplicateCandidate;
import com.aacv.system.governance.domain.DuplicateCandidateQuery;
import com.aacv.system.governance.domain.FieldOverride;
import com.aacv.system.governance.domain.GovernedEntityType;
import com.aacv.system.governance.domain.MergeDecision;
import com.aacv.system.shared.domain.PageResult;
import java.time.Instant;
import java.util.Optional;

public interface GovernanceRepository {

    PageResult<DuplicateCandidate> findCandidates(
            DuplicateCandidateQuery query, int page, int size);

    Optional<DuplicateCandidate> findCandidate(long candidateId);

    Optional<DuplicateCandidate> lockCandidate(long candidateId);

    boolean entityExists(GovernedEntityType entityType, long entityId);

    boolean isCanonicalEntity(GovernedEntityType entityType, long entityId);

    Optional<DuplicateCandidate> updateCandidateStatus(
            long candidateId, CandidateStatus status, long expectedVersion);

    long insertRevision(
            GovernedEntityType entityType,
            long entityId,
            String action,
            String beforeJson,
            String afterJson,
            long actorUserId,
            String reason,
            boolean reversible,
            Instant createdAt);

    void createCanonicalLink(
            GovernedEntityType entityType, long entityId, long canonicalEntityId, long revisionId);

    void removeCanonicalLink(GovernedEntityType entityType, long entityId, long revisionId);

    MergeDecision insertDecision(
            long candidateId,
            String decision,
            Long canonicalEntityId,
            long revisionId,
            long actorUserId,
            String reason,
            Instant decidedAt);

    Optional<MergeDecision> lockDecision(long decisionId);

    boolean isLatestDecision(long candidateId, long decisionId);

    boolean hasMergeDependency(GovernedEntityType entityType, long canonicalEntityId, long revisionId);

    Optional<FieldOverride> lockFieldOverride(long achievementId, String fieldName);

    Optional<FieldOverride> lockFieldOverrideByRevision(long achievementId, long revisionId);

    FieldOverride saveFieldOverride(
            long achievementId,
            String fieldName,
            String fieldValueJson,
            long revisionId,
            long actorUserId,
            String reason,
            Long expectedVersion,
            Instant now);

    FieldOverride deactivateFieldOverride(
            long overrideId,
            long revisionId,
            long actorUserId,
            String reason,
            long expectedVersion,
            Instant now);
}
