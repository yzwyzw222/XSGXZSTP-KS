CREATE TABLE data_source (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_code VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    adapter_code VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    requests_per_second INT NOT NULL DEFAULT 1,
    max_concurrency INT NOT NULL DEFAULT 1,
    connect_timeout_seconds INT NOT NULL DEFAULT 10,
    response_timeout_seconds INT NOT NULL DEFAULT 30,
    max_retries INT NOT NULL DEFAULT 3,
    max_response_bytes INT NOT NULL DEFAULT 5242880,
    compliance_note VARCHAR(1000) NOT NULL,
    last_success_at TIMESTAMP(6) NULL,
    last_failure_at TIMESTAMP(6) NULL,
    consecutive_failures INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_data_source PRIMARY KEY (id),
    CONSTRAINT uk_data_source_code UNIQUE (source_code),
    CONSTRAINT ck_data_source_type CHECK (source_type = 'OPENALEX'),
    CONSTRAINT ck_data_source_url CHECK (base_url = 'https://api.openalex.org'),
    CONSTRAINT ck_data_source_rate CHECK (requests_per_second BETWEEN 1 AND 10),
    CONSTRAINT ck_data_source_concurrency CHECK (max_concurrency BETWEEN 1 AND 4),
    CONSTRAINT ck_data_source_connect_timeout CHECK (connect_timeout_seconds BETWEEN 1 AND 30),
    CONSTRAINT ck_data_source_response_timeout CHECK (response_timeout_seconds BETWEEN 1 AND 120),
    CONSTRAINT ck_data_source_retries CHECK (max_retries BETWEEN 0 AND 5),
    CONSTRAINT ck_data_source_response_size CHECK (max_response_bytes BETWEEN 1024 AND 20971520)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_data_source_enabled_id ON data_source (enabled, id);

CREATE TABLE crawl_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_id BIGINT NOT NULL,
    task_name VARCHAR(128) NOT NULL,
    parameter_version INT NOT NULL DEFAULT 1,
    parameters_json JSON NOT NULL,
    parameter_hash CHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_crawl_task PRIMARY KEY (id),
    CONSTRAINT fk_crawl_task_source FOREIGN KEY (source_id) REFERENCES data_source (id),
    CONSTRAINT fk_crawl_task_creator FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT uk_crawl_task_source_name UNIQUE (source_id, task_name),
    CONSTRAINT ck_crawl_task_parameter_version CHECK (parameter_version = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_crawl_task_source_enabled_id ON crawl_task (source_id, enabled, id);
CREATE INDEX ix_crawl_task_parameter_hash ON crawl_task (source_id, parameter_hash, id);

CREATE TABLE crawl_schedule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    schedule_key VARCHAR(190) NOT NULL,
    local_time TIME NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    incremental_mode VARCHAR(64) NOT NULL,
    next_fire_at TIMESTAMP(6) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_crawl_schedule PRIMARY KEY (id),
    CONSTRAINT fk_crawl_schedule_task FOREIGN KEY (task_id) REFERENCES crawl_task (id) ON DELETE CASCADE,
    CONSTRAINT uk_crawl_schedule_task UNIQUE (task_id),
    CONSTRAINT uk_crawl_schedule_key UNIQUE (schedule_key),
    CONSTRAINT ck_crawl_schedule_mode CHECK (incremental_mode = 'ROLLING_PUBLICATION_DATE_WINDOW')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_crawl_schedule_enabled_next ON crawl_schedule (enabled, next_fire_at, id);

CREATE TABLE crawl_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    run_number CHAR(36) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    control_intent VARCHAR(16) NULL,
    batch_job_execution_id BIGINT NULL,
    parent_run_id BIGINT NULL,
    requested_by BIGINT NULL,
    parameter_hash CHAR(64) NOT NULL,
    range_start DATE NULL,
    range_end DATE NULL,
    read_count BIGINT NOT NULL DEFAULT 0,
    parsed_count BIGINT NOT NULL DEFAULT 0,
    created_count BIGINT NOT NULL DEFAULT 0,
    updated_count BIGINT NOT NULL DEFAULT 0,
    duplicate_count BIGINT NOT NULL DEFAULT 0,
    failure_count BIGINT NOT NULL DEFAULT 0,
    request_count BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMP(6) NULL,
    finished_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_crawl_run PRIMARY KEY (id),
    CONSTRAINT fk_crawl_run_task FOREIGN KEY (task_id) REFERENCES crawl_task (id),
    CONSTRAINT fk_crawl_run_parent FOREIGN KEY (parent_run_id) REFERENCES crawl_run (id),
    CONSTRAINT fk_crawl_run_requester FOREIGN KEY (requested_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT uk_crawl_run_number UNIQUE (run_number),
    CONSTRAINT uk_crawl_run_batch_execution UNIQUE (batch_job_execution_id),
    CONSTRAINT ck_crawl_run_trigger CHECK (trigger_type IN ('MANUAL', 'SCHEDULED', 'RESUME', 'RETRY_FAILURES')),
    CONSTRAINT ck_crawl_run_status CHECK (status IN ('PENDING', 'RUNNING', 'PAUSING', 'PAUSED', 'SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLING', 'CANCELLED')),
    CONSTRAINT ck_crawl_run_intent CHECK (control_intent IS NULL OR control_intent IN ('PAUSE', 'CANCEL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_crawl_run_task_created ON crawl_run (task_id, created_at, id);
CREATE INDEX ix_crawl_run_source_conflict ON crawl_run (status, task_id, range_start, range_end);
CREATE INDEX ix_crawl_run_status_updated ON crawl_run (status, updated_at, id);

CREATE TABLE crawl_checkpoint (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    partition_key VARCHAR(128) NOT NULL DEFAULT 'default',
    cursor_value TEXT NOT NULL,
    cursor_hash CHAR(64) NOT NULL,
    committed_pages INT NOT NULL DEFAULT 0,
    committed_records BIGINT NOT NULL DEFAULT 0,
    committed_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crawl_checkpoint PRIMARY KEY (id),
    CONSTRAINT fk_crawl_checkpoint_run FOREIGN KEY (run_id) REFERENCES crawl_run (id) ON DELETE CASCADE,
    CONSTRAINT uk_crawl_checkpoint_partition UNIQUE (run_id, partition_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_crawl_checkpoint_committed ON crawl_checkpoint (committed_at, run_id);

CREATE TABLE crawl_failure (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    raw_record_id BIGINT NULL,
    external_record_id VARCHAR(255) NULL,
    failure_stage VARCHAR(32) NOT NULL,
    error_category VARCHAR(64) NOT NULL,
    safe_message VARCHAR(1000) NOT NULL,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INT NOT NULL DEFAULT 0,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    evidence_hash CHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_crawl_failure PRIMARY KEY (id),
    CONSTRAINT fk_crawl_failure_run FOREIGN KEY (run_id) REFERENCES crawl_run (id) ON DELETE CASCADE,
    CONSTRAINT ck_crawl_failure_stage CHECK (failure_stage IN ('FETCH', 'PARSE', 'VALIDATE', 'NORMALIZE', 'PERSIST', 'SYSTEM')),
    CONSTRAINT ck_crawl_failure_attempt CHECK (attempt_count BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_crawl_failure_run_resolved ON crawl_failure (run_id, resolved, id);
CREATE INDEX ix_crawl_failure_retry ON crawl_failure (retryable, resolved, attempt_count, id);
