package com.aacv.system.operations.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.operations.application.port.OperationsRepository;
import com.aacv.system.operations.domain.AlertEvent;
import com.aacv.system.operations.domain.AlertSeverity;
import com.aacv.system.operations.domain.AlertStatus;
import com.aacv.system.operations.domain.AlertSubjectType;
import com.aacv.system.operations.domain.AlertType;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class AlertServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void repeatedSignalDoesNotMutateExistingOpenAlert() {
        OperationsRepository repository = mock(OperationsRepository.class);
        AlertEvent existing = event(AlertStatus.OPEN, NOW.minusSeconds(30), null, 2);
        when(repository.findOpenByDedupKey(anyString())).thenReturn(Optional.of(existing));

        service(repository).reconcile(condition(existing.detectedSignalAt()));

        verify(repository, never()).updateDetection(
                any(Long.class), any(), anyString(), any(), any(), any(), any(Long.class));
        verify(repository, never()).insertOpen(any(), anyString());
    }

    @Test
    void newerSignalUpdatesSameOpenAlert() {
        OperationsRepository repository = mock(OperationsRepository.class);
        AlertEvent existing = event(AlertStatus.OPEN, NOW.minusSeconds(60), null, 2);
        when(repository.findOpenByDedupKey(anyString())).thenReturn(Optional.of(existing));

        service(repository).reconcile(condition(NOW.minusSeconds(30)));

        verify(repository).updateDetection(
                existing.id(), AlertSeverity.WARNING, "数据源连续采集失败达到阈值",
                Map.of("consecutiveFailures", 3), NOW.minusSeconds(30), NOW, existing.version());
    }

    @Test
    void acknowledgedAlertSuppressesAnOldSignal() {
        OperationsRepository repository = mock(OperationsRepository.class);
        AlertEvent existing = event(AlertStatus.ACKNOWLEDGED, NOW.minusSeconds(60), NOW.minusSeconds(30), 3);
        when(repository.findOpenByDedupKey(anyString())).thenReturn(Optional.empty());
        when(repository.findLatestByDedupKey(anyString())).thenReturn(Optional.of(existing));

        service(repository).reconcile(condition(NOW.minusSeconds(45)));

        verify(repository, never()).insertOpen(any(), anyString());
    }

    @Test
    void acknowledgementUsesVersionAndWritesSafeAudit() {
        OperationsRepository repository = mock(OperationsRepository.class);
        CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
        AuditService auditService = mock(AuditService.class);
        AlertEvent open = event(AlertStatus.OPEN, NOW.minusSeconds(30), null, 4);
        AlertEvent acknowledged = event(AlertStatus.ACKNOWLEDGED, NOW.minusSeconds(30), NOW, 5);
        when(repository.lockById(1)).thenReturn(Optional.of(open));
        when(repository.acknowledge(1, 7, "已核查", NOW, 4)).thenReturn(true);
        when(repository.findById(1)).thenReturn(Optional.of(acknowledged));
        when(actorProvider.currentUserId()).thenReturn(OptionalLong.of(7));

        AlertEvent result = new AlertService(repository, actorProvider, auditService, CLOCK)
                .acknowledge(1, " 已核查 ", 4);

        assertEquals(AlertStatus.ACKNOWLEDGED, result.status());
        verify(auditService).record(
                AuditAction.ALERT_ACKNOWLEDGED, "ALERT_EVENT", "1", AuditResult.SUCCESS,
                Map.of("alertType", "CRAWL_CONSECUTIVE_FAILURES"));
    }

    @Test
    void acknowledgementRejectsStaleVersion() {
        OperationsRepository repository = mock(OperationsRepository.class);
        when(repository.lockById(1)).thenReturn(Optional.of(event(AlertStatus.OPEN, NOW, null, 4)));

        assertThrows(ResourceConflictException.class, () -> service(repository).acknowledge(1, "已核查", 3));
    }

    private AlertService service(OperationsRepository repository) {
        return new AlertService(
                repository, mock(CurrentActorProvider.class), mock(AuditService.class), CLOCK);
    }

    private AlertCondition condition(Instant signalAt) {
        return new AlertCondition(
                AlertType.CRAWL_CONSECUTIVE_FAILURES, AlertSeverity.WARNING, AlertSubjectType.SOURCE,
                "1", "数据源连续采集失败达到阈值", Map.of("consecutiveFailures", 3), signalAt);
    }

    private AlertEvent event(AlertStatus status, Instant signalAt, Instant acknowledgedAt, long version) {
        return new AlertEvent(
                1, AlertType.CRAWL_CONSECUTIVE_FAILURES, AlertSeverity.WARNING, status,
                AlertSubjectType.SOURCE, "1", "数据源连续采集失败达到阈值",
                Map.of("consecutiveFailures", 3), signalAt, NOW.minusSeconds(120), NOW.minusSeconds(30),
                1, acknowledgedAt == null ? null : 7L, acknowledgedAt,
                acknowledgedAt == null ? null : "已核查", version);
    }
}
