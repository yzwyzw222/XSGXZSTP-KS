package com.aacv.system.operations.infrastructure.web;

import com.aacv.system.identity.infrastructure.security.UserPrincipal;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.shared.infrastructure.web.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class FailedOperationAuditFilter extends OncePerRequestFilter {
    public static final String ERROR_CODE = FailedOperationAuditFilter.class.getName() + ".errorCode";
    public static final String LOGIN_RECORDED = FailedOperationAuditFilter.class.getName() + ".loginRecorded";
    private static final Logger LOGGER = LoggerFactory.getLogger(FailedOperationAuditFilter.class);
    private static final List<Operation> OPERATIONS = List.of(
            operation("POST", "users", "USER_CREATED"),
            operation("PUT", "users/([0-9]+)", "USER_UPDATED"),
            operation("POST", "users/([0-9]+)/enable", "USER_ENABLED"),
            operation("POST", "users/([0-9]+)/disable", "USER_DISABLED"),
            operation("POST", "users/([0-9]+)/roles", "USER_ROLES_CHANGED"),
            operation("POST", "users/([0-9]+)/reset-password", "USER_PASSWORD_RESET"),
            operation("POST", "sources", "SOURCE_CREATED"),
            operation("PUT", "sources/([0-9]+)", "SOURCE_UPDATED"),
            operation("POST", "sources/([0-9]+)/enable", "SOURCE_ENABLED"),
            operation("POST", "sources/([0-9]+)/disable", "SOURCE_DISABLED"),
            operation("POST", "sources/([0-9]+)/probe", "SOURCE_PROBED"),
            operation("POST", "crawl/tasks", "CRAWL_TASK_CREATED"),
            operation("PUT", "crawl/tasks/([0-9]+)", "CRAWL_TASK_UPDATED"),
            operation("PUT", "crawl/tasks/([0-9]+)/schedule", "CRAWL_SCHEDULE_CHANGED"),
            operation("POST", "crawl/tasks/([0-9]+)/trigger", "CRAWL_TASK_TRIGGERED"),
            operation("POST", "crawl/runs/([0-9]+)/pause", "CRAWL_RUN_PAUSE_REQUESTED"),
            operation("POST", "crawl/runs/([0-9]+)/resume", "CRAWL_RUN_RESUMED"),
            operation("POST", "crawl/runs/([0-9]+)/cancel", "CRAWL_RUN_CANCEL_REQUESTED"),
            operation("POST", "crawl/runs/([0-9]+)/retry-failures", "CRAWL_FAILURES_RETRIED"),
            operation("POST", "duplicate-candidates/([0-9]+)/accept", "DUPLICATE_CANDIDATE_ACCEPTED"),
            operation("POST", "duplicate-candidates/([0-9]+)/reject", "DUPLICATE_CANDIDATE_REJECTED"),
            operation("POST", "merge-decisions/([0-9]+)/revert", "MERGE_DECISION_REVERTED"),
            operation("POST", "catalog/achievements/([0-9]+)/field-overrides", "ACHIEVEMENT_FIELD_OVERRIDDEN"),
            operation("POST", "catalog/achievements/([0-9]+)/field-overrides/[0-9]+/revert", "ACHIEVEMENT_FIELD_OVERRIDE_REVERTED"),
            operation("POST", "operations/graph-events/([a-zA-Z0-9-]{1,64})/replay", "GRAPH_EVENT_REPLAYED"),
            operation("POST", "operations/graph-maintenance/backfill", "GRAPH_BACKFILL_STARTED"),
            operation("POST", "operations/graph-maintenance/reconcile", "GRAPH_RECONCILIATION_STARTED"),
            operation("POST", "operations/graph-maintenance/rebuild", "GRAPH_REBUILD_STARTED"),
            operation("POST", "exports", "EXPORT_CREATED"),
            operation("GET", "exports/([a-zA-Z0-9-]{1,64})/download", "EXPORT_DOWNLOADED"),
            operation("POST", "operations/alerts/([0-9]+)/acknowledge", "ALERT_ACKNOWLEDGED"));
    private final AuditService auditService;

    public FailedOperationAuditFilter(AuditService auditService) { this.auditService = auditService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        boolean login = "POST".equals(request.getMethod()) && "/api/v1/auth/login".equals(requestPath);
        Operation operation = OPERATIONS.stream().filter(item -> item.method().equals(request.getMethod())
                && item.path().matcher(requestPath).matches()).findFirst().orElse(null);
        if (operation == null && !login) { chain.doFilter(request, response); return; }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Long actor = authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal.userId() : null;
        AuditRequestMetadata metadata = AuditRequestMetadata.from(request);
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            int status = completed ? response.getStatus() : 500;
            if (status >= 400) {
                Object code = request.getAttribute(ERROR_CODE);
                String errorCode = code instanceof String value ? value : "HTTP_" + status;
                try {
                    if (login) {
                        if (!Boolean.TRUE.equals(request.getAttribute(LOGIN_RECORDED))) {
                            auditService.recordRejectedLogin(errorCode, metadata);
                        }
                    } else {
                        var matcher = operation.path().matcher(requestPath);
                        String target = matcher.matches() && matcher.groupCount() > 0 ? matcher.group(1) : null;
                        // 非法超长标识不进入摘要，仍保留该端点的失败事件。
                        if (target != null && target.length() > 128) target = null;
                        auditService.recordFailure(actor, operation.action(), target, status, errorCode, metadata);
                    }
                } catch (RuntimeException auditFailure) {
                    // 原请求已经失败，保留其响应；单独记录审计基础设施故障，不输出请求或异常正文。
                    LOGGER.error("失败审计写入失败，traceId={}，异常类型={}",
                            TraceContext.current(), auditFailure.getClass().getSimpleName());
                }
            }
        }
    }

    private static Operation operation(String method, String path, String action) {
        return new Operation(method, Pattern.compile("/api/v1/" + path), action);
    }

    private record Operation(String method, Pattern path, String action) { }
}
