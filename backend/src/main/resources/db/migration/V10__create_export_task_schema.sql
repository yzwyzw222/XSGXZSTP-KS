CREATE TABLE export_task (
    id CHAR(36) NOT NULL,
    format VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    filters_json JSON NOT NULL,
    requested_by BIGINT NOT NULL,
    requested_count BIGINT NOT NULL,
    exported_count BIGINT NOT NULL DEFAULT 0,
    download_token VARCHAR(128) NOT NULL,
    file_name VARCHAR(128) NULL,
    file_relative_path VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    expires_at TIMESTAMP(6) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_export_task PRIMARY KEY (id),
    CONSTRAINT fk_export_task_requester FOREIGN KEY (requested_by) REFERENCES sys_user (id),
    CONSTRAINT ck_export_task_format CHECK (format IN ('CSV', 'JSON')),
    CONSTRAINT ck_export_task_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_export_task_token CHECK (CHAR_LENGTH(download_token) BETWEEN 32 AND 128),
    CONSTRAINT ck_export_task_counts CHECK (
        requested_count BETWEEN 0 AND 10000
        AND exported_count BETWEEN 0 AND requested_count
    ),
    CONSTRAINT ck_export_task_file_state CHECK (
        (status = 'SUCCEEDED' AND file_name IS NOT NULL AND file_relative_path IS NOT NULL
            AND completed_at IS NOT NULL AND expires_at IS NOT NULL AND error_code IS NULL AND error_message IS NULL)
        OR status <> 'SUCCEEDED'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_export_task_owner_status_created
    ON export_task (requested_by, status, created_at, id);
CREATE INDEX ix_export_task_status_created ON export_task (status, created_at, id);
CREATE INDEX ix_export_task_expiry ON export_task (expires_at, status, id);
