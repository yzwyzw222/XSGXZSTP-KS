CREATE TABLE alert_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    alert_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(64) NULL,
    dedup_key VARCHAR(190) NOT NULL,
    open_dedup_key VARCHAR(190) GENERATED ALWAYS AS (
        CASE WHEN status = 'OPEN' THEN dedup_key ELSE NULL END
    ) STORED,
    summary VARCHAR(500) NOT NULL,
    evidence_json JSON NOT NULL,
    detected_signal_at TIMESTAMP(6) NOT NULL,
    first_detected_at TIMESTAMP(6) NOT NULL,
    last_detected_at TIMESTAMP(6) NOT NULL,
    occurrence_count BIGINT NOT NULL DEFAULT 1,
    acknowledged_by BIGINT NULL,
    acknowledged_at TIMESTAMP(6) NULL,
    acknowledgement_reason VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_alert_event PRIMARY KEY (id),
    CONSTRAINT fk_alert_event_acknowledger FOREIGN KEY (acknowledged_by) REFERENCES sys_user (id),
    CONSTRAINT uk_alert_event_open_dedup UNIQUE (open_dedup_key),
    CONSTRAINT ck_alert_event_type CHECK (
        alert_type IN ('CRAWL_CONSECUTIVE_FAILURES', 'PARSE_SUCCESS_RATE_DROP', 'GRAPH_SYNC_BACKLOG')
    ),
    CONSTRAINT ck_alert_event_severity CHECK (severity IN ('WARNING', 'CRITICAL')),
    CONSTRAINT ck_alert_event_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED')),
    CONSTRAINT ck_alert_event_subject CHECK (subject_type IN ('SOURCE', 'CRAWL_TASK', 'GRAPH_SYNC')),
    CONSTRAINT ck_alert_event_occurrences CHECK (occurrence_count >= 1),
    CONSTRAINT ck_alert_event_acknowledgement CHECK (
        (status = 'OPEN' AND acknowledged_by IS NULL AND acknowledged_at IS NULL AND acknowledgement_reason IS NULL)
        OR
        (status = 'ACKNOWLEDGED' AND acknowledged_by IS NOT NULL AND acknowledged_at IS NOT NULL
            AND acknowledgement_reason IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_alert_event_status_detected
    ON alert_event (status, last_detected_at, id);
CREATE INDEX ix_alert_event_type_status
    ON alert_event (alert_type, status, last_detected_at, id);
CREATE INDEX ix_alert_event_subject
    ON alert_event (subject_type, subject_id, last_detected_at, id);
