CREATE TABLE graph_projection_state (
    achievement_id BIGINT NOT NULL,
    desired_version BIGINT NOT NULL DEFAULT 0,
    applied_version BIGINT NOT NULL DEFAULT 0,
    last_enqueued_at TIMESTAMP(6) NULL,
    last_projected_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_graph_projection_state PRIMARY KEY (achievement_id),
    CONSTRAINT fk_graph_projection_achievement FOREIGN KEY (achievement_id)
        REFERENCES achievement (id) ON DELETE CASCADE,
    CONSTRAINT ck_graph_projection_versions CHECK (
        desired_version >= 0 AND applied_version >= 0 AND desired_version >= applied_version
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_graph_projection_lag
    ON graph_projection_state (applied_version, desired_version, achievement_id);

CREATE TABLE graph_outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL DEFAULT 'ACHIEVEMENT',
    achievement_id BIGINT NOT NULL,
    desired_version BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL DEFAULT 'REFRESH',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    locked_by VARCHAR(128) NULL,
    locked_until TIMESTAMP(6) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_summary VARCHAR(512) NULL,
    replay_of_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT pk_graph_outbox_event PRIMARY KEY (id),
    CONSTRAINT fk_graph_outbox_achievement FOREIGN KEY (achievement_id)
        REFERENCES achievement (id) ON DELETE CASCADE,
    CONSTRAINT fk_graph_outbox_replay FOREIGN KEY (replay_of_event_id)
        REFERENCES graph_outbox_event (event_id),
    CONSTRAINT uk_graph_outbox_event_id UNIQUE (event_id),
    CONSTRAINT uk_graph_outbox_aggregate_version UNIQUE (achievement_id, desired_version),
    CONSTRAINT ck_graph_outbox_aggregate CHECK (aggregate_type = 'ACHIEVEMENT'),
    CONSTRAINT ck_graph_outbox_event_type CHECK (event_type = 'REFRESH'),
    CONSTRAINT ck_graph_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'DEAD')),
    CONSTRAINT ck_graph_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_graph_outbox_desired_version CHECK (desired_version > 0),
    CONSTRAINT ck_graph_outbox_lock CHECK (
        (status = 'PROCESSING' AND locked_by IS NOT NULL AND locked_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND locked_by IS NULL AND locked_until IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_graph_outbox_claim
    ON graph_outbox_event (status, next_attempt_at, achievement_id, desired_version, id);
CREATE INDEX ix_graph_outbox_lease
    ON graph_outbox_event (status, locked_until, id);
CREATE INDEX ix_graph_outbox_aggregate
    ON graph_outbox_event (achievement_id, status, desired_version, id);

CREATE TABLE graph_sync_dead_letter (
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    error_summary VARCHAR(512) NOT NULL,
    failed_at TIMESTAMP(6) NOT NULL,
    replay_event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_graph_sync_dead_letter PRIMARY KEY (event_id),
    CONSTRAINT fk_graph_dead_letter_event FOREIGN KEY (event_id)
        REFERENCES graph_outbox_event (event_id) ON DELETE CASCADE,
    CONSTRAINT fk_graph_dead_letter_replay FOREIGN KEY (replay_event_id)
        REFERENCES graph_outbox_event (event_id),
    CONSTRAINT uk_graph_dead_letter_replay UNIQUE (replay_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_graph_dead_letter_failed
    ON graph_sync_dead_letter (failed_at, event_id);

CREATE TABLE graph_maintenance_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    cursor_achievement_id BIGINT NOT NULL DEFAULT 0,
    scanned_count BIGINT NOT NULL DEFAULT 0,
    repaired_count BIGINT NOT NULL DEFAULT 0,
    difference_count BIGINT NOT NULL DEFAULT 0,
    requested_by BIGINT NOT NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    active_lock TINYINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDING', 'RUNNING', 'PAUSED') THEN 1 ELSE NULL END
    ) STORED,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_graph_maintenance_run PRIMARY KEY (id),
    CONSTRAINT fk_graph_maintenance_actor FOREIGN KEY (requested_by) REFERENCES sys_user (id),
    CONSTRAINT uk_graph_maintenance_active UNIQUE (active_lock),
    CONSTRAINT ck_graph_maintenance_type CHECK (
        run_type IN ('INITIAL_BACKFILL', 'RECONCILE', 'FULL_REBUILD')
    ),
    CONSTRAINT ck_graph_maintenance_status CHECK (
        status IN ('PENDING', 'RUNNING', 'PAUSED', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_graph_maintenance_counts CHECK (
        cursor_achievement_id >= 0
        AND scanned_count >= 0
        AND repaired_count >= 0
        AND difference_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_graph_maintenance_history
    ON graph_maintenance_run (created_at, id);
