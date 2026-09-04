package com.aacv.system.governance.infrastructure.persistence;

import com.aacv.system.governance.domain.DuplicateCandidateQuery;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface GovernanceMapper {
    long countCandidates(@Param("query") DuplicateCandidateQuery query);
    List<CandidateRow> findCandidates(
            @Param("query") DuplicateCandidateQuery query,
            @Param("offset") long offset,
            @Param("size") int size);
    CandidateRow findCandidate(long candidateId);
    CandidateRow lockCandidate(long candidateId);
    int countEntity(@Param("entityType") String entityType, @Param("entityId") long entityId);
    int countCanonicalLink(@Param("entityType") String entityType, @Param("entityId") long entityId);
    int updateCandidateStatus(
            @Param("candidateId") long candidateId,
            @Param("status") String status,
            @Param("expectedVersion") long expectedVersion);
    int insertRevision(
            @Param("row") RevisionRow row,
            @Param("entityType") String entityType,
            @Param("entityId") long entityId,
            @Param("action") String action,
            @Param("beforeJson") String beforeJson,
            @Param("afterJson") String afterJson,
            @Param("actorUserId") long actorUserId,
            @Param("reason") String reason,
            @Param("reversible") boolean reversible,
            @Param("createdAt") Instant createdAt);
    int insertCanonicalLink(
            @Param("entityType") String entityType,
            @Param("entityId") long entityId,
            @Param("canonicalEntityId") long canonicalEntityId,
            @Param("revisionId") long revisionId);
    int deleteCanonicalLink(
            @Param("entityType") String entityType,
            @Param("entityId") long entityId,
            @Param("revisionId") long revisionId);
    int insertDecision(DecisionRow row);
    DecisionRow findDecision(long decisionId);
    DecisionRow lockDecision(long decisionId);
    int countLaterDecisions(@Param("candidateId") long candidateId, @Param("decisionId") long decisionId);
    int countMergeDependencies(
            @Param("entityType") String entityType,
            @Param("canonicalEntityId") long canonicalEntityId,
            @Param("revisionId") long revisionId);
    OverrideRow lockFieldOverride(
            @Param("achievementId") long achievementId, @Param("fieldName") String fieldName);
    OverrideRow lockFieldOverrideByRevision(
            @Param("achievementId") long achievementId, @Param("revisionId") long revisionId);
    int insertFieldOverride(OverrideRow row);
    int updateFieldOverride(@Param("row") OverrideRow row, @Param("expectedVersion") long expectedVersion);
    OverrideRow findFieldOverrideById(long overrideId);
}
