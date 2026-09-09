ALTER TABLE sys_user
    ADD COLUMN real_name VARCHAR(64) NULL,
    ADD COLUMN email VARCHAR(254) NULL,
    ADD COLUMN phone VARCHAR(32) NULL,
    ADD COLUMN organization VARCHAR(128) NULL,
    ADD COLUMN department VARCHAR(128) NULL,
    ADD COLUMN remark VARCHAR(500) NULL,
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE audit_log
    ADD COLUMN client_ip VARCHAR(64) NULL,
    ADD COLUMN user_agent VARCHAR(512) NULL;

CREATE INDEX ix_audit_log_created ON audit_log (created_at, id);
