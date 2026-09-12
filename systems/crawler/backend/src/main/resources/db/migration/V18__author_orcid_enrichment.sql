-- 每位作者仅保留一个可恢复任务，正式编号仍由既有唯一约束保护。
CREATE TABLE author_orcid_task (
    author_id BIGINT NOT NULL PRIMARY KEY,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    auto_apply BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    candidates JSON NOT NULL,
    message VARCHAR(500) NOT NULL DEFAULT '',
    checked_at TIMESTAMP(6) NULL,
    next_attempt_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_token CHAR(36) NULL,
    lease_until TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_orcid_task_author FOREIGN KEY (author_id) REFERENCES author(id) ON DELETE CASCADE,
    CONSTRAINT ck_orcid_task_status CHECK (status IN ('PENDING','RUNNING','MATCHED','REVIEW','NOT_FOUND','RETRY','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_orcid_task_due ON author_orcid_task(status, next_attempt_at, lease_until);
