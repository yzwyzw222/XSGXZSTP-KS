CREATE TABLE organization_name_evidence (
    organization_id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    name_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    first_observed_at TIMESTAMP(6) NOT NULL,
    last_observed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_organization_name_evidence PRIMARY KEY (organization_id, source_id, name_hash),
    CONSTRAINT fk_organization_name_entity FOREIGN KEY (organization_id) REFERENCES organization (id),
    CONSTRAINT fk_organization_name_source FOREIGN KEY (source_id) REFERENCES data_source (id),
    CONSTRAINT ck_organization_name_time CHECK (first_observed_at <= last_observed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
