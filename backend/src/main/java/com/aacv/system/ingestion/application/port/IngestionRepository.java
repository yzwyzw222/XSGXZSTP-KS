package com.aacv.system.ingestion.application.port;

import com.aacv.system.ingestion.domain.NormalizedWork;
import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.ingestion.domain.RetryFailureRecord;
import java.time.Instant;
import java.util.List;

public interface IngestionRepository {

    RawUpsertResult upsertRawRecord(
            long sourceId,
            long runId,
            RawSourceRecord record,
            String payloadHash,
            String parserVersion,
            Instant payloadExpiresAt,
            Instant now);

    PersistOutcome persistNormalizedWork(
            long sourceId,
            long rawRecordId,
            RawSourceRecord rawRecord,
            NormalizedWork work,
            String parserVersion,
            Instant now);

    void markRawParsed(long rawRecordId);

    void markRawFailed(long rawRecordId);

    void recordFailure(
            long runId,
            long rawRecordId,
            String externalRecordId,
            String failureStage,
            String errorCategory,
            String safeMessage,
            boolean retryable,
            String evidenceHash);

    List<RetryFailureRecord> findRetryableFailures(long runId, int limit);

    void recordRetryAttempt(long failureId, boolean resolved);

    int clearExpiredPayloads(Instant expiredBefore, int batchSize);

    void commitPage(
            long runId,
            PageStatistics statistics,
            List<QualityMetricIncrement> qualityMetrics,
            List<QualityIssueSample> qualityIssueSamples,
            String cursorValue,
            String cursorHash,
            Instant now);

    record RawUpsertResult(long rawRecordId, boolean duplicatePayload) {
    }

    record PersistOutcome(
            long achievementId,
            boolean created,
            long autoMatchCount,
            long candidateCount,
            long fieldConflictCount) {
    }

    record QualityMetricIncrement(String metricCode, long numerator, long denominator) {

        public QualityMetricIncrement {
            if (metricCode == null || metricCode.isBlank() || numerator < 0 || denominator < 0) {
                throw new IllegalArgumentException("质量指标增量无效");
            }
        }
    }

    record QualityIssueSample(
            String metricCode,
            long rawRecordId,
            String externalRecordId,
            java.util.Map<String, Object> evidence) {

        public QualityIssueSample {
            evidence = evidence == null ? java.util.Map.of() : java.util.Map.copyOf(evidence);
            if (metricCode == null || metricCode.isBlank() || rawRecordId < 1
                    || externalRecordId == null || externalRecordId.isBlank()) {
                throw new IllegalArgumentException("质量问题样本无效");
            }
        }
    }

    record PageStatistics(
            long readCount,
            long parsedCount,
            long createdCount,
            long updatedCount,
            long duplicateCount,
            long failureCount,
            long requestCount) {
    }
}
