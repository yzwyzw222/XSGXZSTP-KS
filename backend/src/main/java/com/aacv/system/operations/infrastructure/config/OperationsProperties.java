package com.aacv.system.operations.infrastructure.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aacv.operations")
public class OperationsProperties {
    private int alertEvaluationIntervalSeconds = 60;
    private int consecutiveFailureThreshold = 3;
    private long parseMinimumRecords = 20;
    private BigDecimal parseSuccessRateThreshold = new BigDecimal("0.80");
    private long graphBacklogSeconds = 300;

    public int getAlertEvaluationIntervalSeconds() {
        return alertEvaluationIntervalSeconds;
    }

    public void setAlertEvaluationIntervalSeconds(int alertEvaluationIntervalSeconds) {
        this.alertEvaluationIntervalSeconds = alertEvaluationIntervalSeconds;
    }

    public int getConsecutiveFailureThreshold() {
        return consecutiveFailureThreshold;
    }

    public void setConsecutiveFailureThreshold(int consecutiveFailureThreshold) {
        this.consecutiveFailureThreshold = consecutiveFailureThreshold;
    }

    public long getParseMinimumRecords() {
        return parseMinimumRecords;
    }

    public void setParseMinimumRecords(long parseMinimumRecords) {
        this.parseMinimumRecords = parseMinimumRecords;
    }

    public BigDecimal getParseSuccessRateThreshold() {
        return parseSuccessRateThreshold;
    }

    public void setParseSuccessRateThreshold(BigDecimal parseSuccessRateThreshold) {
        this.parseSuccessRateThreshold = parseSuccessRateThreshold;
    }

    public long getGraphBacklogSeconds() {
        return graphBacklogSeconds;
    }

    public void setGraphBacklogSeconds(long graphBacklogSeconds) {
        this.graphBacklogSeconds = graphBacklogSeconds;
    }

    public void validate() {
        if (alertEvaluationIntervalSeconds < 10 || alertEvaluationIntervalSeconds > 3600
                || consecutiveFailureThreshold < 1 || consecutiveFailureThreshold > 100
                || parseMinimumRecords < 1 || parseMinimumRecords > 1_000_000
                || parseSuccessRateThreshold == null
                || parseSuccessRateThreshold.compareTo(BigDecimal.ZERO) <= 0
                || parseSuccessRateThreshold.compareTo(BigDecimal.ONE) >= 0
                || graphBacklogSeconds < 60 || graphBacklogSeconds > 86_400) {
            throw new IllegalStateException("运维告警配置无效");
        }
    }
}
