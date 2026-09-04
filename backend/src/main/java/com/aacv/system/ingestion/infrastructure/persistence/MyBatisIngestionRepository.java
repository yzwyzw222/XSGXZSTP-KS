package com.aacv.system.ingestion.infrastructure.persistence;

import com.aacv.system.graph.application.port.GraphProjectionRequestPort;
import com.aacv.system.ingestion.application.port.IngestionRepository;
import com.aacv.system.ingestion.domain.NormalizedWork;
import com.aacv.system.ingestion.domain.NormalizedWork.NormalizedAuthorship;
import com.aacv.system.ingestion.domain.NormalizedWork.NormalizedOrganization;
import com.aacv.system.ingestion.domain.NormalizedWork.NormalizedTopic;
import com.aacv.system.ingestion.domain.NormalizedWork.NormalizedVenue;
import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.ingestion.domain.RetryFailureRecord;
import com.aacv.system.ingestion.application.port.IngestionRepository.QualityMetricIncrement;
import com.aacv.system.ingestion.application.port.IngestionRepository.QualityIssueSample;
import com.aacv.system.source.domain.SourceType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class MyBatisIngestionRepository implements IngestionRepository {

    private final IngestionMapper mapper;
    private final ObjectMapper objectMapper;
    private final GraphProjectionRequestPort graphProjectionRequestPort;

    public MyBatisIngestionRepository(
            IngestionMapper mapper,
            ObjectMapper objectMapper,
            GraphProjectionRequestPort graphProjectionRequestPort) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.graphProjectionRequestPort = graphProjectionRequestPort;
    }

    @Override
    public RawUpsertResult upsertRawRecord(
            long sourceId,
            long runId,
            RawSourceRecord record,
            String payloadHash,
            String parserVersion,
            Instant payloadExpiresAt,
            Instant now) {
        RawRecordState existing = mapper.findRawState(sourceId, record.externalRecordId());
        RawRecordPersistenceRow row = new RawRecordPersistenceRow();
        row.setSourceId(sourceId);
        row.setRunId(runId);
        row.setExternalRecordId(record.externalRecordId());
        row.setSourceUrl(record.sourceLocation().toString());
        row.setFetchedAt(record.fetchedAt());
        row.setPayloadHash(payloadHash);
        row.setParserVersion(parserVersion);
        row.setPayload(record.payload());
        row.setPayloadExpiresAt(payloadExpiresAt);
        row.setNow(now);
        int affectedRows = mapper.upsertRaw(row);
        if ((affectedRows < 1 || affectedRows > 2) || row.getId() == null) {
            throw new IllegalStateException("原始记录写入数量或主键异常");
        }
        return new RawUpsertResult(
                row.getId(), existing != null && existing.payloadHash().equals(payloadHash));
    }

    @Override
    public PersistOutcome persistNormalizedWork(
            long sourceId,
            long rawRecordId,
            RawSourceRecord rawRecord,
            NormalizedWork work,
            String parserVersion,
            Instant now) {
        SourceType sourceType = rawRecord.sourceType();
        Long achievementId = mapper.findAchievementBySource(sourceId, work.externalId());
        boolean autoMatched = false;
        if (achievementId == null && work.doi() != null) {
            achievementId = mapper.findAchievementByDoi(work.doi());
            autoMatched = achievementId != null;
        }
        boolean created = achievementId == null;
        AchievementPersistenceRow row = new AchievementPersistenceRow();
        row.setWork(work);
        row.setVenueId(null);
        row.setNow(now);
        if (created) {
            if (mapper.insertAchievement(row) != 1 || row.getId() == null) {
                throw new IllegalStateException("成果写入数量或主键异常");
            }
            achievementId = row.getId();
        }
        requireUpsertRows(
                mapper.upsertAchievementSource(
                        achievementId,
                        sourceId,
                        rawRecordId,
                        work.externalId(),
                        rawRecord.sourceLocation().toString(),
                        parserVersion,
                        now),
                "成果来源写入");
        long achievementSourceId = requireId(
                mapper.findAchievementSourceId(sourceId, work.externalId()), "成果来源");
        requireUpsertRows(
                mapper.upsertAchievementSnapshot(
                        achievementSourceId,
                        serialize(work),
                        baseSourcePriority(sourceType),
                        now),
                "成果来源快照写入");

        List<SourceSnapshot> snapshots = loadSnapshots(achievementId);
        CanonicalSelection selection = selectCanonical(snapshots);
        EntityUpsert venue = upsertVenue(
                selection.relationSource(Field.VENUE), selection.work().externalId(), selection.work().primaryVenue());
        row.setId(achievementId);
        row.setWork(selection.work());
        row.setVenueId(venue.entityId());
        requireSingleRow(mapper.updateAchievement(row), "成果更新");
        requireUpsertRows(
                mapper.upsertPaperDetail(
                        achievementId,
                        selection.work().abstractText(),
                        selection.work().authorshipsMayBeIncomplete()),
                "论文详情写入");
        synchronizeProvenance(achievementId, snapshots, selection, now);
        int candidateCount = venue.candidateCount()
                + replaceRelations(achievementId, selection)
                + createAchievementCandidates(achievementId, sourceId, selection.work());
        List<Long> citingAchievementIds = List.of();
        if (selection.work().doi() != null) {
            citingAchievementIds = mapper.findCitingAchievementIdsByDoi(selection.work().doi());
            mapper.resolveDoiReferences(selection.work().doi(), achievementId);
        }
        graphProjectionRequestPort.requestAchievement(achievementId);
        for (Long citingAchievementId : citingAchievementIds) {
            if (citingAchievementId != achievementId) {
                graphProjectionRequestPort.requestAchievement(citingAchievementId);
            }
        }
        return new PersistOutcome(
                achievementId,
                created,
                autoMatched ? 1 : 0,
                candidateCount,
                countFieldConflicts(snapshots));
    }

    @Override
    public void markRawParsed(long rawRecordId) {
        requireSingleRow(mapper.markRawParsed(rawRecordId), "原始记录解析状态更新");
    }

    @Override
    public void markRawFailed(long rawRecordId) {
        requireSingleRow(mapper.markRawFailed(rawRecordId), "原始记录失败状态更新");
    }

    @Override
    public void recordFailure(
            long runId,
            long rawRecordId,
            String externalRecordId,
            String failureStage,
            String errorCategory,
            String safeMessage,
            boolean retryable,
            String evidenceHash) {
        String boundedMessage = safeMessage == null ? "来源记录处理失败" : safeMessage.replaceAll("[\\r\\n]+", " ");
        if (boundedMessage.length() > 1_000) {
            boundedMessage = boundedMessage.substring(0, 1_000);
        }
        requireSingleRow(
                mapper.insertFailure(
                        runId,
                        rawRecordId,
                        externalRecordId,
                        failureStage,
                        errorCategory,
                        boundedMessage,
                        retryable,
                        evidenceHash),
                "采集失败记录写入");
    }

    @Override
    public java.util.List<RetryFailureRecord> findRetryableFailures(long runId, int limit) {
        if (runId < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("失败重试查询参数无效");
        }
        return mapper.findRetryableFailures(runId, limit).stream()
                .map(row -> new RetryFailureRecord(
                        row.getFailureId(),
                        new RawSourceRecord(
                                SourceType.valueOf(row.getSourceType()),
                                row.getExternalRecordId(),
                                URI.create(row.getSourceUrl()),
                                row.getPayload(),
                                row.getFetchedAt())))
                .toList();
    }

    @Override
    public void recordRetryAttempt(long failureId, boolean resolved) {
        requireSingleRow(mapper.recordRetryAttempt(failureId, resolved), "失败记录重试状态更新");
    }

    @Override
    public int clearExpiredPayloads(Instant expiredBefore, int batchSize) {
        if (expiredBefore == null || batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("原始Payload清理参数无效");
        }
        return mapper.clearExpiredPayloads(expiredBefore, batchSize);
    }

    @Override
    public void commitPage(
            long runId,
            PageStatistics statistics,
            List<QualityMetricIncrement> qualityMetrics,
            List<QualityIssueSample> qualityIssueSamples,
            String cursorValue,
            String cursorHash,
            Instant now) {
        requireSingleRow(mapper.updateRunStatistics(runId, statistics), "采集运行统计更新");
        for (QualityMetricIncrement metric : qualityMetrics) {
            requireSingleRow(
                    mapper.upsertQualityMetric(
                            runId, metric.metricCode(), metric.numerator(), metric.denominator(), now),
                    "质量指标写入");
        }
        for (QualityIssueSample sample : qualityIssueSamples) {
            int affected = mapper.insertQualityIssueSample(
                    runId,
                    sample.rawRecordId(),
                    sample.metricCode(),
                    sample.externalRecordId(),
                    serialize(sample.evidence()));
            if (affected < 0 || affected > 1) {
                throw new IllegalStateException("质量问题样本写入数量异常");
            }
        }
        requireSingleRow(
                mapper.upsertCheckpoint(runId, cursorValue, cursorHash, statistics, now),
                "采集检查点写入");
    }

    private EntityUpsert upsertVenue(SourceSnapshot snapshot, String workExternalId, NormalizedVenue venue) {
        if (venue == null || snapshot == null) {
            return new EntityUpsert(null, 0);
        }
        String identifierType;
        String identifierValue;
        if (venue.issnL() != null) {
            identifierType = "ISSN";
            identifierValue = venue.issnL().toUpperCase(java.util.Locale.ROOT);
        } else if (venue.externalId() != null) {
            identifierType = snapshot.sourceType().name();
            identifierValue = venue.externalId();
        } else {
            identifierType = snapshot.sourceType().name();
            identifierValue = workExternalId + "#venue";
        }
        Long venueId = mapper.findVenueByExternalId(identifierType, identifierValue);
        boolean created = venueId == null;
        CanonicalEntityRow row = new CanonicalEntityRow();
        row.setOpenAlexId(snapshot.sourceType() == SourceType.OPENALEX ? venue.externalId() : null);
        row.setDisplayName(venue.displayName());
        row.setIssn(venue.issnL());
        row.setType(venue.type());
        if (created) {
            if (mapper.insertVenue(row) != 1 || row.getId() == null) {
                throw new IllegalStateException("载体写入数量或主键异常");
            }
            venueId = row.getId();
        } else if (snapshot.sourceType() == SourceType.CROSSREF
                || mapper.countVenueExternalType(venueId, "CROSSREF") == 0) {
            row.setId(venueId);
            requireSingleRow(mapper.updateVenue(row), "载体更新");
        }
        mapper.insertVenueExternalId(venueId, identifierType, identifierValue);
        if (snapshot.sourceType() == SourceType.CROSSREF) {
            mapper.insertVenueExternalId(venueId, "CROSSREF", workExternalId + "#venue");
        }
        int candidateCount = created && venue.displayName() != null && venue.issnL() == null
                ? createNameCandidates(
                        "VENUE", snapshot.sourceId(), venueId,
                        mapper.findVenueIdsByName(venue.displayName(), venueId), venue.displayName())
                : 0;
        return new EntityUpsert(venueId, candidateCount);
    }

    private int replaceRelations(long achievementId, CanonicalSelection selection) {
        int candidateCount = 0;
        mapper.deleteAuthorshipOrganizations(achievementId);
        mapper.deleteAchievementAuthors(achievementId);
        mapper.deleteAchievementTopics(achievementId);
        mapper.deleteAchievementSubjects(achievementId);
        mapper.deleteAchievementReferences(achievementId);

        SourceSnapshot authorSnapshot = selection.relationSource(Field.AUTHORS);
        NormalizedWork work = selection.work();
        for (NormalizedAuthorship authorship : work.authorships()) {
            EntityUpsert author = upsertAuthor(authorSnapshot, work.externalId(), authorship);
            long authorId = author.entityId();
            candidateCount += author.candidateCount();
            requireSingleRow(
                    mapper.insertAchievementAuthor(achievementId, authorId, authorship.position()),
                    "成果作者关系写入");
            int organizationPosition = 0;
            for (NormalizedOrganization organization : authorship.organizations()) {
                organizationPosition++;
                EntityUpsert organizationResult = upsertOrganization(
                        authorSnapshot,
                        work.externalId(),
                        authorship.position(),
                        organizationPosition,
                        organization);
                long organizationId = organizationResult.entityId();
                candidateCount += organizationResult.candidateCount();
                requireSingleRow(
                        mapper.insertAuthorshipOrganization(achievementId, authorId, organizationId),
                        "作者机构关系写入");
            }
        }

        int topicPosition = 0;
        SourceSnapshot topicSnapshot = selection.relationSource(Field.TOPICS);
        for (NormalizedTopic topic : work.topics()) {
            topicPosition++;
            String displayName = topic.displayName() == null ? topic.externalId() : topic.displayName();
            String subjectPath = String.join(" > ", java.util.stream.Stream.of(
                            topic.fieldName(), topic.subfieldName(), topic.displayName())
                    .filter(java.util.Objects::nonNull)
                    .toList());
            requireUpsertRows(
                    mapper.upsertSubject(
                            topicSnapshot.sourceId(), topic.externalId(), displayName, subjectPath),
                    "来源主题写入");
            long subjectId = requireId(
                    mapper.findSubjectId(topicSnapshot.sourceId(), topic.externalId()), "来源主题");
            requireSingleRow(
                    mapper.insertAchievementSubject(achievementId, subjectId, topicPosition),
                    "成果来源主题关系写入");
            if (topicSnapshot.sourceType() == SourceType.OPENALEX) {
                requireUpsertRows(mapper.upsertTopic(topic), "OpenAlex主题兼容写入");
                long topicId = requireId(mapper.findTopicId(topic.externalId()), "OpenAlex主题");
                requireSingleRow(
                        mapper.insertAchievementTopic(achievementId, topicId, topicPosition),
                        "成果OpenAlex主题兼容关系写入");
            }
        }

        SourceSnapshot referenceSnapshot = selection.relationSource(Field.REFERENCES);
        String referenceType = referenceSnapshot.sourceType() == SourceType.CROSSREF ? "DOI" : "OPENALEX";
        for (String reference : work.referencedWorkIds()) {
            Long citedAchievementId = mapper.findAchievementByTypedReference(referenceType, reference);
            requireSingleRow(
                    mapper.insertTypedAchievementReference(
                            achievementId, referenceType, reference, citedAchievementId),
                    "成果引用关系写入");
        }
        return candidateCount;
    }

    private EntityUpsert upsertAuthor(
            SourceSnapshot snapshot, String workExternalId, NormalizedAuthorship authorship) {
        String sourceExternalId = authorship.authorExternalId() == null
                ? workExternalId + "#author:" + authorship.position()
                : authorship.authorExternalId();
        Long authorId = authorship.orcid() == null
                ? null : mapper.findAuthorByExternalId("ORCID", authorship.orcid());
        if (authorId == null) {
            authorId = mapper.findAuthorByExternalId(snapshot.sourceType().name(), sourceExternalId);
        }
        boolean created = authorId == null;
        if (authorId == null) {
            GeneratedIdRow row = new GeneratedIdRow();
            row.setDisplayName(authorship.displayName());
            if (mapper.insertAuthor(row) != 1 || row.getId() == null) {
                throw new IllegalStateException("作者写入数量或主键异常");
            }
            authorId = row.getId();
        } else if (snapshot.sourceType() == SourceType.CROSSREF
                || mapper.countAuthorExternalType(authorId, "CROSSREF") == 0) {
            requireSingleRow(mapper.updateAuthor(authorId, authorship.displayName()), "作者更新");
        }
        mapper.insertAuthorExternalId(authorId, snapshot.sourceType().name(), sourceExternalId);
        if (authorship.orcid() != null) {
            mapper.insertAuthorOrcid(authorId, authorship.orcid());
        }
        int candidateCount = 0;
        if (created && authorship.orcid() == null && authorship.displayName() != null) {
            candidateCount = createNameCandidates(
                    "AUTHOR",
                    snapshot.sourceId(),
                    authorId,
                    mapper.findAuthorIdsByName(authorship.displayName(), authorId),
                    authorship.displayName());
        }
        return new EntityUpsert(authorId, candidateCount);
    }

    private EntityUpsert upsertOrganization(
            SourceSnapshot snapshot,
            String workExternalId,
            int authorPosition,
            int organizationPosition,
            NormalizedOrganization organization) {
        boolean hasRor = organization.rorId() != null;
        String identifierType = hasRor ? "ROR" : snapshot.sourceType().name();
        String identifierValue = hasRor ? organization.rorId() : organization.externalId();
        if (identifierValue == null) {
            identifierValue = workExternalId + "#author:" + authorPosition
                    + "#organization:" + organizationPosition;
        }
        Long organizationId = mapper.findOrganizationByExternalId(identifierType, identifierValue);
        boolean created = organizationId == null;
        CanonicalEntityRow row = new CanonicalEntityRow();
        row.setOpenAlexId(snapshot.sourceType() == SourceType.OPENALEX ? organization.externalId() : null);
        row.setDisplayName(organization.displayName());
        row.setCountryCode(organization.countryCode());
        row.setType(organization.type());
        if (created) {
            if (mapper.insertOrganization(row) != 1 || row.getId() == null) {
                throw new IllegalStateException("机构写入数量或主键异常");
            }
            organizationId = row.getId();
        } else if (snapshot.sourceType() == SourceType.CROSSREF
                || mapper.countOrganizationExternalType(organizationId, "CROSSREF") == 0) {
            row.setId(organizationId);
            requireSingleRow(mapper.updateOrganization(row), "机构更新");
        }
        mapper.insertOrganizationExternalId(organizationId, identifierType, identifierValue);
        if (snapshot.sourceType() == SourceType.OPENALEX && organization.externalId() != null) {
            mapper.insertOrganizationExternalId(
                    organizationId, "OPENALEX", organization.externalId());
        }
        if (snapshot.sourceType() == SourceType.CROSSREF) {
            String marker = workExternalId + "#author:" + authorPosition
                    + "#organization:" + organizationPosition;
            mapper.insertOrganizationExternalId(organizationId, "CROSSREF", marker);
        }
        int candidateCount = 0;
        if (created && !hasRor && organization.displayName() != null) {
            candidateCount = createNameCandidates(
                    "ORGANIZATION",
                    snapshot.sourceId(),
                    organizationId,
                    mapper.findOrganizationIdsByName(organization.displayName(), organizationId),
                    organization.displayName());
        }
        return new EntityUpsert(organizationId, candidateCount);
    }

    private int createAchievementCandidates(long achievementId, long sourceId, NormalizedWork work) {
        if (work.doi() != null) {
            return 0;
        }
        int created = 0;
        for (Long otherId : mapper.findAchievementIdsByFingerprint(work.matchFingerprint(), achievementId)) {
            created += createCandidate(
                    "ACHIEVEMENT",
                    sourceId,
                    achievementId,
                    otherId,
                    "FINGERPRINT",
                    Map.of("fingerprintVersion", 1, "fingerprint", work.matchFingerprint()));
        }
        return created;
    }

    private int createNameCandidates(
            String entityType,
            long sourceId,
            long entityId,
            List<Long> otherIds,
            String normalizedName) {
        int created = 0;
        for (Long otherId : otherIds) {
            created += createCandidate(
                    entityType,
                    sourceId,
                    entityId,
                    otherId,
                    "TEXT_NAME",
                    Map.of("normalizedName", normalizedName));
        }
        return created;
    }

    private int createCandidate(
            String entityType,
            long sourceId,
            long firstId,
            long secondId,
            String matchBasis,
            Map<String, Object> evidence) {
        long leftId = Math.min(firstId, secondId);
        long rightId = Math.max(firstId, secondId);
        String evidenceJson = serialize(evidence);
        return mapper.insertDuplicateCandidate(
                entityType, leftId, rightId, matchBasis, sourceId, 1,
                evidenceJson, sha256(evidenceJson));
    }

    private int countFieldConflicts(List<SourceSnapshot> snapshots) {
        if (snapshots.size() < 2) {
            return 0;
        }
        int conflicts = 0;
        for (Field field : Field.values()) {
            if (field == Field.FINGERPRINT) {
                continue;
            }
            long variants = snapshots.stream()
                    .map(snapshot -> fieldValue(field, snapshot.work()))
                    .filter(this::hasValue)
                    .map(this::serialize)
                    .distinct()
                    .count();
            if (variants > 1) {
                conflicts++;
            }
        }
        return conflicts;
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    private List<SourceSnapshot> loadSnapshots(long achievementId) {
        return mapper.findAchievementSnapshots(achievementId).stream()
                .map(row -> new SourceSnapshot(
                        row.getSourceId(),
                        row.getRawRecordId(),
                        SourceType.valueOf(row.getSourceType()),
                        deserialize(row.getNormalizedPayload())))
                .toList();
    }

    private CanonicalSelection selectCanonical(List<SourceSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            throw new IllegalStateException("成果缺少来源快照");
        }
        EnumMap<Field, SourceSnapshot> selections = new EnumMap<>(Field.class);
        selections.put(Field.DOI, select(snapshots, Field.DOI, work -> work.doi() != null));
        selections.put(Field.TITLE, select(snapshots, Field.TITLE,
                work -> work.titleOriginal() != null || work.titleNormalized() != null));
        selections.put(Field.TYPE, select(snapshots, Field.TYPE, work -> work.achievementType() != null));
        selections.put(Field.LANGUAGE, select(snapshots, Field.LANGUAGE, work -> work.language() != null));
        selections.put(Field.DATE, select(snapshots, Field.DATE, work -> work.publicationDate() != null));
        selections.put(Field.FINGERPRINT, select(snapshots, Field.FINGERPRINT, work -> true));
        selections.put(Field.VENUE, select(snapshots, Field.VENUE, work -> work.primaryVenue() != null));
        selections.put(Field.AUTHORS, select(snapshots, Field.AUTHORS, work -> !work.authorships().isEmpty()));
        selections.put(Field.TOPICS, select(snapshots, Field.TOPICS, work -> !work.topics().isEmpty()));
        selections.put(Field.REFERENCES, select(snapshots, Field.REFERENCES,
                work -> !work.referencedWorkIds().isEmpty()));
        selections.put(Field.ABSTRACT, select(snapshots, Field.ABSTRACT, work -> work.abstractText() != null));

        SourceSnapshot first = snapshots.getFirst();
        SourceSnapshot title = selectionOr(selections, Field.TITLE, first);
        SourceSnapshot doi = selectionOr(selections, Field.DOI, first);
        SourceSnapshot type = selectionOr(selections, Field.TYPE, first);
        SourceSnapshot language = selectionOr(selections, Field.LANGUAGE, first);
        SourceSnapshot date = selectionOr(selections, Field.DATE, first);
        SourceSnapshot fingerprint = selectionOr(selections, Field.FINGERPRINT, first);
        SourceSnapshot venue = selectionOr(selections, Field.VENUE, first);
        SourceSnapshot authors = selectionOr(selections, Field.AUTHORS, first);
        SourceSnapshot topics = selectionOr(selections, Field.TOPICS, first);
        SourceSnapshot references = selectionOr(selections, Field.REFERENCES, first);
        SourceSnapshot abstractSource = selectionOr(selections, Field.ABSTRACT, first);
        NormalizedWork canonical = new NormalizedWork(
                title.work().externalId(),
                doi.work().doi(),
                title.work().titleOriginal(),
                title.work().titleNormalized(),
                type.work().achievementType(),
                language.work().language(),
                date.work().publicationDate(),
                date.work().datePrecision(),
                fingerprint.work().matchFingerprint(),
                venue.work().primaryVenue(),
                authors.work().authorships(),
                topics.work().topics(),
                references.work().referencedWorkIds(),
                abstractSource.work().abstractText(),
                authors.work().authorshipsMayBeIncomplete(),
                snapshots.stream().flatMap(snapshot -> snapshot.work().fieldWarnings().stream()).distinct().toList());
        return new CanonicalSelection(canonical, selections, first);
    }

    private SourceSnapshot select(
            List<SourceSnapshot> snapshots, Field field, Predicate<NormalizedWork> predicate) {
        return snapshots.stream()
                .filter(snapshot -> predicate.test(snapshot.work()))
                .min(Comparator
                        .comparingInt((SourceSnapshot snapshot) -> fieldPriority(snapshot.sourceType(), field))
                        .thenComparing(snapshot -> snapshot.sourceType().name())
                        .thenComparingLong(SourceSnapshot::sourceId))
                .orElse(null);
    }

    private SourceSnapshot selectionOr(
            EnumMap<Field, SourceSnapshot> selections, Field field, SourceSnapshot fallback) {
        return selections.get(field) == null ? fallback : selections.get(field);
    }

    private void synchronizeProvenance(
            long achievementId,
            List<SourceSnapshot> snapshots,
            CanonicalSelection selection,
            Instant observedAt) {
        mapper.clearAchievementFieldSelection(achievementId);
        for (SourceSnapshot snapshot : snapshots) {
            for (Field field : Field.values()) {
                SourceSnapshot selected = selection.selections().get(field);
                requireUpsertRows(
                        mapper.upsertFieldProvenance(
                                "ACHIEVEMENT",
                                achievementId,
                                field.databaseName,
                                snapshot.sourceId(),
                                snapshot.rawRecordId(),
                                fieldPriority(snapshot.sourceType(), field),
                                serialize(fieldValue(field, snapshot.work())),
                                selected != null && selected.equals(snapshot),
                                observedAt),
                        "字段来源写入");
            }
        }
    }

    private Object fieldValue(Field field, NormalizedWork work) {
        return switch (field) {
            case DOI -> work.doi();
            case TITLE -> Map.of(
                    "original", work.titleOriginal() == null ? "" : work.titleOriginal(),
                    "normalized", work.titleNormalized() == null ? "" : work.titleNormalized());
            case TYPE -> work.achievementType();
            case LANGUAGE -> work.language();
            case DATE -> work.publicationDate();
            case FINGERPRINT -> work.matchFingerprint();
            case VENUE -> work.primaryVenue();
            case AUTHORS -> work.authorships();
            case TOPICS -> work.topics();
            case REFERENCES -> work.referencedWorkIds();
            case ABSTRACT -> work.abstractText();
        };
    }

    private int baseSourcePriority(SourceType sourceType) {
        return sourceType == SourceType.CROSSREF ? 10 : 20;
    }

    private int fieldPriority(SourceType sourceType, Field field) {
        boolean openAlexPreferred = field == Field.ABSTRACT || field == Field.TOPICS;
        if (openAlexPreferred) {
            return sourceType == SourceType.OPENALEX ? 10 : 20;
        }
        return sourceType == SourceType.CROSSREF ? 10 : 20;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("规范数据序列化失败", exception);
        }
    }

    private NormalizedWork deserialize(String value) {
        try {
            return objectMapper.readValue(value, NormalizedWork.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("规范数据反序列化失败", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private enum Field {
        DOI("doi"),
        TITLE("title"),
        TYPE("achievementType"),
        LANGUAGE("language"),
        DATE("publicationDate"),
        FINGERPRINT("matchFingerprint"),
        VENUE("primaryVenue"),
        AUTHORS("authorships"),
        TOPICS("topics"),
        REFERENCES("references"),
        ABSTRACT("abstract");

        private final String databaseName;

        Field(String databaseName) {
            this.databaseName = databaseName;
        }
    }

    private record SourceSnapshot(
            long sourceId, long rawRecordId, SourceType sourceType, NormalizedWork work) {
    }

    private record EntityUpsert(Long entityId, int candidateCount) {
    }

    private record CanonicalSelection(
            NormalizedWork work,
            EnumMap<Field, SourceSnapshot> selections,
            SourceSnapshot fallback) {

        SourceSnapshot relationSource(Field field) {
            SourceSnapshot selected = selections.get(field);
            return selected == null ? fallback : selected;
        }
    }

    private long requireId(Long id, String resource) {
        if (id == null) {
            throw new IllegalStateException(resource + "主键查询失败");
        }
        return id;
    }

    private void requireSingleRow(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw new IllegalStateException(operation + "数量异常");
        }
    }

    private void requireUpsertRows(int affectedRows, String operation) {
        if (affectedRows < 1 || affectedRows > 2) {
            throw new IllegalStateException(operation + "数量异常");
        }
    }
}
