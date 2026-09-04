package com.aacv.system.operations.application;

import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.operations.application.port.OperationsRepository;
import com.aacv.system.operations.domain.AlertEvent;
import com.aacv.system.operations.domain.AlertStatus;
import com.aacv.system.operations.domain.AlertType;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.shared.domain.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private final OperationsRepository repository;
    private final CurrentActorProvider currentActorProvider;
    private final AuditService auditService;
    private final Clock clock;

    public AlertService(
            OperationsRepository repository,
            CurrentActorProvider currentActorProvider,
            AuditService auditService,
            Clock clock) {
        this.repository = repository;
        this.currentActorProvider = currentActorProvider;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('OPERATIONS_READ')")
    public PageResult<AlertEvent> findPage(AlertStatus status, AlertType type, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return repository.findAlerts(status, type, page, size);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    public AlertEvent acknowledge(long alertId, String reason, long version) {
        if (alertId < 1 || version < 0) {
            throw new IllegalArgumentException("告警标识或版本无效");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty() || normalizedReason.length() > 1000) {
            throw new IllegalArgumentException("确认原因不能为空且不能超过1000个字符");
        }
        AlertEvent current = repository.lockById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("告警事件不存在"));
        if (current.status() != AlertStatus.OPEN || current.version() != version) {
            throw new ResourceConflictException("告警状态或版本已变化");
        }
        long actorId = currentActorProvider.currentUserId()
                .orElseThrow(() -> new ResourceConflictException("当前操作人身份不可用"));
        if (!repository.acknowledge(alertId, actorId, normalizedReason, clock.instant(), version)) {
            throw new ResourceConflictException("告警状态或版本已变化");
        }
        auditService.record(
                AuditAction.ALERT_ACKNOWLEDGED, "ALERT_EVENT", Long.toString(alertId), AuditResult.SUCCESS,
                Map.of("alertType", current.type().name()));
        return repository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("告警事件不存在"));
    }

    @Transactional
    public void reconcile(AlertCondition condition) {
        Instant now = clock.instant();
        var open = repository.findOpenByDedupKey(condition.dedupKey());
        if (open.isPresent()) {
            AlertEvent event = open.get();
            if (!condition.signalAt().isAfter(event.detectedSignalAt())) {
                return;
            }
            repository.updateDetection(
                    event.id(), condition.severity(), condition.summary(), condition.evidence(),
                    condition.signalAt(), now, event.version());
            return;
        }

        var latest = repository.findLatestByDedupKey(condition.dedupKey());
        if (latest.isPresent()
                && latest.get().acknowledgedAt() != null
                && !condition.signalAt().isAfter(latest.get().acknowledgedAt())) {
            return;
        }
        repository.insertOpen(new AlertEvent(
                0, condition.type(), condition.severity(), AlertStatus.OPEN,
                condition.subjectType(), condition.subjectId(), condition.summary(), condition.evidence(),
                condition.signalAt(), now, now, 1, null, null, null, 0), condition.dedupKey());
    }
}
