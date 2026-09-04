ALTER TABLE data_source
    DROP CHECK ck_data_source_type,
    DROP CHECK ck_data_source_url,
    ADD CONSTRAINT ck_data_source_type CHECK (source_type IN ('OPENALEX', 'CROSSREF')),
    ADD CONSTRAINT ck_data_source_identity CHECK (
        (source_type = 'OPENALEX'
            AND source_code = 'OPENALEX'
            AND base_url = 'https://api.openalex.org'
            AND adapter_code = 'OPENALEX_REST_V1')
        OR
        (source_type = 'CROSSREF'
            AND source_code = 'CROSSREF'
            AND base_url = 'https://api.crossref.org'
            AND adapter_code = 'CROSSREF_REST_V1')
    );

ALTER TABLE crawl_task
    DROP CHECK ck_crawl_task_parameter_version,
    ADD CONSTRAINT ck_crawl_task_parameter_version CHECK (parameter_version IN (1, 2));

ALTER TABLE crawl_schedule
    DROP CHECK ck_crawl_schedule_mode,
    ADD CONSTRAINT ck_crawl_schedule_mode CHECK (
        incremental_mode IN ('ROLLING_PUBLICATION_DATE_WINDOW', 'CLOSED_INDEX_DATE_WINDOW')
    );

ALTER TABLE achievement
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER last_seen_at,
    ADD COLUMN fingerprint_version INT NOT NULL DEFAULT 1 AFTER match_fingerprint,
    ADD CONSTRAINT ck_achievement_fingerprint_version CHECK (fingerprint_version > 0);

ALTER TABLE author
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER display_name;

ALTER TABLE organization
    MODIFY COLUMN openalex_id VARCHAR(255) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER organization_type;

ALTER TABLE venue
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER venue_type;

ALTER TABLE author_external_id
    DROP CHECK ck_author_external_type,
    ADD CONSTRAINT ck_author_external_type CHECK (id_type IN ('OPENALEX', 'ORCID', 'CROSSREF'));

CREATE TABLE organization_external_id (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    id_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_organization_external_id PRIMARY KEY (id),
    CONSTRAINT fk_organization_external_org FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE CASCADE,
    CONSTRAINT uk_organization_external_type_value UNIQUE (id_type, external_id),
    CONSTRAINT uk_organization_external_org_type UNIQUE (organization_id, id_type),
    CONSTRAINT ck_organization_external_type CHECK (id_type IN ('OPENALEX', 'ROR', 'CROSSREF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO organization_external_id (organization_id, id_type, external_id)
SELECT id, 'OPENALEX', openalex_id
FROM organization
WHERE openalex_id IS NOT NULL;

CREATE TABLE venue_external_id (
    id BIGINT NOT NULL AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    id_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_venue_external_id PRIMARY KEY (id),
    CONSTRAINT fk_venue_external_venue FOREIGN KEY (venue_id) REFERENCES venue (id) ON DELETE CASCADE,
    CONSTRAINT uk_venue_external_type_value UNIQUE (id_type, external_id),
    CONSTRAINT uk_venue_external_venue_type UNIQUE (venue_id, id_type),
    CONSTRAINT ck_venue_external_type CHECK (id_type IN ('OPENALEX', 'ISSN', 'CROSSREF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO venue_external_id (venue_id, id_type, external_id)
SELECT id, 'OPENALEX', openalex_id
FROM venue
WHERE openalex_id IS NOT NULL;

INSERT IGNORE INTO venue_external_id (venue_id, id_type, external_id)
SELECT id, 'ISSN', UPPER(issn_l)
FROM venue
WHERE issn_l IS NOT NULL;

CREATE TABLE subject (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_id BIGINT NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    subject_path VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_subject PRIMARY KEY (id),
    CONSTRAINT fk_subject_source FOREIGN KEY (source_id) REFERENCES data_source (id),
    CONSTRAINT uk_subject_source_external UNIQUE (source_id, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_subject_name ON subject (display_name(191), id);

CREATE TABLE achievement_subject (
    achievement_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    subject_position INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_achievement_subject PRIMARY KEY (achievement_id, subject_id),
    CONSTRAINT fk_achievement_subject_achievement FOREIGN KEY (achievement_id)
        REFERENCES achievement (id) ON DELETE CASCADE,
    CONSTRAINT fk_achievement_subject_subject FOREIGN KEY (subject_id) REFERENCES subject (id),
    CONSTRAINT uk_achievement_subject_position UNIQUE (achievement_id, subject_id, subject_position),
    CONSTRAINT ck_achievement_subject_position CHECK (subject_position > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO subject (source_id, external_id, display_name, subject_path)
SELECT ds.id,
       t.openalex_id,
       COALESCE(t.display_name, t.openalex_id),
       CONCAT_WS(' > ', t.field_name, t.subfield_name, t.display_name)
FROM topic t
JOIN data_source ds ON ds.source_type = 'OPENALEX';

INSERT INTO achievement_subject (achievement_id, subject_id, subject_position)
SELECT at.achievement_id, s.id, at.topic_position
FROM achievement_topic at
JOIN topic t ON t.id = at.topic_id
JOIN data_source ds ON ds.source_type = 'OPENALEX'
JOIN subject s ON s.source_id = ds.id AND s.external_id = t.openalex_id;

ALTER TABLE achievement_reference
    ADD COLUMN referenced_id_type VARCHAR(32) NOT NULL DEFAULT 'OPENALEX'
        AFTER referenced_external_work_id,
    ADD COLUMN referenced_id_value VARCHAR(255) NULL AFTER referenced_id_type;

UPDATE achievement_reference
SET referenced_id_value = referenced_external_work_id;

ALTER TABLE achievement_reference
    MODIFY COLUMN referenced_id_value VARCHAR(255) NOT NULL,
    ADD CONSTRAINT ck_achievement_reference_id_type CHECK (referenced_id_type IN ('OPENALEX', 'DOI')),
    ADD CONSTRAINT uk_achievement_reference_typed
        UNIQUE (citing_achievement_id, referenced_id_type, referenced_id_value);

CREATE TABLE achievement_source_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    achievement_source_id BIGINT NOT NULL,
    normalized_payload JSON NOT NULL,
    source_priority INT NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_achievement_source_snapshot PRIMARY KEY (id),
    CONSTRAINT fk_achievement_snapshot_source FOREIGN KEY (achievement_source_id)
        REFERENCES achievement_source (id) ON DELETE CASCADE,
    CONSTRAINT uk_achievement_snapshot_source UNIQUE (achievement_source_id),
    CONSTRAINT ck_achievement_snapshot_priority CHECK (source_priority BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE entity_field_provenance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    source_id BIGINT NULL,
    raw_record_id BIGINT NULL,
    source_priority INT NOT NULL,
    field_value JSON NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    observed_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_entity_field_provenance PRIMARY KEY (id),
    CONSTRAINT fk_field_provenance_source FOREIGN KEY (source_id) REFERENCES data_source (id),
    CONSTRAINT fk_field_provenance_raw FOREIGN KEY (raw_record_id) REFERENCES raw_record (id) ON DELETE SET NULL,
    CONSTRAINT uk_field_provenance_observation
        UNIQUE (entity_type, entity_id, field_name, source_id, raw_record_id),
    CONSTRAINT ck_field_provenance_entity CHECK (
        entity_type IN ('ACHIEVEMENT', 'AUTHOR', 'ORGANIZATION', 'VENUE')
    ),
    CONSTRAINT ck_field_provenance_priority CHECK (source_priority BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_field_provenance_selected
    ON entity_field_provenance (entity_type, entity_id, field_name, selected, source_priority, id);

CREATE TABLE duplicate_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    entity_type VARCHAR(32) NOT NULL,
    left_entity_id BIGINT NOT NULL,
    right_entity_id BIGINT NOT NULL,
    match_basis VARCHAR(32) NOT NULL,
    source_id BIGINT NULL,
    rule_version INT NOT NULL DEFAULT 1,
    evidence_json JSON NOT NULL,
    evidence_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_duplicate_candidate PRIMARY KEY (id),
    CONSTRAINT fk_duplicate_candidate_source FOREIGN KEY (source_id) REFERENCES data_source (id),
    CONSTRAINT uk_duplicate_candidate_pair
        UNIQUE (entity_type, left_entity_id, right_entity_id, rule_version),
    CONSTRAINT ck_duplicate_candidate_entity CHECK (
        entity_type IN ('ACHIEVEMENT', 'AUTHOR', 'ORGANIZATION', 'VENUE')
    ),
    CONSTRAINT ck_duplicate_candidate_order CHECK (left_entity_id < right_entity_id),
    CONSTRAINT ck_duplicate_candidate_rule_version CHECK (rule_version > 0),
    CONSTRAINT ck_duplicate_candidate_basis CHECK (
        match_basis IN ('FINGERPRINT', 'TEXT_NAME', 'SOURCE_POSITION')
    ),
    CONSTRAINT ck_duplicate_candidate_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_duplicate_candidate_queue ON duplicate_candidate (status, entity_type, id);
CREATE INDEX ix_duplicate_candidate_source_rule
    ON duplicate_candidate (source_id, rule_version, created_at, id);

CREATE TABLE data_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    revision_action VARCHAR(32) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    actor_user_id BIGINT NULL,
    reason VARCHAR(1000) NOT NULL,
    reversible BOOLEAN NOT NULL DEFAULT TRUE,
    reverted_by_revision_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_data_revision PRIMARY KEY (id),
    CONSTRAINT fk_data_revision_actor FOREIGN KEY (actor_user_id)
        REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_data_revision_reverted_by FOREIGN KEY (reverted_by_revision_id)
        REFERENCES data_revision (id) ON DELETE SET NULL,
    CONSTRAINT ck_data_revision_action CHECK (
        revision_action IN ('MERGE', 'REJECT', 'REVERT', 'FIELD_OVERRIDE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_data_revision_entity ON data_revision (entity_type, entity_id, created_at, id);

CREATE TABLE manual_field_override (
    id BIGINT NOT NULL AUTO_INCREMENT,
    achievement_id BIGINT NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    field_value JSON NOT NULL,
    revision_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_manual_field_override PRIMARY KEY (id),
    CONSTRAINT fk_manual_override_achievement FOREIGN KEY (achievement_id)
        REFERENCES achievement (id) ON DELETE CASCADE,
    CONSTRAINT fk_manual_override_revision FOREIGN KEY (revision_id) REFERENCES data_revision (id),
    CONSTRAINT fk_manual_override_actor FOREIGN KEY (actor_user_id) REFERENCES sys_user (id),
    CONSTRAINT uk_manual_override_field UNIQUE (achievement_id, field_name),
    CONSTRAINT ck_manual_override_field CHECK (
        field_name IN ('title', 'type', 'language', 'publicationDate', 'venueId')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_manual_override_active ON manual_field_override (active, achievement_id, field_name);

CREATE TABLE merge_decision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT NOT NULL,
    decision VARCHAR(16) NOT NULL,
    canonical_entity_id BIGINT NULL,
    revision_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    decided_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_merge_decision PRIMARY KEY (id),
    CONSTRAINT fk_merge_decision_candidate FOREIGN KEY (candidate_id)
        REFERENCES duplicate_candidate (id),
    CONSTRAINT fk_merge_decision_revision FOREIGN KEY (revision_id) REFERENCES data_revision (id),
    CONSTRAINT fk_merge_decision_actor FOREIGN KEY (actor_user_id) REFERENCES sys_user (id),
    CONSTRAINT uk_merge_decision_candidate_revision UNIQUE (candidate_id, revision_id),
    CONSTRAINT ck_merge_decision_value CHECK (decision IN ('ACCEPT', 'REJECT', 'REVERT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_merge_decision_candidate ON merge_decision (candidate_id, decided_at, id);

CREATE TABLE canonical_entity_link (
    id BIGINT NOT NULL AUTO_INCREMENT,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    canonical_entity_id BIGINT NOT NULL,
    revision_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_canonical_entity_link PRIMARY KEY (id),
    CONSTRAINT fk_canonical_link_revision FOREIGN KEY (revision_id)
        REFERENCES data_revision (id) ON DELETE SET NULL,
    CONSTRAINT uk_canonical_entity_link UNIQUE (entity_type, entity_id),
    CONSTRAINT ck_canonical_entity_link_type CHECK (
        entity_type IN ('ACHIEVEMENT', 'AUTHOR', 'ORGANIZATION', 'VENUE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_canonical_entity_target
    ON canonical_entity_link (entity_type, canonical_entity_id, entity_id);

CREATE TABLE quality_metric_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    numerator BIGINT NOT NULL DEFAULT 0,
    denominator BIGINT NOT NULL DEFAULT 0,
    metric_value DECIMAL(20, 6) NULL,
    measured_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_quality_metric_snapshot PRIMARY KEY (id),
    CONSTRAINT fk_quality_metric_source FOREIGN KEY (source_id) REFERENCES data_source (id),
    CONSTRAINT fk_quality_metric_task FOREIGN KEY (task_id) REFERENCES crawl_task (id),
    CONSTRAINT fk_quality_metric_run FOREIGN KEY (run_id) REFERENCES crawl_run (id) ON DELETE CASCADE,
    CONSTRAINT uk_quality_metric_run_code UNIQUE (run_id, metric_code),
    CONSTRAINT ck_quality_metric_counts CHECK (numerator >= 0 AND denominator >= 0),
    CONSTRAINT ck_quality_metric_code CHECK (metric_code IN (
        'TOTAL_RECORDS', 'VALID_RECORDS', 'MISSING_OR_INVALID_DOI',
        'MISSING_TITLE', 'MISSING_DATE', 'MISSING_AUTHORS',
        'AUTHORS_WITHOUT_STABLE_ID', 'ORGANIZATIONS_WITHOUT_ROR',
        'FIELD_CONFLICTS', 'AUTO_MATCHES', 'NEW_CANDIDATES'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_quality_metric_source_run
    ON quality_metric_snapshot (source_id, run_id, metric_code, id);

CREATE TABLE quality_issue_sample (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    raw_record_id BIGINT NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    external_record_id VARCHAR(255) NOT NULL,
    evidence_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_quality_issue_sample PRIMARY KEY (id),
    CONSTRAINT fk_quality_issue_source FOREIGN KEY (source_id) REFERENCES data_source (id),
    CONSTRAINT fk_quality_issue_run FOREIGN KEY (run_id) REFERENCES crawl_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_quality_issue_raw FOREIGN KEY (raw_record_id) REFERENCES raw_record (id) ON DELETE CASCADE,
    CONSTRAINT uk_quality_issue_run_raw_code UNIQUE (run_id, raw_record_id, metric_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_quality_issue_lookup ON quality_issue_sample (run_id, metric_code, id);
