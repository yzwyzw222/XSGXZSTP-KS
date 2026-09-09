CREATE TABLE raw_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    external_record_id VARCHAR(255) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    fetched_at TIMESTAMP(6) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    parser_version VARCHAR(64) NOT NULL,
    parse_status VARCHAR(32) NOT NULL,
    payload JSON NULL,
    payload_expires_at TIMESTAMP(6) NOT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_raw_record PRIMARY KEY (id),
    CONSTRAINT fk_raw_record_source FOREIGN KEY (source_id) REFERENCES data_source (id),
    CONSTRAINT fk_raw_record_run FOREIGN KEY (run_id) REFERENCES crawl_run (id),
    CONSTRAINT uk_raw_record_source_external UNIQUE (source_id, external_record_id),
    CONSTRAINT ck_raw_record_parse_status CHECK (parse_status IN ('PENDING', 'PARSED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_raw_record_expiry ON raw_record (payload_expires_at, id);
CREATE INDEX ix_raw_record_run_status ON raw_record (run_id, parse_status, id);
CREATE INDEX ix_raw_record_hash ON raw_record (payload_hash, id);

ALTER TABLE crawl_failure
    ADD CONSTRAINT fk_crawl_failure_raw_record
    FOREIGN KEY (raw_record_id) REFERENCES raw_record (id) ON DELETE SET NULL;

CREATE TABLE venue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    openalex_id VARCHAR(255) NULL,
    display_name VARCHAR(500) NULL,
    issn_l VARCHAR(16) NULL,
    venue_type VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_venue PRIMARY KEY (id),
    CONSTRAINT uk_venue_openalex UNIQUE (openalex_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_venue_name ON venue (display_name(191), id);

CREATE TABLE author (
    id BIGINT NOT NULL AUTO_INCREMENT,
    display_name VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_author PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_author_name ON author (display_name(191), id);

CREATE TABLE author_external_id (
    id BIGINT NOT NULL AUTO_INCREMENT,
    author_id BIGINT NOT NULL,
    id_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_author_external_id PRIMARY KEY (id),
    CONSTRAINT fk_author_external_author FOREIGN KEY (author_id) REFERENCES author (id) ON DELETE CASCADE,
    CONSTRAINT uk_author_external_type_value UNIQUE (id_type, external_id),
    CONSTRAINT uk_author_external_author_type UNIQUE (author_id, id_type),
    CONSTRAINT ck_author_external_type CHECK (id_type IN ('OPENALEX', 'ORCID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_author_external_author ON author_external_id (author_id, id_type);

CREATE TABLE organization (
    id BIGINT NOT NULL AUTO_INCREMENT,
    openalex_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(500) NULL,
    country_code CHAR(2) NULL,
    organization_type VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_organization PRIMARY KEY (id),
    CONSTRAINT uk_organization_openalex UNIQUE (openalex_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_organization_name ON organization (display_name(191), id);

CREATE TABLE topic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    openalex_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(500) NULL,
    subfield_name VARCHAR(255) NULL,
    field_name VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_topic PRIMARY KEY (id),
    CONSTRAINT uk_topic_openalex UNIQUE (openalex_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_topic_name ON topic (display_name(191), id);

CREATE TABLE achievement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title_original TEXT NULL,
    title_normalized VARCHAR(1000) NULL,
    doi_normalized VARCHAR(255) NULL,
    match_fingerprint CHAR(64) NOT NULL,
    achievement_type VARCHAR(64) NOT NULL,
    language VARCHAR(16) NULL,
    publication_date DATE NULL,
    date_precision VARCHAR(16) NULL,
    primary_venue_id BIGINT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_achievement PRIMARY KEY (id),
    CONSTRAINT fk_achievement_venue FOREIGN KEY (primary_venue_id) REFERENCES venue (id) ON DELETE SET NULL,
    CONSTRAINT uk_achievement_doi UNIQUE (doi_normalized),
    CONSTRAINT ck_achievement_date_precision CHECK (date_precision IS NULL OR date_precision IN ('DAY', 'MONTH', 'YEAR', 'UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_achievement_title ON achievement (title_normalized(191), id);
CREATE INDEX ix_achievement_year_type ON achievement ((YEAR(publication_date)), achievement_type, id);
CREATE INDEX ix_achievement_venue_date ON achievement (primary_venue_id, publication_date, id);
CREATE INDEX ix_achievement_fingerprint ON achievement (match_fingerprint, id);

CREATE TABLE paper_detail (
    achievement_id BIGINT NOT NULL,
    abstract_text MEDIUMTEXT NULL,
    authorships_may_be_incomplete BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_paper_detail PRIMARY KEY (achievement_id),
    CONSTRAINT fk_paper_detail_achievement FOREIGN KEY (achievement_id) REFERENCES achievement (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE achievement_source (
    id BIGINT NOT NULL AUTO_INCREMENT,
    achievement_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    raw_record_id BIGINT NOT NULL,
    external_record_id VARCHAR(255) NOT NULL,
    source_url VARCHAR(1000) NOT NULL,
    parser_version VARCHAR(64) NOT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_achievement_source PRIMARY KEY (id),
    CONSTRAINT fk_achievement_source_achievement FOREIGN KEY (achievement_id) REFERENCES achievement (id) ON DELETE CASCADE,
    CONSTRAINT fk_achievement_source_source FOREIGN KEY (source_id) REFERENCES data_source (id),
    CONSTRAINT fk_achievement_source_raw FOREIGN KEY (raw_record_id) REFERENCES raw_record (id),
    CONSTRAINT uk_achievement_source_external UNIQUE (source_id, external_record_id),
    CONSTRAINT uk_achievement_source_raw UNIQUE (raw_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_achievement_source_achievement ON achievement_source (achievement_id, source_id);

CREATE TABLE achievement_author (
    achievement_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    author_position INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_achievement_author PRIMARY KEY (achievement_id, author_id),
    CONSTRAINT fk_achievement_author_achievement FOREIGN KEY (achievement_id) REFERENCES achievement (id) ON DELETE CASCADE,
    CONSTRAINT fk_achievement_author_author FOREIGN KEY (author_id) REFERENCES author (id),
    CONSTRAINT uk_achievement_author_position UNIQUE (achievement_id, author_position),
    CONSTRAINT ck_achievement_author_position CHECK (author_position > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_achievement_author_author ON achievement_author (author_id, achievement_id);

CREATE TABLE authorship_organization (
    achievement_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_authorship_organization PRIMARY KEY (achievement_id, author_id, organization_id),
    CONSTRAINT fk_authorship_org_authorship FOREIGN KEY (achievement_id, author_id)
        REFERENCES achievement_author (achievement_id, author_id) ON DELETE CASCADE,
    CONSTRAINT fk_authorship_org_organization FOREIGN KEY (organization_id) REFERENCES organization (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_authorship_organization_org ON authorship_organization (organization_id, achievement_id, author_id);

CREATE TABLE achievement_topic (
    achievement_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    topic_position INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_achievement_topic PRIMARY KEY (achievement_id, topic_id),
    CONSTRAINT fk_achievement_topic_achievement FOREIGN KEY (achievement_id) REFERENCES achievement (id) ON DELETE CASCADE,
    CONSTRAINT fk_achievement_topic_topic FOREIGN KEY (topic_id) REFERENCES topic (id),
    CONSTRAINT uk_achievement_topic_position UNIQUE (achievement_id, topic_position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_achievement_topic_topic ON achievement_topic (topic_id, achievement_id);

CREATE TABLE achievement_reference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    citing_achievement_id BIGINT NOT NULL,
    referenced_external_work_id VARCHAR(255) NOT NULL,
    cited_achievement_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_achievement_reference PRIMARY KEY (id),
    CONSTRAINT fk_achievement_reference_citing FOREIGN KEY (citing_achievement_id) REFERENCES achievement (id) ON DELETE CASCADE,
    CONSTRAINT fk_achievement_reference_cited FOREIGN KEY (cited_achievement_id) REFERENCES achievement (id) ON DELETE SET NULL,
    CONSTRAINT uk_achievement_reference_external UNIQUE (citing_achievement_id, referenced_external_work_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_achievement_reference_cited ON achievement_reference (cited_achievement_id, citing_achievement_id);
