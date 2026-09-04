package com.aacv.system.ingestion.infrastructure.persistence;

import com.aacv.system.ingestion.application.port.IngestionRepository.PageStatistics;
import com.aacv.system.ingestion.domain.NormalizedWork;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface IngestionMapper {

    RawRecordState findRawState(@Param("sourceId") long sourceId, @Param("externalId") String externalId);

    int upsertRaw(RawRecordPersistenceRow row);

    int markRawParsed(long id);

    int markRawFailed(long id);

    Long findAchievementBySource(@Param("sourceId") long sourceId, @Param("externalId") String externalId);

    Long findAchievementByDoi(String doi);

    int upsertVenue(NormalizedWork.NormalizedVenue venue);

    Long findVenueId(String externalId);

    int insertAchievement(AchievementPersistenceRow row);

    int updateAchievement(AchievementPersistenceRow row);

    int upsertPaperDetail(
            @Param("achievementId") long achievementId,
            @Param("abstractText") String abstractText,
            @Param("authorshipsMayBeIncomplete") boolean authorshipsMayBeIncomplete);

    int upsertAchievementSource(
            @Param("achievementId") long achievementId,
            @Param("sourceId") long sourceId,
            @Param("rawRecordId") long rawRecordId,
            @Param("externalId") String externalId,
            @Param("sourceUrl") String sourceUrl,
            @Param("parserVersion") String parserVersion,
            @Param("now") Instant now);

    Long findAchievementSourceId(
            @Param("sourceId") long sourceId, @Param("externalId") String externalId);

    int upsertAchievementSnapshot(
            @Param("achievementSourceId") long achievementSourceId,
            @Param("normalizedPayload") String normalizedPayload,
            @Param("sourcePriority") int sourcePriority,
            @Param("observedAt") Instant observedAt);

    List<SourceSnapshotRow> findAchievementSnapshots(long achievementId);

    int clearAchievementFieldSelection(long achievementId);

    int upsertFieldProvenance(
            @Param("entityType") String entityType,
            @Param("entityId") long entityId,
            @Param("fieldName") String fieldName,
            @Param("sourceId") long sourceId,
            @Param("rawRecordId") long rawRecordId,
            @Param("sourcePriority") int sourcePriority,
            @Param("fieldValue") String fieldValue,
            @Param("selected") boolean selected,
            @Param("observedAt") Instant observedAt);

    int deleteAuthorshipOrganizations(long achievementId);

    int deleteAchievementAuthors(long achievementId);

    int deleteAchievementTopics(long achievementId);

    int deleteAchievementSubjects(long achievementId);

    int deleteAchievementReferences(long achievementId);

    Long findAuthorId(String openAlexId);

    Long findAuthorByExternalId(@Param("idType") String idType, @Param("externalId") String externalId);

    int countAuthorExternalType(@Param("authorId") long authorId, @Param("idType") String idType);

    List<Long> findAuthorIdsByName(@Param("displayName") String displayName, @Param("excludeId") long excludeId);

    int insertAuthor(GeneratedIdRow row);

    int insertAuthorOpenAlexId(@Param("authorId") long authorId, @Param("openAlexId") String openAlexId);

    int insertAuthorExternalId(
            @Param("authorId") long authorId,
            @Param("idType") String idType,
            @Param("externalId") String externalId);

    int updateAuthor(@Param("authorId") long authorId, @Param("displayName") String displayName);

    int deleteAuthorOrcid(long authorId);

    int insertAuthorOrcid(@Param("authorId") long authorId, @Param("orcid") String orcid);

    int insertAchievementAuthor(
            @Param("achievementId") long achievementId,
            @Param("authorId") long authorId,
            @Param("position") int position);

    int upsertOrganization(NormalizedWork.NormalizedOrganization organization);

    Long findOrganizationId(String externalId);

    Long findOrganizationByExternalId(
            @Param("idType") String idType, @Param("externalId") String externalId);

    int countOrganizationExternalType(
            @Param("organizationId") long organizationId, @Param("idType") String idType);

    List<Long> findOrganizationIdsByName(
            @Param("displayName") String displayName, @Param("excludeId") long excludeId);

    int insertOrganization(CanonicalEntityRow row);

    int updateOrganization(CanonicalEntityRow row);

    int insertOrganizationExternalId(
            @Param("organizationId") long organizationId,
            @Param("idType") String idType,
            @Param("externalId") String externalId);

    int insertAuthorshipOrganization(
            @Param("achievementId") long achievementId,
            @Param("authorId") long authorId,
            @Param("organizationId") long organizationId);

    int upsertTopic(NormalizedWork.NormalizedTopic topic);

    Long findTopicId(String externalId);

    int upsertSubject(
            @Param("sourceId") long sourceId,
            @Param("externalId") String externalId,
            @Param("displayName") String displayName,
            @Param("subjectPath") String subjectPath);

    Long findSubjectId(@Param("sourceId") long sourceId, @Param("externalId") String externalId);

    int insertAchievementSubject(
            @Param("achievementId") long achievementId,
            @Param("subjectId") long subjectId,
            @Param("position") int position);

    int insertAchievementTopic(
            @Param("achievementId") long achievementId,
            @Param("topicId") long topicId,
            @Param("position") int position);

    Long findAchievementByOpenAlexWorkId(String externalId);

    Long findAchievementByTypedReference(
            @Param("idType") String idType, @Param("externalId") String externalId);

    int insertAchievementReference(
            @Param("achievementId") long achievementId,
            @Param("externalId") String externalId,
            @Param("citedAchievementId") Long citedAchievementId);

    int insertTypedAchievementReference(
            @Param("achievementId") long achievementId,
            @Param("idType") String idType,
            @Param("externalId") String externalId,
            @Param("citedAchievementId") Long citedAchievementId);

    int resolveDoiReferences(
            @Param("doi") String doi, @Param("achievementId") long achievementId);

    List<Long> findCitingAchievementIdsByDoi(String doi);

    Long findVenueByExternalId(@Param("idType") String idType, @Param("externalId") String externalId);

    int countVenueExternalType(@Param("venueId") long venueId, @Param("idType") String idType);

    List<Long> findVenueIdsByName(@Param("displayName") String displayName, @Param("excludeId") long excludeId);

    int insertVenue(CanonicalEntityRow row);

    int updateVenue(CanonicalEntityRow row);

    int insertVenueExternalId(
            @Param("venueId") long venueId,
            @Param("idType") String idType,
            @Param("externalId") String externalId);

    List<Long> findAchievementIdsByFingerprint(
            @Param("fingerprint") String fingerprint, @Param("excludeId") long excludeId);

    int insertDuplicateCandidate(
            @Param("entityType") String entityType,
            @Param("leftEntityId") long leftEntityId,
            @Param("rightEntityId") long rightEntityId,
            @Param("matchBasis") String matchBasis,
            @Param("sourceId") long sourceId,
            @Param("ruleVersion") int ruleVersion,
            @Param("evidenceJson") String evidenceJson,
            @Param("evidenceHash") String evidenceHash);

    int insertFailure(
            @Param("runId") long runId,
            @Param("rawRecordId") long rawRecordId,
            @Param("externalRecordId") String externalRecordId,
            @Param("failureStage") String failureStage,
            @Param("errorCategory") String errorCategory,
            @Param("safeMessage") String safeMessage,
            @Param("retryable") boolean retryable,
            @Param("evidenceHash") String evidenceHash);
    List<RetryFailureRow> findRetryableFailures(
            @Param("runId") long runId, @Param("limit") int limit);
    int recordRetryAttempt(
            @Param("failureId") long failureId, @Param("resolved") boolean resolved);
    int clearExpiredPayloads(
            @Param("expiredBefore") Instant expiredBefore,
            @Param("batchSize") int batchSize);

    int updateRunStatistics(@Param("runId") long runId, @Param("statistics") PageStatistics statistics);

    int upsertCheckpoint(
            @Param("runId") long runId,
            @Param("cursorValue") String cursorValue,
            @Param("cursorHash") String cursorHash,
            @Param("statistics") PageStatistics statistics,
            @Param("now") Instant now);

    int upsertQualityMetric(
            @Param("runId") long runId,
            @Param("metricCode") String metricCode,
            @Param("numerator") long numerator,
            @Param("denominator") long denominator,
            @Param("measuredAt") Instant measuredAt);

    int insertQualityIssueSample(
            @Param("runId") long runId,
            @Param("rawRecordId") long rawRecordId,
            @Param("metricCode") String metricCode,
            @Param("externalRecordId") String externalRecordId,
            @Param("evidenceJson") String evidenceJson);
}
