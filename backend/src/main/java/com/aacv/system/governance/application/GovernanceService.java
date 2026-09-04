package com.aacv.system.governance.application;

import com.aacv.system.governance.application.port.GovernanceRepository;
import com.aacv.system.governance.domain.CandidateStatus;
import com.aacv.system.governance.domain.DuplicateCandidate;
import com.aacv.system.governance.domain.DuplicateCandidateQuery;
import com.aacv.system.governance.domain.FieldOverride;
import com.aacv.system.governance.domain.GovernedEntityType;
import com.aacv.system.governance.domain.MergeDecision;
import com.aacv.system.graph.application.port.GraphProjectionRequestPort;
import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.shared.domain.PageResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class GovernanceService {

    private static final int MAX_REASON_LENGTH = 1000;

    private final GovernanceRepository repository;
    private final CurrentActorProvider currentActorProvider;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final GraphProjectionRequestPort graphProjectionRequestPort;

    public GovernanceService(
            GovernanceRepository repository,
            CurrentActorProvider currentActorProvider,
            AuditService auditService,
            ObjectMapper objectMapper,
            Clock clock,
            GraphProjectionRequestPort graphProjectionRequestPort) {
        this.repository = repository;
        this.currentActorProvider = currentActorProvider;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.graphProjectionRequestPort = graphProjectionRequestPort;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GOVERNANCE_READ')")
    public PageResult<DuplicateCandidate> findCandidates(
            DuplicateCandidateQuery query, int page, int size) {
        return repository.findCandidates(query, page, size);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GOVERNANCE_READ')")
    public DuplicateCandidate requireCandidate(long candidateId) {
        return repository.findCandidate(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("重复候选不存在"));
    }

    @Transactional
    @PreAuthorize("hasAuthority('GOVERNANCE_MANAGE')")
    public MergeDecision acceptCandidate(
            long candidateId, long canonicalEntityId, String reason, long expectedVersion) {
        String checkedReason = requireReason(reason);
        DuplicateCandidate candidate = lockPendingCandidate(candidateId, expectedVersion);
        if (canonicalEntityId != candidate.leftEntityId()
                && canonicalEntityId != candidate.rightEntityId()) {
            throw new IllegalArgumentException("规范实体必须属于当前候选对");
        }
        long memberEntityId = otherEntityId(candidate, canonicalEntityId);
        ensureEntityCanMerge(candidate.entityType(), canonicalEntityId, memberEntityId);
        long actorUserId = requireActor();
        String beforeJson = json(Map.of(
                "candidateStatus", candidate.status().name(),
                "memberEntityId", memberEntityId));
        String afterJson = json(Map.of(
                "candidateStatus", CandidateStatus.ACCEPTED.name(),
                "canonicalEntityId", canonicalEntityId,
                "memberEntityId", memberEntityId,
                "ruleVersion", candidate.ruleVersion()));
        long revisionId = repository.insertRevision(
                candidate.entityType(), memberEntityId, "MERGE", beforeJson, afterJson,
                actorUserId, checkedReason, true, clock.instant());
        repository.createCanonicalLink(
                candidate.entityType(), memberEntityId, canonicalEntityId, revisionId);
        updateCandidateStatus(candidate, CandidateStatus.ACCEPTED);
        MergeDecision decision = repository.insertDecision(
                candidate.id(), "ACCEPT", canonicalEntityId, revisionId,
                actorUserId, checkedReason, clock.instant());
        auditService.record(
                AuditAction.DUPLICATE_CANDIDATE_ACCEPTED,
                "DUPLICATE_CANDIDATE",
                Long.toString(candidate.id()),
                AuditResult.SUCCESS,
                Map.of(
                        "entityType", candidate.entityType().name(),
                        "canonicalEntityId", Long.toString(canonicalEntityId),
                        "ruleVersion", Integer.toString(candidate.ruleVersion())));
        requestCandidateProjection(candidate);
        return decision;
    }

    @Transactional
    @PreAuthorize("hasAuthority('GOVERNANCE_MANAGE')")
    public MergeDecision rejectCandidate(long candidateId, String reason, long expectedVersion) {
        String checkedReason = requireReason(reason);
        DuplicateCandidate candidate = lockPendingCandidate(candidateId, expectedVersion);
        long actorUserId = requireActor();
        long revisionId = repository.insertRevision(
                candidate.entityType(), candidate.leftEntityId(), "REJECT",
                json(Map.of("candidateStatus", candidate.status().name())),
                json(Map.of(
                        "candidateStatus", CandidateStatus.REJECTED.name(),
                        "ruleVersion", candidate.ruleVersion())),
                actorUserId, checkedReason, true, clock.instant());
        updateCandidateStatus(candidate, CandidateStatus.REJECTED);
        MergeDecision decision = repository.insertDecision(
                candidate.id(), "REJECT", null, revisionId,
                actorUserId, checkedReason, clock.instant());
        auditService.record(
                AuditAction.DUPLICATE_CANDIDATE_REJECTED,
                "DUPLICATE_CANDIDATE",
                Long.toString(candidate.id()),
                AuditResult.SUCCESS,
                Map.of(
                        "entityType", candidate.entityType().name(),
                        "ruleVersion", Integer.toString(candidate.ruleVersion())));
        return decision;
    }

    @Transactional
    @PreAuthorize("hasAuthority('GOVERNANCE_MANAGE')")
    public MergeDecision revertDecision(long decisionId, String reason, long expectedVersion) {
        String checkedReason = requireReason(reason);
        MergeDecision decision = repository.lockDecision(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("治理决定不存在"));
        if (decision.version() != expectedVersion) {
            throw new ResourceConflictException("治理决定版本冲突");
        }
        if ("REVERT".equals(decision.decision())
                || !repository.isLatestDecision(decision.candidateId(), decision.id())) {
            throw new ResourceConflictException("该治理决定已被后续决定覆盖");
        }
        DuplicateCandidate candidate = repository.lockCandidate(decision.candidateId())
                .orElseThrow(() -> new ResourceNotFoundException("重复候选不存在"));
        long entityId = candidate.leftEntityId();
        if ("ACCEPT".equals(decision.decision())) {
            long canonicalEntityId = Optional.ofNullable(decision.canonicalEntityId())
                    .orElseThrow(() -> new IllegalStateException("接受决定缺少规范实体"));
            long memberEntityId = otherEntityId(candidate, canonicalEntityId);
            if (repository.hasMergeDependency(
                    candidate.entityType(), memberEntityId, decision.revisionId())) {
                throw new ResourceConflictException("该合并已被后续规范关联依赖，无法撤销");
            }
            repository.removeCanonicalLink(
                    candidate.entityType(), memberEntityId, decision.revisionId());
            entityId = memberEntityId;
            requestCandidateProjection(candidate);
        }
        CandidateStatus expectedStatus = "ACCEPT".equals(decision.decision())
                ? CandidateStatus.ACCEPTED : CandidateStatus.REJECTED;
        if (candidate.status() != expectedStatus) {
            throw new ResourceConflictException("候选状态已变化，无法撤销");
        }
        long actorUserId = requireActor();
        long revisionId = repository.insertRevision(
                candidate.entityType(), entityId, "REVERT",
                json(Map.of("decisionId", decision.id(), "candidateStatus", candidate.status().name())),
                json(Map.of("candidateStatus", CandidateStatus.PENDING.name())),
                actorUserId, checkedReason, false, clock.instant());
        updateCandidateStatus(candidate, CandidateStatus.PENDING);
        MergeDecision reverted = repository.insertDecision(
                candidate.id(), "REVERT", decision.canonicalEntityId(), revisionId,
                actorUserId, checkedReason, clock.instant());
        auditService.record(
                AuditAction.MERGE_DECISION_REVERTED,
                "MERGE_DECISION",
                Long.toString(decision.id()),
                AuditResult.SUCCESS,
                Map.of("candidateId", Long.toString(candidate.id())));
        return reverted;
    }

    @Transactional
    @PreAuthorize("hasAuthority('GOVERNANCE_MANAGE')")
    public FieldOverride overrideAchievementField(
            long achievementId,
            String fieldName,
            Object value,
            String reason,
            long expectedVersion) {
        if (!repository.entityExists(GovernedEntityType.ACHIEVEMENT, achievementId)) {
            throw new ResourceNotFoundException("成果不存在");
        }
        String checkedFieldName = requireFieldName(fieldName);
        Object checkedValue = validateFieldValue(checkedFieldName, value);
        String checkedReason = requireReason(reason);
        Optional<FieldOverride> current = repository.lockFieldOverride(achievementId, checkedFieldName);
        if (current.map(FieldOverride::version).orElse(0L) != expectedVersion
                || current.isEmpty() && expectedVersion != 0) {
            throw new ResourceConflictException("人工字段覆盖版本冲突");
        }
        long actorUserId = requireActor();
        long revisionId = repository.insertRevision(
                GovernedEntityType.ACHIEVEMENT, achievementId, "FIELD_OVERRIDE",
                json(Map.of("fieldName", checkedFieldName,
                        "previousValue", current.map(FieldOverride::value).orElse(""))),
                json(Map.of("fieldName", checkedFieldName, "value", checkedValue)),
                actorUserId, checkedReason, true, clock.instant());
        FieldOverride saved = repository.saveFieldOverride(
                achievementId, checkedFieldName, json(checkedValue), revisionId,
                actorUserId, checkedReason, current.map(FieldOverride::version).orElse(null),
                clock.instant());
        auditService.record(
                AuditAction.ACHIEVEMENT_FIELD_OVERRIDDEN,
                "ACHIEVEMENT",
                Long.toString(achievementId),
                AuditResult.SUCCESS,
                Map.of("fieldName", checkedFieldName));
        graphProjectionRequestPort.requestAchievement(achievementId);
        return saved;
    }

    @Transactional
    @PreAuthorize("hasAuthority('GOVERNANCE_MANAGE')")
    public FieldOverride revertAchievementFieldOverride(
            long achievementId, long revisionId, String reason, long expectedVersion) {
        String checkedReason = requireReason(reason);
        FieldOverride current = repository.lockFieldOverrideByRevision(achievementId, revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("人工字段修订不存在"));
        if (!current.active() || current.version() != expectedVersion) {
            throw new ResourceConflictException("人工字段覆盖已变化，无法撤销");
        }
        long actorUserId = requireActor();
        long revertRevisionId = repository.insertRevision(
                GovernedEntityType.ACHIEVEMENT, achievementId, "REVERT",
                json(Map.of("fieldName", current.fieldName(), "value", current.value())),
                json(Map.of("fieldName", current.fieldName(), "active", false)),
                actorUserId, checkedReason, false, clock.instant());
        FieldOverride reverted = repository.deactivateFieldOverride(
                current.id(), revertRevisionId, actorUserId, checkedReason,
                current.version(), clock.instant());
        auditService.record(
                AuditAction.ACHIEVEMENT_FIELD_OVERRIDE_REVERTED,
                "ACHIEVEMENT",
                Long.toString(achievementId),
                AuditResult.SUCCESS,
                Map.of("fieldName", current.fieldName()));
        graphProjectionRequestPort.requestAchievement(achievementId);
        return reverted;
    }

    private void requestCandidateProjection(DuplicateCandidate candidate) {
        GraphNodeType graphNodeType = switch (candidate.entityType()) {
            case ACHIEVEMENT -> GraphNodeType.ACHIEVEMENT;
            case AUTHOR -> GraphNodeType.AUTHOR;
            case ORGANIZATION -> GraphNodeType.INSTITUTION;
            case VENUE -> GraphNodeType.VENUE;
        };
        graphProjectionRequestPort.requestRelated(
                graphNodeType, List.of(candidate.leftEntityId(), candidate.rightEntityId()));
    }

    private DuplicateCandidate lockPendingCandidate(long candidateId, long expectedVersion) {
        DuplicateCandidate candidate = repository.lockCandidate(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("重复候选不存在"));
        if (candidate.status() != CandidateStatus.PENDING || candidate.version() != expectedVersion) {
            throw new ResourceConflictException("重复候选状态或版本已变化");
        }
        return candidate;
    }

    private void ensureEntityCanMerge(
            GovernedEntityType entityType, long canonicalEntityId, long memberEntityId) {
        if (!repository.entityExists(entityType, canonicalEntityId)
                || !repository.entityExists(entityType, memberEntityId)) {
            throw new ResourceConflictException("候选实体已不存在");
        }
        if (!repository.isCanonicalEntity(entityType, canonicalEntityId)
                || !repository.isCanonicalEntity(entityType, memberEntityId)) {
            throw new ResourceConflictException("候选实体已有规范关联");
        }
        if (repository.hasMergeDependency(entityType, memberEntityId, -1)) {
            throw new ResourceConflictException("待合并实体已有下游规范成员");
        }
    }

    private void updateCandidateStatus(DuplicateCandidate candidate, CandidateStatus status) {
        repository.updateCandidateStatus(candidate.id(), status, candidate.version())
                .orElseThrow(() -> new ResourceConflictException("重复候选版本冲突"));
    }

    private long otherEntityId(DuplicateCandidate candidate, long canonicalEntityId) {
        return canonicalEntityId == candidate.leftEntityId()
                ? candidate.rightEntityId() : candidate.leftEntityId();
    }

    private long requireActor() {
        return currentActorProvider.currentUserId()
                .orElseThrow(() -> new ResourceConflictException("当前操作人身份不可用"));
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("治理原因不能为空且长度不能超过1000个字符");
        }
        return reason.trim();
    }

    private String requireFieldName(String fieldName) {
        return switch (fieldName == null ? "" : fieldName.trim()) {
            case "title", "type", "language", "publicationDate", "venueId" -> fieldName.trim();
            default -> throw new IllegalArgumentException("不允许修正该成果字段");
        };
    }

    private Object validateFieldValue(String fieldName, Object value) {
        if ("venueId".equals(fieldName)) {
            if (!(value instanceof Number number) || number.longValue() < 1
                    || !repository.entityExists(GovernedEntityType.VENUE, number.longValue())) {
                throw new IllegalArgumentException("载体ID无效");
            }
            return number.longValue();
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("人工字段值不能为空");
        }
        String checked = text.trim();
        if ("publicationDate".equals(fieldName)) {
            try {
                LocalDate.parse(checked);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("发表日期必须使用ISO日期格式", exception);
            }
        }
        int limit = "title".equals(fieldName) ? 1000 : 64;
        if (checked.length() > limit) {
            throw new IllegalArgumentException("人工字段值长度超限");
        }
        return checked;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("治理修订摘要序列化失败", exception);
        }
    }
}
