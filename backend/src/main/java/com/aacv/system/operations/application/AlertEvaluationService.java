package com.aacv.system.operations.application;

import com.aacv.system.graph.application.GraphOperationsService;
import com.aacv.system.graph.domain.GraphSyncStatus;
import com.aacv.system.operations.application.port.OperationsRepository;
import com.aacv.system.operations.domain.AlertSeverity;
import com.aacv.system.operations.domain.AlertSubjectType;
import com.aacv.system.operations.domain.AlertType;
import com.aacv.system.operations.infrastructure.config.OperationsProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AlertEvaluationService {

    private final OperationsRepository repository;
    private final GraphOperationsService graphOperationsService;
    private final AlertService alertService;
    private final OperationsProperties properties;
    private final Clock clock;

    public AlertEvaluationService(
            OperationsRepository repository,
            GraphOperationsService graphOperationsService,
            AlertService alertService,
            OperationsProperties properties,
            Clock clock) {
        this.repository = repository;
        this.graphOperationsService = graphOperationsService;
        this.alertService = alertService;
        this.properties = properties;
        this.clock = clock;
        properties.validate();
    }

    public void evaluate() {
        repository.findSourceFailureSignals(properties.getConsecutiveFailureThreshold())
                .forEach(signal -> alertService.reconcile(new AlertCondition(
                        AlertType.CRAWL_CONSECUTIVE_FAILURES,
                        signal.consecutiveFailures() >= properties.getConsecutiveFailureThreshold() * 2
                                ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                        AlertSubjectType.SOURCE,
                        Long.toString(signal.sourceId()),
                        "数据源连续采集失败达到阈值",
                        Map.of(
                                "consecutiveFailures", signal.consecutiveFailures(),
                                "lastFailureAt", signal.lastFailureAt().toString()),
                        signal.lastFailureAt())));

        repository.findParseRateSignals(
                        properties.getParseMinimumRecords(), properties.getParseSuccessRateThreshold())
                .forEach(signal -> alertService.reconcile(new AlertCondition(
                        AlertType.PARSE_SUCCESS_RATE_DROP,
                        criticalParseRate(signal.successRate()) ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                        AlertSubjectType.CRAWL_TASK,
                        Long.toString(signal.taskId()),
                        "最近一次采集运行的解析成功率低于阈值",
                        Map.of(
                                "runId", signal.runId(),
                                "readCount", signal.readCount(),
                                "parsedCount", signal.parsedCount(),
                                "successRate", signal.successRate().toPlainString(),
                                "finishedAt", signal.finishedAt().toString()),
                        signal.finishedAt())));

        evaluateGraphBacklog(graphOperationsService.systemStatus());
    }

    private boolean criticalParseRate(BigDecimal successRate) {
        return successRate.compareTo(properties.getParseSuccessRateThreshold().divide(BigDecimal.TWO)) < 0;
    }

    private void evaluateGraphBacklog(GraphSyncStatus status) {
        Long age = status.oldestPendingAgeSeconds();
        if (status.deadCount() == 0 && (age == null || age < properties.getGraphBacklogSeconds())) {
            return;
        }
        Instant signalAt = repository.latestGraphSignalAt();
        if (signalAt == null) {
            signalAt = clock.instant();
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("pendingCount", status.pendingCount());
        evidence.put("processingCount", status.processingCount());
        evidence.put("deadCount", status.deadCount());
        if (age != null) {
            evidence.put("oldestPendingAgeSeconds", age);
        }
        alertService.reconcile(new AlertCondition(
                AlertType.GRAPH_SYNC_BACKLOG,
                status.deadCount() > 0 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                AlertSubjectType.GRAPH_SYNC,
                null,
                status.deadCount() > 0 ? "图同步存在死信事件" : "图同步积压时间超过阈值",
                evidence,
                signalAt));
    }
}
