package com.aacv.system.operations.application.port;

import com.aacv.system.operations.domain.AlertEvent;
import com.aacv.system.operations.domain.AlertSeverity;
import com.aacv.system.operations.domain.AlertStatus;
import com.aacv.system.operations.domain.AlertType;
import com.aacv.system.shared.domain.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OperationsRepository {
    long countActiveCrawlRuns();

    long countRecentUnresolvedCrawlFailures(Instant since);

    long countOpenAlerts();

    List<SourceFailureSignal> findSourceFailureSignals(int threshold);

    List<ParseRateSignal> findParseRateSignals(long minimumRecords, BigDecimal successRateThreshold);

    Instant latestGraphSignalAt();

    Optional<AlertEvent> findOpenByDedupKey(String dedupKey);

    Optional<AlertEvent> findLatestByDedupKey(String dedupKey);

    Optional<AlertEvent> findById(long alertId);

    Optional<AlertEvent> lockById(long alertId);

    void insertOpen(AlertEvent event, String dedupKey);

    boolean updateDetection(
            long alertId,
            AlertSeverity severity,
            String summary,
            Map<String, Object> evidence,
            Instant detectedSignalAt,
            Instant detectedAt,
            long expectedVersion);

    boolean acknowledge(long alertId, long actorId, String reason, Instant acknowledgedAt, long expectedVersion);

    PageResult<AlertEvent> findAlerts(AlertStatus status, AlertType type, int page, int size);

    record SourceFailureSignal(long sourceId, int consecutiveFailures, Instant lastFailureAt) {
    }

    record ParseRateSignal(
            long taskId,
            long runId,
            long readCount,
            long parsedCount,
            BigDecimal successRate,
            Instant finishedAt) {
    }
}
