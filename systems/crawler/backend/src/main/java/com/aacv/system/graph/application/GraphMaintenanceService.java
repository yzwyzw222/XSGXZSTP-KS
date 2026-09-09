package com.aacv.system.graph.application;

import com.aacv.system.graph.application.port.GraphMaintenanceLaunchPort;
import com.aacv.system.graph.domain.GraphMaintenanceRun;
import com.aacv.system.graph.domain.GraphMaintenanceStatus;
import com.aacv.system.graph.domain.GraphMaintenanceType;
import com.aacv.system.graph.infrastructure.neo4j.Neo4jProjectionInspector;
import com.aacv.system.graph.infrastructure.persistence.GraphMaintenanceMapper;
import com.aacv.system.graph.infrastructure.persistence.GraphMaintenanceRow;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.shared.domain.PageResult;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class GraphMaintenanceService {

    private final GraphMaintenanceMapper mapper;
    private final GraphMaintenancePageService pageService;
    private final GraphMaintenanceLaunchPort launchPort;
    private final CurrentActorProvider actorProvider;
    private final AuditService auditService;
    private final Neo4jProjectionInspector inspector;
    private final GraphMaintenanceStateService stateService;

    public GraphMaintenanceService(
            GraphMaintenanceMapper mapper,
            GraphMaintenancePageService pageService,
            GraphMaintenanceLaunchPort launchPort,
            CurrentActorProvider actorProvider,
            AuditService auditService,
            Neo4jProjectionInspector inspector,
            GraphMaintenanceStateService stateService) {
        this.mapper = mapper;
        this.pageService = pageService;
        this.launchPort = launchPort;
        this.actorProvider = actorProvider;
        this.auditService = auditService;
        this.inspector = inspector;
        this.stateService = stateService;
    }

    @Transactional
    @PreAuthorize("hasAuthority('GRAPH_SYNC_MANAGE')")
    public GraphMaintenanceRun start(GraphMaintenanceType type) {
        long actorId = actorProvider.currentUserId()
                .orElseThrow(() -> new ResourceConflictException("当前操作人身份不可用"));
        GraphMaintenanceRun failed = mapper.findLatestFailed(type.name());
        long runId;
        if (failed != null && type != GraphMaintenanceType.FULL_REBUILD) {
            if (mapper.resumeRun(failed.id(), actorId) != 1) {
                throw new ResourceConflictException("图维护运行状态已变化");
            }
            runId = failed.id();
        } else {
            GraphMaintenanceRow row = new GraphMaintenanceRow();
            row.setRunType(type);
            row.setStatus(GraphMaintenanceStatus.PENDING);
            row.setRequestedBy(actorId);
            try {
                if (mapper.insertRun(row) != 1 || row.getId() == null) {
                    throw new IllegalStateException("图维护运行写入数量异常");
                }
            } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                throw new ResourceConflictException("已有图维护运行正在执行");
            }
            runId = row.getId();
        }
        auditService.record(action(type), "GRAPH_MAINTENANCE_RUN", Long.toString(runId),
                AuditResult.SUCCESS, Map.of("runType", type.name()));
        launchPort.launchAfterCommit(runId);
        return requireRun(runId);
    }

    // 每页独立提交后必须读取新游标，避免复用Batch外层事务的旧快照而重复处理同一页。
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void execute(long runId) {
        GraphMaintenanceRun run = requireRun(runId);
        try {
            stateService.markRunning(runId);
            if (run.runType() == GraphMaintenanceType.FULL_REBUILD) {
                inspector.deleteManagedProjection();
            }
            long cursor = run.cursorAchievementId();
            boolean force = run.runType() != GraphMaintenanceType.RECONCILE;
            int processed;
            do {
                processed = pageService.processNext(runId, cursor, force);
                if (processed > 0) {
                    long nextCursor = stateService.currentCursor(runId);
                    if (nextCursor <= cursor) {
                        throw new IllegalStateException("图维护游标未向前推进");
                    }
                    cursor = nextCursor;
                }
            } while (processed > 0);
            stateService.markSucceeded(runId);
        } catch (RuntimeException exception) {
            stateService.markFailed(runId, "GRAPH_MAINTENANCE_FAILED", "图维护运行失败");
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GRAPH_SYNC_READ')")
    public PageResult<GraphMaintenanceRun> runs(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return PageResult.of(mapper.findRuns((long) page * size, size), page, size, mapper.countRuns());
    }

    @Transactional(readOnly = true)
    public GraphMaintenanceRun requireRun(long runId) {
        GraphMaintenanceRun run = mapper.findRun(runId);
        if (run == null) {
            throw new ResourceNotFoundException("图维护运行不存在");
        }
        return run;
    }

    private AuditAction action(GraphMaintenanceType type) {
        return switch (type) {
            case INITIAL_BACKFILL -> AuditAction.GRAPH_BACKFILL_STARTED;
            case RECONCILE -> AuditAction.GRAPH_RECONCILIATION_STARTED;
            case FULL_REBUILD -> AuditAction.GRAPH_REBUILD_STARTED;
        };
    }
}
