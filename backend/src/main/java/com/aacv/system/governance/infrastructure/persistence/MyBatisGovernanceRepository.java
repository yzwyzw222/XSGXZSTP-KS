package com.aacv.system.governance.infrastructure.persistence;

import com.aacv.system.governance.application.port.GovernanceRepository;
import com.aacv.system.governance.domain.CandidateStatus;
import com.aacv.system.governance.domain.DuplicateCandidate;
import com.aacv.system.governance.domain.DuplicateCandidateQuery;
import com.aacv.system.governance.domain.FieldOverride;
import com.aacv.system.governance.domain.GovernedEntityType;
import com.aacv.system.governance.domain.MergeDecision;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.domain.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class MyBatisGovernanceRepository implements GovernanceRepository {

    private static final TypeReference<Map<String, Object>> EVIDENCE_TYPE = new TypeReference<>() { };

    private final GovernanceMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisGovernanceRepository(GovernanceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<DuplicateCandidate> findCandidates(
            DuplicateCandidateQuery query, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        long total = mapper.countCandidates(query);
        List<DuplicateCandidate> items = mapper.findCandidates(query, (long) page * size, size)
                .stream()
                .map(this::toCandidate)
                .toList();
        return PageResult.of(items, page, size, total);
    }

    @Override
    public Optional<DuplicateCandidate> findCandidate(long candidateId) {
        return Optional.ofNullable(mapper.findCandidate(candidateId)).map(this::toCandidate);
    }

    @Override
    public Optional<DuplicateCandidate> lockCandidate(long candidateId) {
        return Optional.ofNullable(mapper.lockCandidate(candidateId)).map(this::toCandidate);
    }

    @Override
    public Optional<Map<String, Object>> findEntityComparison(GovernedEntityType entityType, long entityId) {
        if (entityType == null || entityId < 1) {
            throw new IllegalArgumentException("候选实体标识无效");
        }
        return Optional.ofNullable(mapper.findEntityComparison(entityType.name(), entityId)).map(this::readEvidence);
    }

    @Override
    public boolean hasExplicitVersionRelation(long leftAchievementId, long rightAchievementId) {
        return mapper.hasExplicitVersionRelation(leftAchievementId, rightAchievementId);
    }

    @Override
    public boolean entityExists(GovernedEntityType entityType, long entityId) {
        return mapper.countEntity(entityType.name(), entityId) == 1;
    }

    @Override
    public boolean isCanonicalEntity(GovernedEntityType entityType, long entityId) {
        return mapper.countCanonicalLink(entityType.name(), entityId) == 0;
    }

    @Override
    public Optional<DuplicateCandidate> updateCandidateStatus(
            long candidateId, CandidateStatus status, long expectedVersion) {
        if (mapper.updateCandidateStatus(candidateId, status.name(), expectedVersion) != 1) {
            return Optional.empty();
        }
        return findCandidate(candidateId);
    }

    @Override
    public long insertRevision(
            GovernedEntityType entityType,
            long entityId,
            String action,
            String beforeJson,
            String afterJson,
            long actorUserId,
            String reason,
            boolean reversible,
            Instant createdAt) {
        RevisionRow row = new RevisionRow();
        mapper.insertRevision(
                row, entityType.name(), entityId, action, beforeJson, afterJson,
                actorUserId, reason, reversible, createdAt);
        if (row.getId() == null) {
            throw new IllegalStateException("修订记录主键未生成");
        }
        return row.getId();
    }

    @Override
    public void createCanonicalLink(
            GovernedEntityType entityType, long entityId, long canonicalEntityId, long revisionId) {
        if (mapper.insertCanonicalLink(entityType.name(), entityId, canonicalEntityId, revisionId) != 1) {
            throw new ResourceConflictException("规范实体关联创建失败");
        }
    }

    @Override
    public void removeCanonicalLink(GovernedEntityType entityType, long entityId, long revisionId) {
        if (mapper.deleteCanonicalLink(entityType.name(), entityId, revisionId) != 1) {
            throw new ResourceConflictException("规范实体关联已变化，无法撤销");
        }
    }

    @Override
    public MergeDecision insertDecision(
            long candidateId,
            String decision,
            Long canonicalEntityId,
            long revisionId,
            long actorUserId,
            String reason,
            Instant decidedAt) {
        DecisionRow row = new DecisionRow();
        row.setCandidateId(candidateId);
        row.setDecision(decision);
        row.setCanonicalEntityId(canonicalEntityId);
        row.setRevisionId(revisionId);
        row.setActorUserId(actorUserId);
        row.setReason(reason);
        row.setDecidedAt(decidedAt);
        mapper.insertDecision(row);
        if (row.getId() == null) {
            throw new IllegalStateException("治理决定主键未生成");
        }
        return toDecision(mapper.findDecision(row.getId()));
    }

    @Override
    public Optional<MergeDecision> lockDecision(long decisionId) {
        return Optional.ofNullable(mapper.lockDecision(decisionId)).map(this::toDecision);
    }

    @Override
    public boolean isLatestDecision(long candidateId, long decisionId) {
        return mapper.countLaterDecisions(candidateId, decisionId) == 0;
    }

    @Override
    public boolean hasMergeDependency(
            GovernedEntityType entityType, long canonicalEntityId, long revisionId) {
        return mapper.countMergeDependencies(entityType.name(), canonicalEntityId, revisionId) > 0;
    }

    @Override
    public Optional<FieldOverride> lockFieldOverride(long achievementId, String fieldName) {
        return Optional.ofNullable(mapper.lockFieldOverride(achievementId, fieldName)).map(this::toOverride);
    }

    @Override
    public Optional<FieldOverride> lockFieldOverrideByRevision(long achievementId, long revisionId) {
        return Optional.ofNullable(mapper.lockFieldOverrideByRevision(achievementId, revisionId))
                .map(this::toOverride);
    }

    @Override
    public FieldOverride saveFieldOverride(
            long achievementId,
            String fieldName,
            String fieldValueJson,
            long revisionId,
            long actorUserId,
            String reason,
            Long expectedVersion,
            Instant now) {
        OverrideRow row = new OverrideRow();
        row.setAchievementId(achievementId);
        row.setFieldName(fieldName);
        row.setFieldValue(fieldValueJson);
        row.setRevisionId(revisionId);
        row.setActorUserId(actorUserId);
        row.setReason(reason);
        row.setActive(true);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        if (expectedVersion == null) {
            mapper.insertFieldOverride(row);
        } else {
            FieldOverride current = lockFieldOverride(achievementId, fieldName)
                    .orElseThrow(() -> new ResourceConflictException("人工字段覆盖已变化"));
            row.setId(current.id());
            if (mapper.updateFieldOverride(row, expectedVersion) != 1) {
                throw new ResourceConflictException("人工字段覆盖版本冲突");
            }
        }
        return toOverride(mapper.findFieldOverrideById(row.getId()));
    }

    @Override
    public FieldOverride deactivateFieldOverride(
            long overrideId,
            long revisionId,
            long actorUserId,
            String reason,
            long expectedVersion,
            Instant now) {
        OverrideRow row = mapper.findFieldOverrideById(overrideId);
        if (row == null) {
            throw new ResourceConflictException("人工字段覆盖不存在");
        }
        row.setRevisionId(revisionId);
        row.setActorUserId(actorUserId);
        row.setReason(reason);
        row.setActive(false);
        row.setUpdatedAt(now);
        if (mapper.updateFieldOverride(row, expectedVersion) != 1) {
            throw new ResourceConflictException("人工字段覆盖版本冲突");
        }
        return toOverride(mapper.findFieldOverrideById(overrideId));
    }

    private DuplicateCandidate toCandidate(CandidateRow row) {
        return new DuplicateCandidate(
                row.getId(), GovernedEntityType.valueOf(row.getEntityType()),
                row.getLeftEntityId(), row.getRightEntityId(), row.getMatchBasis(),
                readEvidence(row.getEvidenceJson()), CandidateStatus.valueOf(row.getStatus()),
                row.getSourceId(), row.getRuleVersion(), row.getVersion(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private MergeDecision toDecision(DecisionRow row) {
        return new MergeDecision(
                row.getId(), row.getCandidateId(), row.getDecision(), row.getCanonicalEntityId(),
                row.getRevisionId(), row.getActorUserId(), row.getReason(), row.getVersion(),
                row.getDecidedAt());
    }

    private FieldOverride toOverride(OverrideRow row) {
        try {
            return new FieldOverride(
                    row.getId(), row.getAchievementId(), row.getFieldName(),
                    objectMapper.readValue(row.getFieldValue(), Object.class), row.getRevisionId(),
                    row.getActorUserId(), row.getReason(), row.isActive(), row.getVersion(),
                    row.getCreatedAt(), row.getUpdatedAt());
        } catch (JacksonException exception) {
            throw new IllegalStateException("人工字段覆盖JSON损坏", exception);
        }
    }

    private Map<String, Object> readEvidence(String json) {
        try {
            return objectMapper.readValue(json, EVIDENCE_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("候选证据JSON损坏", exception);
        }
    }
}
