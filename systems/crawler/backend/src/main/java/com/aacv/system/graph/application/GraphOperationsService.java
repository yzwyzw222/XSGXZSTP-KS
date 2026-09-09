package com.aacv.system.graph.application;

import com.aacv.system.graph.domain.GraphEventView;
import com.aacv.system.graph.domain.GraphOutboxStatus;
import com.aacv.system.graph.domain.GraphSyncStatus;
import com.aacv.system.graph.infrastructure.neo4j.GraphSchemaState;
import com.aacv.system.graph.infrastructure.persistence.GraphOperationsMapper;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.shared.domain.PageResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphOperationsService {

    private static final Duration LAG_THRESHOLD = Duration.ofMinutes(5);
    private final GraphOperationsMapper mapper;
    private final GraphOutboxService outboxService;
    private final GraphSchemaState schemaState;
    private final Driver driver;
    private final AuditService auditService;
    private final Clock clock;

    public GraphOperationsService(
            GraphOperationsMapper mapper,
            GraphOutboxService outboxService,
            GraphSchemaState schemaState,
            Driver driver,
            AuditService auditService,
            Clock clock) {
        this.mapper = mapper;
        this.outboxService = outboxService;
        this.schemaState = schemaState;
        this.driver = driver;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GRAPH_SYNC_READ')")
    public GraphSyncStatus status() {
        return systemStatus();
    }

    @Transactional(readOnly = true)
    public GraphSyncStatus systemStatus() {
        Instant now = clock.instant();
        Long age = mapper.oldestPendingAgeSeconds(now);
        boolean neo4jAvailable = neo4jAvailable();
        return new GraphSyncStatus(
                neo4jAvailable, schemaState.isReady() ? 1 : null,
                mapper.countByStatus("PENDING"), mapper.countByStatus("PROCESSING"),
                mapper.countByStatus("DEAD"), age, mapper.lastSucceededAt(),
                age != null && age > LAG_THRESHOLD.toSeconds(), mapper.rebuildInProgress());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GRAPH_SYNC_READ')")
    public PageResult<GraphEventView> events(GraphOutboxStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        String value = status == null ? null : status.name();
        long total = mapper.countEvents(value);
        return PageResult.of(mapper.findEvents(value, (long) page * size, size), page, size, total);
    }

    @Transactional
    @PreAuthorize("hasAuthority('GRAPH_SYNC_MANAGE')")
    public GraphEventView replay(String eventId) {
        String replayEventId = outboxService.replay(eventId);
        GraphEventView replay = mapper.findEvent(replayEventId);
        if (replay == null) {
            throw new ResourceNotFoundException("重放图事件不存在");
        }
        auditService.record(
                AuditAction.GRAPH_EVENT_REPLAYED, "GRAPH_OUTBOX_EVENT", eventId,
                AuditResult.SUCCESS, Map.of("replayEventId", replayEventId));
        return replay;
    }

    public Instant latestProjectedAt() {
        return mapper.latestProjectedAt();
    }

    public boolean rebuildInProgress() {
        return mapper.rebuildInProgress();
    }

    private boolean neo4jAvailable() {
        if (!schemaState.isReady()) {
            return false;
        }
        try {
            driver.verifyConnectivity();
            return true;
        } catch (Neo4jException exception) {
            return false;
        }
    }
}
