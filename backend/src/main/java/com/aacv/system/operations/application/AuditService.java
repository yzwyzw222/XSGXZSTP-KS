package com.aacv.system.operations.application;

import com.aacv.system.operations.application.port.AuditLogRepository;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditQuery;
import com.aacv.system.operations.infrastructure.web.AuditRequestMetadata;
import org.springframework.transaction.annotation.Propagation;
import com.aacv.system.operations.domain.AuditLogEntry;
import com.aacv.system.operations.domain.AuditRecord;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.domain.PageResult;
import com.aacv.system.shared.infrastructure.web.TraceContext;
import java.time.Clock;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentActorProvider currentActorProvider;
    private final Clock clock;

    public AuditService(
            AuditLogRepository repository, CurrentActorProvider currentActorProvider, Clock clock) {
        this.repository = repository;
        this.currentActorProvider = currentActorProvider;
        this.clock = clock;
    }

    @Transactional
    public void record(
            AuditAction action,
            String targetType,
            String targetId,
            AuditResult result,
            Map<String, String> summary) {
        OptionalLong currentUserId = currentActorProvider.currentUserId();
        append(currentUserId.isPresent() ? currentUserId.getAsLong() : null,
                action, targetType, targetId, result, summary);
    }

    @Transactional
    public void recordForActor(
            long actorUserId,
            AuditAction action,
            String targetType,
            String targetId,
            AuditResult result,
            Map<String, String> summary) {
        append(actorUserId, action, targetType, targetId, result, summary);
    }

    private void append(
            Long actorUserId,
            AuditAction action,
            String targetType,
            String targetId,
            AuditResult result,
            Map<String, String> summary) {
        AuditRequestMetadata metadata = AuditRequestMetadata.current();
        repository.append(new AuditRecord(
                actorUserId,
                action,
                targetType,
                targetId,
                result,
                TraceContext.current(),
                summary,
                clock.instant(), metadata.clientIp(), metadata.userAgent()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(String username, String reason) {
        append(null, AuditAction.LOGIN_FAILED, "USER_ACCOUNT", username, AuditResult.FAILURE, Map.of("reason", reason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejectedLogin(String errorCode, AuditRequestMetadata metadata) {
        repository.append(new AuditRecord(null, AuditAction.LOGIN_FAILED, "USER_ACCOUNT", null,
                AuditResult.FAILURE, TraceContext.current(), Map.of("reason", errorCode), clock.instant(),
                metadata.clientIp(), metadata.userAgent()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long actorUserId, String operation, String targetId, int status, String errorCode,
            AuditRequestMetadata metadata) {
        repository.append(new AuditRecord(actorUserId, AuditAction.OPERATION_FAILED, "API_OPERATION", targetId,
                AuditResult.FAILURE, TraceContext.current(),
                Map.of("operation", operation, "httpStatus", Integer.toString(status), "errorCode", errorCode),
                clock.instant(), metadata.clientIp(), metadata.userAgent()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public PageResult<AuditLogEntry> findPage(int page, int size) {
        return findPage(page, size, AuditQuery.ALL);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public PageResult<AuditLogEntry> findPage(int page, int size, AuditQuery query) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return repository.findPage(page, size, query == null ? AuditQuery.ALL : query);
    }
}
