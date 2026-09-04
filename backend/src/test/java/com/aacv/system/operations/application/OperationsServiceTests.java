package com.aacv.system.operations.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aacv.system.graph.application.GraphOperationsService;
import com.aacv.system.graph.domain.GraphSyncStatus;
import com.aacv.system.operations.application.port.OperationsRepository;
import com.aacv.system.operations.domain.HealthStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;

class OperationsServiceTests {

    @Test
    void combinesHealthCrawlGraphAndAlertCounts() {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        OperationsRepository repository = mock(OperationsRepository.class);
        GraphOperationsService graphOperationsService = mock(GraphOperationsService.class);
        HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
        when(graphOperationsService.systemStatus()).thenReturn(new GraphSyncStatus(
                false, null, 3, 1, 2, 360L, null, true, false));
        when(repository.countActiveCrawlRuns()).thenReturn(4L);
        when(repository.countRecentUnresolvedCrawlFailures(now.minusSeconds(86_400))).thenReturn(5L);
        when(repository.countOpenAlerts()).thenReturn(6L);

        var result = new OperationsService(
                repository, graphOperationsService, healthEndpoint, Clock.fixed(now, ZoneOffset.UTC)).overview();

        assertEquals(now, result.generatedAt());
        assertEquals(HealthStatus.UNKNOWN, result.applicationStatus());
        assertEquals(HealthStatus.UNKNOWN, result.mysqlStatus());
        assertEquals(HealthStatus.DOWN, result.neo4jStatus());
        assertEquals(4, result.activeCrawlRunCount());
        assertEquals(5, result.recentCrawlFailureCount());
        assertEquals(3, result.graphPendingCount());
        assertEquals(1, result.graphProcessingCount());
        assertEquals(2, result.graphDeadCount());
        assertEquals(6, result.openAlertCount());
    }
}
