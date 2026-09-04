package com.aacv.system.ingestion.application;

import com.aacv.system.ingestion.application.port.IngestionRepository;
import com.aacv.system.ingestion.application.port.IngestionRepository.PageStatistics;
import com.aacv.system.ingestion.application.port.IngestionRepository.PersistOutcome;
import com.aacv.system.ingestion.application.port.IngestionRepository.QualityMetricIncrement;
import com.aacv.system.ingestion.application.port.IngestionRepository.QualityIssueSample;
import com.aacv.system.ingestion.application.port.IngestionRepository.RawUpsertResult;
import com.aacv.system.ingestion.domain.NormalizedWork;
import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.source.application.DataSourceAdapterRegistry;
import com.aacv.system.source.application.SourceClientException;
import com.aacv.system.source.application.port.DataSourceAdapter;
import com.aacv.system.source.domain.SourcePage;
import com.aacv.system.source.domain.SourceType;
import com.aacv.system.source.domain.SourceWork;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionPageService {

    public static final String PARSER_VERSION = "openalex-v1";
    public static final String TERMINAL_CURSOR = "__END__";

    private final DataSourceAdapterRegistry adapterRegistry;
    private final SourceWorkNormalizer normalizer;
    private final IngestionRepository repository;
    private final Clock clock;

    public IngestionPageService(
            DataSourceAdapterRegistry adapterRegistry,
            SourceWorkNormalizer normalizer,
            IngestionRepository repository,
            Clock clock) {
        this.adapterRegistry = adapterRegistry;
        this.normalizer = normalizer;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public IngestionPageResult processOpenAlexPage(long sourceId, long runId, SourcePage page) {
        return processPage(SourceType.OPENALEX, sourceId, runId, page);
    }

    @Transactional
    public IngestionPageResult processPage(
            SourceType sourceType, long sourceId, long runId, SourcePage page) {
        if (sourceType == null || sourceId < 1 || runId < 1 || page == null) {
            throw new IllegalArgumentException("来源、运行和页面不能为空");
        }
        DataSourceAdapter adapter = adapterRegistry.require(sourceType);
        String parserVersion = adapter.parserVersion();
        long parsed = 0;
        long created = 0;
        long updated = 0;
        long duplicates = 0;
        long failures = 0;
        long missingOrInvalidDoi = 0;
        long missingTitle = 0;
        long missingDate = 0;
        long missingAuthors = 0;
        long authorsWithoutStableId = 0;
        long totalAuthors = 0;
        long organizationsWithoutRor = 0;
        long totalOrganizations = 0;
        long fieldConflicts = 0;
        long autoMatches = 0;
        long newCandidates = 0;
        List<QualityIssueSample> qualityIssueSamples = new ArrayList<>();
        Instant now = clock.instant();
        for (RawSourceRecord rawRecord : page.records()) {
            if (rawRecord.sourceType() != sourceType) {
                throw new IllegalArgumentException("页面包含与任务来源不一致的原始记录");
            }
            String payloadHash = sha256(rawRecord.payload());
            RawUpsertResult raw = repository.upsertRawRecord(
                    sourceId,
                    runId,
                    rawRecord,
                    payloadHash,
                    parserVersion,
                    now.plus(Duration.ofDays(90)),
                    now);
            try {
                SourceWork sourceWork = adapter.parse(rawRecord);
                NormalizedWork normalized = normalizer.normalize(sourceWork);
                if (normalized.doi() == null) {
                    missingOrInvalidDoi++;
                    addSample(qualityIssueSamples, "MISSING_OR_INVALID_DOI", raw, rawRecord, Map.of());
                }
                if (normalized.titleNormalized() == null) {
                    missingTitle++;
                    addSample(qualityIssueSamples, "MISSING_TITLE", raw, rawRecord, Map.of());
                }
                if (normalized.publicationDate() == null) {
                    missingDate++;
                    addSample(qualityIssueSamples, "MISSING_DATE", raw, rawRecord, Map.of());
                }
                if (normalized.authorships().isEmpty()) {
                    missingAuthors++;
                    addSample(qualityIssueSamples, "MISSING_AUTHORS", raw, rawRecord, Map.of());
                }
                totalAuthors += normalized.authorships().size();
                long unstableAuthors = normalized.authorships().stream()
                        .filter(authorship -> authorship.orcid() == null
                                && (sourceType == SourceType.CROSSREF
                                        || authorship.authorExternalId() == null))
                        .count();
                authorsWithoutStableId += unstableAuthors;
                totalOrganizations += normalized.authorships().stream()
                        .flatMap(authorship -> authorship.organizations().stream())
                        .count();
                long organizationsWithoutStableRor = normalized.authorships().stream()
                        .flatMap(authorship -> authorship.organizations().stream())
                        .filter(organization -> organization.rorId() == null)
                        .count();
                organizationsWithoutRor += organizationsWithoutStableRor;
                if (unstableAuthors > 0) {
                    addSample(
                            qualityIssueSamples, "AUTHORS_WITHOUT_STABLE_ID", raw, rawRecord,
                            Map.of("count", unstableAuthors));
                }
                if (organizationsWithoutStableRor > 0) {
                    addSample(
                            qualityIssueSamples, "ORGANIZATIONS_WITHOUT_ROR", raw, rawRecord,
                            Map.of("count", organizationsWithoutStableRor));
                }
                PersistOutcome outcome = repository.persistNormalizedWork(
                        sourceId,
                        raw.rawRecordId(),
                        rawRecord,
                        normalized,
                        parserVersion,
                        now);
                autoMatches += outcome.autoMatchCount();
                newCandidates += outcome.candidateCount();
                fieldConflicts += outcome.fieldConflictCount();
                if (outcome.fieldConflictCount() > 0) {
                    addSample(
                            qualityIssueSamples, "FIELD_CONFLICTS", raw, rawRecord,
                            Map.of("count", outcome.fieldConflictCount()));
                }
                if (outcome.autoMatchCount() > 0) {
                    addSample(qualityIssueSamples, "AUTO_MATCHES", raw, rawRecord, Map.of());
                }
                if (outcome.candidateCount() > 0) {
                    addSample(
                            qualityIssueSamples, "NEW_CANDIDATES", raw, rawRecord,
                            Map.of("count", outcome.candidateCount()));
                }
                repository.markRawParsed(raw.rawRecordId());
                parsed++;
                if (outcome.created()) {
                    created++;
                } else if (raw.duplicatePayload()) {
                    duplicates++;
                } else {
                    updated++;
                }
            } catch (SourceClientException exception) {
                failures++;
                recordRowFailure(
                        runId, rawRecord, raw.rawRecordId(), payloadHash,
                        "PARSE", exception.category(), exception.getMessage());
            } catch (IllegalArgumentException exception) {
                failures++;
                recordRowFailure(
                        runId, rawRecord, raw.rawRecordId(), payloadHash,
                        "VALIDATE", "NORMALIZATION", exception.getMessage());
            }
        }
        String cursor = page.nextCursor() == null ? TERMINAL_CURSOR : page.nextCursor().value();
        PageStatistics statistics = new PageStatistics(
                page.records().size(),
                parsed,
                created,
                updated,
                duplicates,
                failures,
                page.requestCount());
        List<QualityMetricIncrement> qualityMetrics = List.of(
                metric("TOTAL_RECORDS", statistics.readCount(), statistics.readCount()),
                metric("VALID_RECORDS", statistics.parsedCount(), statistics.readCount()),
                metric("MISSING_OR_INVALID_DOI", missingOrInvalidDoi, statistics.readCount()),
                metric("MISSING_TITLE", missingTitle, statistics.readCount()),
                metric("MISSING_DATE", missingDate, statistics.readCount()),
                metric("MISSING_AUTHORS", missingAuthors, statistics.readCount()),
                metric("AUTHORS_WITHOUT_STABLE_ID", authorsWithoutStableId, totalAuthors),
                metric("ORGANIZATIONS_WITHOUT_ROR", organizationsWithoutRor, totalOrganizations),
                metric("FIELD_CONFLICTS", fieldConflicts, statistics.parsedCount()),
                metric("AUTO_MATCHES", autoMatches, statistics.parsedCount()),
                metric("NEW_CANDIDATES", newCandidates, statistics.parsedCount()));
        repository.commitPage(
                runId, statistics, qualityMetrics, qualityIssueSamples,
                cursor, sha256(cursor), now);
        return new IngestionPageResult(
                statistics.readCount(),
                statistics.parsedCount(),
                statistics.createdCount(),
                statistics.updatedCount(),
                statistics.duplicateCount(),
                statistics.failureCount(),
                statistics.requestCount(),
                cursor);
    }

    private QualityMetricIncrement metric(String code, long numerator, long denominator) {
        return new QualityMetricIncrement(code, numerator, denominator);
    }

    private void addSample(
            List<QualityIssueSample> samples,
            String metricCode,
            RawUpsertResult raw,
            RawSourceRecord rawRecord,
            Map<String, Object> evidence) {
        samples.add(new QualityIssueSample(
                metricCode, raw.rawRecordId(), rawRecord.externalRecordId(), evidence));
    }

    private void recordRowFailure(
            long runId,
            RawSourceRecord rawRecord,
            long rawRecordId,
            String payloadHash,
            String stage,
            String category,
            String safeMessage) {
        repository.markRawFailed(rawRecordId);
        repository.recordFailure(
                runId,
                rawRecordId,
                rawRecord.externalRecordId(),
                stage,
                category,
                safeMessage,
                true,
                payloadHash);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}
