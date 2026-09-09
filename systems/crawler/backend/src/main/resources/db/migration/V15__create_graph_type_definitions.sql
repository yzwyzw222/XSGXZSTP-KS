CREATE TABLE graph_type_definition (
    kind VARCHAR(16) NOT NULL,
    code VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    color CHAR(7) NOT NULL,
    size INT NOT NULL,
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (kind, code),
    CONSTRAINT ck_graph_type_kind CHECK (kind IN ('NODE', 'RELATIONSHIP')),
    CONSTRAINT ck_graph_type_size CHECK (
        (kind = 'NODE' AND size BETWEEN 16 AND 96)
        OR (kind = 'RELATIONSHIP' AND size BETWEEN 1 AND 8)),
    CONSTRAINT ck_graph_type_review CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_graph_type_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 默认配置尚未经人工审核，不将初始化误标为已审核。
INSERT INTO graph_type_definition (kind, code, display_name, color, size) VALUES
('NODE', 'ACHIEVEMENT', '作品', '#2363b8', 40),
('NODE', 'AUTHOR', '作者', '#258ca3', 30),
('NODE', 'INSTITUTION', '机构', '#ba7b35', 30),
('NODE', 'VENUE', '期刊/载体', '#8365b6', 30),
('NODE', 'TOPIC', '主题', '#278566', 30),
('RELATIONSHIP', 'AUTHORED', '创作', '#7690a8', 2),
('RELATIONSHIP', 'AFFILIATED_WITH', '隶属', '#7690a8', 2),
('RELATIONSHIP', 'PUBLISHED_IN', '发表于', '#7690a8', 2),
('RELATIONSHIP', 'HAS_TOPIC', '主题', '#7690a8', 2),
('RELATIONSHIP', 'CITES', '引用', '#7690a8', 2),
('RELATIONSHIP', 'COAUTHORED', '合作', '#258ca3', 2);
