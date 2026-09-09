CREATE TABLE IF NOT EXISTS authors (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    semantic_scholar_id VARCHAR(64) UNIQUE,
    name            VARCHAR(255) NOT NULL,
    affiliation     VARCHAR(512),
    h_index         INT DEFAULT 0,
    paper_count     INT DEFAULT 0,
    citation_count  INT DEFAULT 0,
    url             VARCHAR(512),
    version         INT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_authors_name (name),
    INDEX idx_authors_ss_id (semantic_scholar_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS venues (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(512) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    version         INT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_venues_name (name(255)),
    INDEX idx_venues_name (name(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS papers (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    semantic_scholar_id VARCHAR(64) UNIQUE,
    title           VARCHAR(1024) NOT NULL,
    abstract_text   TEXT,
    year            INT,
    doi             VARCHAR(255),
    citation_count  INT DEFAULT 0,
    reference_count INT DEFAULT 0,
    venue_id        BIGINT,
    extraction_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    url             VARCHAR(512),
    version         INT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_papers_title (title(255)),
    INDEX idx_papers_year (year),
    INDEX idx_papers_status (extraction_status),
    INDEX idx_papers_doi (doi),
    CONSTRAINT fk_papers_venue FOREIGN KEY (venue_id) REFERENCES venues(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS paper_authors (
    paper_id        BIGINT NOT NULL,
    author_id       BIGINT NOT NULL,
    author_position INT,
    PRIMARY KEY (paper_id, author_id),
    CONSTRAINT fk_pa_paper FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
    CONSTRAINT fk_pa_author FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS paper_citations (
    citing_paper_id BIGINT NOT NULL,
    cited_paper_id  BIGINT NOT NULL,
    PRIMARY KEY (citing_paper_id, cited_paper_id),
    CONSTRAINT fk_pc_citing FOREIGN KEY (citing_paper_id) REFERENCES papers(id) ON DELETE CASCADE,
    CONSTRAINT fk_pc_cited FOREIGN KEY (cited_paper_id) REFERENCES papers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS research_topics (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    version         INT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_topics_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS paper_topics (
    paper_id        BIGINT NOT NULL,
    topic_id        BIGINT NOT NULL,
    confidence      DOUBLE,
    PRIMARY KEY (paper_id, topic_id),
    CONSTRAINT fk_pt_paper FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
    CONSTRAINT fk_pt_topic FOREIGN KEY (topic_id) REFERENCES research_topics(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS extracted_entities (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id        BIGINT NOT NULL,
    entity_name     VARCHAR(512) NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    properties      JSON,
    version         INT NOT NULL DEFAULT 0,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ee_paper FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
    INDEX idx_ee_type (entity_type),
    INDEX idx_ee_name (entity_name(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS entity_relationships (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id        BIGINT NOT NULL,
    source_entity_id BIGINT NOT NULL,
    target_entity_id BIGINT NOT NULL,
    relationship_type VARCHAR(64) NOT NULL,
    evidence_text   TEXT,
    confidence      DOUBLE,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_er_paper FOREIGN KEY (paper_id) REFERENCES papers(id) ON DELETE CASCADE,
    CONSTRAINT fk_er_source FOREIGN KEY (source_entity_id) REFERENCES extracted_entities(id) ON DELETE CASCADE,
    CONSTRAINT fk_er_target FOREIGN KEY (target_entity_id) REFERENCES extracted_entities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS outbox_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(64) NOT NULL,
    aggregate_id    BIGINT NOT NULL,
    aggregate_type  VARCHAR(64) NOT NULL,
    payload         JSON NOT NULL,
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at    DATETIME(6),
    INDEX idx_outbox_pending (processed, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
