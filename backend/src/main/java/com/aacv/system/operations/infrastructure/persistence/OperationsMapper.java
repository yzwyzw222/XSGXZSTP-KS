package com.aacv.system.operations.infrastructure.persistence;

import com.aacv.system.operations.application.port.OperationsRepository.ParseRateSignal;
import com.aacv.system.operations.application.port.OperationsRepository.SourceFailureSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface OperationsMapper {
    long countActiveCrawlRuns();

    long countRecentUnresolvedCrawlFailures(@Param("since") Instant since);

    long countOpenAlerts();

    List<SourceFailureSignal> findSourceFailureSignals(@Param("threshold") int threshold);

    List<ParseRateSignal> findParseRateSignals(
            @Param("minimumRecords") long minimumRecords,
            @Param("successRateThreshold") BigDecimal successRateThreshold);

    Instant latestGraphSignalAt();

    AlertEventRow findOpenByDedupKey(@Param("dedupKey") String dedupKey);

    AlertEventRow findLatestByDedupKey(@Param("dedupKey") String dedupKey);

    AlertEventRow findById(@Param("alertId") long alertId);

    AlertEventRow lockById(@Param("alertId") long alertId);

    int insertOpen(AlertEventRow row);

    int updateDetection(
            @Param("alertId") long alertId,
            @Param("severity") String severity,
            @Param("summary") String summary,
            @Param("evidenceJson") String evidenceJson,
            @Param("detectedSignalAt") Instant detectedSignalAt,
            @Param("detectedAt") Instant detectedAt,
            @Param("expectedVersion") long expectedVersion);

    int acknowledge(
            @Param("alertId") long alertId,
            @Param("actorId") long actorId,
            @Param("reason") String reason,
            @Param("acknowledgedAt") Instant acknowledgedAt,
            @Param("expectedVersion") long expectedVersion);

    long countAlerts(@Param("status") String status, @Param("type") String type);

    List<AlertEventRow> findAlerts(
            @Param("status") String status,
            @Param("type") String type,
            @Param("offset") long offset,
            @Param("size") int size);
}
