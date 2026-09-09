package com.aacv.system.operations.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.graph.application.GraphOperationsService;
import com.aacv.system.graph.domain.GraphSyncStatus;
import com.aacv.system.operations.application.port.OperationsRepository;
import com.aacv.system.operations.application.port.OperationsRepository.ParseRateSignal;
import com.aacv.system.operations.application.port.OperationsRepository.SourceFailureSignal;
import com.aacv.system.operations.domain.AlertSeverity;
import com.aacv.system.operations.domain.AlertType;
import com.aacv.system.operations.infrastructure.config.OperationsProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlertEvaluationServiceTests {

    @Test
    void evaluatesAllThreeBoundedAlertTypes() {
        Instant now = Instant.parse("2026-09-03T00:00:00Z");
        OperationsRepository repository = mock(OperationsRepository.class);
        GraphOperationsService graphOperationsService = mock(GraphOperationsService.class);
        AlertService alertService = mock(AlertService.class);
        when(repository.findSourceFailureSignals(3))
                .thenReturn(List.of(new SourceFailureSignal(2, 6, now.minusSeconds(10))));
        when(repository.findParseRateSignals(20, new BigDecimal("0.80")))
                .thenReturn(List.of(new ParseRateSignal(
                        4, 9, 100, 35, new BigDecimal("0.350000"), now.minusSeconds(20))));
        when(graphOperationsService.systemStatus()).thenReturn(new GraphSyncStatus(
                false, 1, 5, 1, 2, 600L, null, true, false));
        when(repository.latestGraphSignalAt()).thenReturn(now.minusSeconds(5));

        new AlertEvaluationService(
                repository, graphOperationsService, alertService, new OperationsProperties(),
                Clock.fixed(now, ZoneOffset.UTC)).evaluate();

        ArgumentCaptor<AlertCondition> captor = ArgumentCaptor.forClass(AlertCondition.class);
        verify(alertService, times(3)).reconcile(captor.capture());
        assertEquals(
                List.of(
                        AlertType.CRAWL_CONSECUTIVE_FAILURES,
                        AlertType.PARSE_SUCCESS_RATE_DROP,
                        AlertType.GRAPH_SYNC_BACKLOG),
                captor.getAllValues().stream().map(AlertCondition::type).toList());
        assertEquals(AlertSeverity.CRITICAL, captor.getAllValues().get(0).severity());
        assertEquals(AlertSeverity.CRITICAL, captor.getAllValues().get(1).severity());
        assertEquals(AlertSeverity.CRITICAL, captor.getAllValues().get(2).severity());
    }

    @Test
    void skipsGraphAlertBelowBacklogThreshold() {
        OperationsRepository repository = mock(OperationsRepository.class);
        GraphOperationsService graphOperationsService = mock(GraphOperationsService.class);
        AlertService alertService = mock(AlertService.class);
        when(repository.findSourceFailureSignals(3)).thenReturn(List.of());
        when(repository.findParseRateSignals(20, new BigDecimal("0.80"))).thenReturn(List.of());
        when(graphOperationsService.systemStatus()).thenReturn(new GraphSyncStatus(
                true, 1, 2, 0, 0, 299L, null, false, false));

        new AlertEvaluationService(
                repository, graphOperationsService, alertService, new OperationsProperties(),
                Clock.systemUTC()).evaluate();

        verify(alertService, times(0)).reconcile(any());
    }
}
