package com.aacv.system.operations.infrastructure.persistence;

import com.aacv.system.operations.application.port.OperationsRepository;
import com.aacv.system.operations.domain.AlertEvent;
import com.aacv.system.operations.domain.AlertSeverity;
import com.aacv.system.operations.domain.AlertStatus;
import com.aacv.system.operations.domain.AlertSubjectType;
import com.aacv.system.operations.domain.AlertType;
import com.aacv.system.shared.domain.PageResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
class MyBatisOperationsRepository implements OperationsRepository {

    private static final TypeReference<Map<String, Object>> EVIDENCE_TYPE = new TypeReference<>() { };
    private final OperationsMapper mapper;
    private final ObjectMapper objectMapper;

    MyBatisOperationsRepository(OperationsMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public long countActiveCrawlRuns() {
        return mapper.countActiveCrawlRuns();
    }

    @Override
    public long countRecentUnresolvedCrawlFailures(Instant since) {
        return mapper.countRecentUnresolvedCrawlFailures(since);
    }

    @Override
    public long countOpenAlerts() {
        return mapper.countOpenAlerts();
    }

    @Override
    public List<SourceFailureSignal> findSourceFailureSignals(int threshold) {
        return mapper.findSourceFailureSignals(threshold);
    }

    @Override
    public List<ParseRateSignal> findParseRateSignals(long minimumRecords, BigDecimal successRateThreshold) {
        return mapper.findParseRateSignals(minimumRecords, successRateThreshold);
    }

    @Override
    public Instant latestGraphSignalAt() {
        return mapper.latestGraphSignalAt();
    }

    @Override
    public Optional<AlertEvent> findOpenByDedupKey(String dedupKey) {
        return Optional.ofNullable(mapper.findOpenByDedupKey(dedupKey)).map(this::toDomain);
    }

    @Override
    public Optional<AlertEvent> findLatestByDedupKey(String dedupKey) {
        return Optional.ofNullable(mapper.findLatestByDedupKey(dedupKey)).map(this::toDomain);
    }

    @Override
    public Optional<AlertEvent> findById(long alertId) {
        return Optional.ofNullable(mapper.findById(alertId)).map(this::toDomain);
    }

    @Override
    public Optional<AlertEvent> lockById(long alertId) {
        return Optional.ofNullable(mapper.lockById(alertId)).map(this::toDomain);
    }

    @Override
    public void insertOpen(AlertEvent event, String dedupKey) {
        AlertEventRow row = new AlertEventRow();
        row.setAlertType(event.type().name());
        row.setSeverity(event.severity().name());
        row.setSubjectType(event.subjectType().name());
        row.setSubjectId(event.subjectId());
        row.setDedupKey(dedupKey);
        row.setSummary(event.summary());
        row.setEvidenceJson(writeEvidence(event.evidence()));
        row.setDetectedSignalAt(event.detectedSignalAt());
        row.setFirstDetectedAt(event.firstDetectedAt());
        row.setLastDetectedAt(event.lastDetectedAt());
        mapper.insertOpen(row);
    }

    @Override
    public boolean updateDetection(
            long alertId,
            AlertSeverity severity,
            String summary,
            Map<String, Object> evidence,
            Instant detectedSignalAt,
            Instant detectedAt,
            long expectedVersion) {
        return mapper.updateDetection(
                alertId, severity.name(), summary, writeEvidence(evidence), detectedSignalAt,
                detectedAt, expectedVersion) == 1;
    }

    @Override
    public boolean acknowledge(
            long alertId, long actorId, String reason, Instant acknowledgedAt, long expectedVersion) {
        return mapper.acknowledge(alertId, actorId, reason, acknowledgedAt, expectedVersion) == 1;
    }

    @Override
    public PageResult<AlertEvent> findAlerts(AlertStatus status, AlertType type, int page, int size) {
        String statusValue = status == null ? null : status.name();
        String typeValue = type == null ? null : type.name();
        List<AlertEvent> items = mapper.findAlerts(statusValue, typeValue, (long) page * size, size)
                .stream().map(this::toDomain).toList();
        return PageResult.of(items, page, size, mapper.countAlerts(statusValue, typeValue));
    }

    private AlertEvent toDomain(AlertEventRow row) {
        return new AlertEvent(
                row.getId(), AlertType.valueOf(row.getAlertType()), AlertSeverity.valueOf(row.getSeverity()),
                AlertStatus.valueOf(row.getStatus()), AlertSubjectType.valueOf(row.getSubjectType()),
                row.getSubjectId(), row.getSummary(), readEvidence(row.getEvidenceJson()),
                row.getDetectedSignalAt(), row.getFirstDetectedAt(), row.getLastDetectedAt(),
                row.getOccurrenceCount(), row.getAcknowledgedBy(), row.getAcknowledgedAt(),
                row.getAcknowledgementReason(), row.getVersion());
    }

    private String writeEvidence(Map<String, Object> evidence) {
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JacksonException exception) {
            throw new IllegalStateException("告警证据无法序列化", exception);
        }
    }

    private Map<String, Object> readEvidence(String evidenceJson) {
        try {
            return objectMapper.readValue(evidenceJson, EVIDENCE_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("告警证据无法读取", exception);
        }
    }
}
