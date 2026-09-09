package com.aacv.system.operations.application;

import com.aacv.system.graph.application.GraphOperationsService;
import com.aacv.system.graph.domain.GraphSyncStatus;
import com.aacv.system.operations.application.port.OperationsRepository;
import com.aacv.system.operations.domain.HealthStatus;
import com.aacv.system.operations.domain.OperationsOverview;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {

    private final OperationsRepository repository;
    private final GraphOperationsService graphOperationsService;
    private final HealthEndpoint healthEndpoint;
    private final Clock clock;

    public OperationsService(
            OperationsRepository repository,
            GraphOperationsService graphOperationsService,
            HealthEndpoint healthEndpoint,
            Clock clock) {
        this.repository = repository;
        this.graphOperationsService = graphOperationsService;
        this.healthEndpoint = healthEndpoint;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('OPERATIONS_READ')")
    public OperationsOverview overview() {
        var now = clock.instant();
        GraphSyncStatus graph = graphOperationsService.systemStatus();
        return new OperationsOverview(
                now,
                health("livenessState"),
                health("db"),
                graph.neo4jAvailable() ? HealthStatus.UP : HealthStatus.DOWN,
                repository.countActiveCrawlRuns(),
                repository.countRecentUnresolvedCrawlFailures(now.minus(Duration.ofHours(24))),
                graph.pendingCount(),
                graph.processingCount(),
                graph.deadCount(),
                repository.countOpenAlerts());
    }

    private HealthStatus health(String path) {
        try {
            HealthDescriptor descriptor = healthEndpoint.healthForPath(path);
            if (descriptor == null) return HealthStatus.UNKNOWN;
            return switch (descriptor.getStatus().getCode()) {
                case "UP" -> HealthStatus.UP;
                case "DOWN" -> HealthStatus.DOWN;
                case "OUT_OF_SERVICE" -> HealthStatus.DEGRADED;
                default -> HealthStatus.UNKNOWN;
            };
        } catch (RuntimeException exception) {
            return HealthStatus.UNKNOWN;
        }
    }
}
