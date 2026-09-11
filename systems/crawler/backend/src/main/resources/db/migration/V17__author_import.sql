-- 保留历史采集、治理表及数据；文件导入拥有独立来源证据，不伪造采集运行。
CREATE TABLE author_import_lock (
    id INT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO author_import_lock (id) VALUES (1);

CREATE TABLE author_import_person (
    identity_key CHAR(64) NOT NULL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    CONSTRAINT fk_import_person_author FOREIGN KEY (author_id) REFERENCES author (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE author_import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_key CHAR(64) NOT NULL,
    author_id BIGINT NOT NULL,
    file_name VARCHAR(240) NOT NULL,
    sheet_name VARCHAR(200) NOT NULL,
    import_mode VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL,
    imported_count INT NOT NULL DEFAULT 0,
    linked_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_import_batch_request UNIQUE (request_key),
    CONSTRAINT fk_import_batch_author FOREIGN KEY (author_id) REFERENCES author (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE author_import_work (
    identity_key CHAR(64) NOT NULL PRIMARY KEY,
    achievement_id BIGINT NOT NULL,
    CONSTRAINT fk_import_work_achievement FOREIGN KEY (achievement_id) REFERENCES achievement (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE author_import_record (
    batch_id BIGINT NOT NULL,
    source_row INT NOT NULL,
    achievement_id BIGINT NOT NULL,
    original_columns JSON NOT NULL,
    PRIMARY KEY (batch_id, source_row),
    CONSTRAINT fk_import_record_batch FOREIGN KEY (batch_id) REFERENCES author_import_batch (id),
    CONSTRAINT fk_import_record_achievement FOREIGN KEY (achievement_id) REFERENCES achievement (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE INDEX ix_import_record_achievement ON author_import_record (achievement_id, batch_id);

CREATE TABLE achievement_advisor (
    achievement_id BIGINT NOT NULL,
    advisor_id BIGINT NOT NULL,
    PRIMARY KEY (achievement_id, advisor_id),
    CONSTRAINT fk_achievement_advisor_work FOREIGN KEY (achievement_id) REFERENCES achievement (id),
    CONSTRAINT fk_achievement_advisor_author FOREIGN KEY (advisor_id) REFERENCES author (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 来源表的机构列表不一定能逐一对应作者，因此独立保存成果所属机构。
CREATE TABLE achievement_institution (
    achievement_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    PRIMARY KEY (achievement_id, organization_id),
    CONSTRAINT fk_achievement_institution_work FOREIGN KEY (achievement_id) REFERENCES achievement (id),
    CONSTRAINT fk_achievement_institution_org FOREIGN KEY (organization_id) REFERENCES organization (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE author_import_affiliation (
    achievement_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    PRIMARY KEY (achievement_id, author_id, organization_id),
    CONSTRAINT fk_import_affiliation_work FOREIGN KEY (achievement_id) REFERENCES achievement (id),
    CONSTRAINT fk_import_affiliation_author FOREIGN KEY (author_id) REFERENCES author (id),
    CONSTRAINT fk_import_affiliation_org FOREIGN KEY (organization_id) REFERENCES organization (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 文件关键词没有远程数据源；仍使用现有主题与成果关系供检索和图投影读取。
ALTER TABLE subject MODIFY COLUMN source_id BIGINT NULL;

INSERT INTO graph_type_definition (kind, code, display_name, color, size) VALUES
('RELATIONSHIP', 'SUPERVISED', '指导', '#d9a45c', 2),
('RELATIONSHIP', 'PRODUCED_AT', '所属机构', '#7690a8', 2);
