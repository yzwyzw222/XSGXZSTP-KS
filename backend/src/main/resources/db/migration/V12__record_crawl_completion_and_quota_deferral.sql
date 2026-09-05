ALTER TABLE crawl_run
    ADD COLUMN completion_reason VARCHAR(32) NULL,
    ADD COLUMN deferred_until DATETIME(6) NULL,
    ADD COLUMN quota_deferrals INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_crawl_run_quota_deferrals CHECK (quota_deferrals BETWEEN 0 AND 3),
    ADD CONSTRAINT ck_crawl_run_completion_reason CHECK (completion_reason IN (
        'SOURCE_EXHAUSTED', 'PAGE_LIMIT', 'RECORD_LIMIT', 'RETRY_BATCH_COMPLETED',
        'QUOTA_EXHAUSTED', 'QUOTA_RETRY_LIMIT', 'USER_PAUSED', 'USER_CANCELLED', 'BATCH_FAILED'
    )),
    ADD INDEX idx_crawl_run_deferred (status, deferred_until, id);

ALTER TABLE crawl_schedule
    DROP CHECK ck_crawl_schedule_mode,
    ADD CONSTRAINT ck_crawl_schedule_mode CHECK (incremental_mode IN (
        'ROLLING_PUBLICATION_DATE_WINDOW', 'CLOSED_INDEX_DATE_WINDOW', 'FIXED_SCOPE_REFRESH'
    ));

-- 既有每日任务实际重复固定范围；更正模式名称，不改任务范围和触发时间。
UPDATE crawl_schedule SET incremental_mode = 'FIXED_SCOPE_REFRESH';
