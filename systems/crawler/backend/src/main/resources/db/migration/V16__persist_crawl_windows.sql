-- 每次运行的窗口快照用于增量推进与检查点重试，不改写已有任务或历史数据。
CREATE TABLE crawl_run_window (
    run_id BIGINT NOT NULL,
    mode VARCHAR(64) NOT NULL,
    window_start DATETIME(6) NOT NULL,
    window_end DATETIME(6) NOT NULL,
    parameters_json JSON NOT NULL,
    CONSTRAINT pk_crawl_run_window PRIMARY KEY (run_id),
    CONSTRAINT fk_crawl_window_run FOREIGN KEY (run_id) REFERENCES crawl_run (id),
    CONSTRAINT ck_crawl_window_range CHECK (window_start < window_end),
    CONSTRAINT ck_crawl_window_mode CHECK (mode IN ('ROLLING_PUBLICATION_DATE_WINDOW', 'CLOSED_INDEX_DATE_WINDOW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
