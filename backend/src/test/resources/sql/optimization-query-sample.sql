CREATE TABLE optimization_numbers (n INT NOT NULL PRIMARY KEY);
INSERT INTO optimization_numbers
WITH digit AS (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)
SELECT 1 + a.d + 10*b.d + 100*c.d + 1000*d.d + 10000*e.d FROM digit a, digit b, digit c, digit d, digit e;

INSERT INTO sys_user (id, username, password_hash, status) VALUES (1, 'query-profile', 'disabled-test-account', 'DISABLED');
INSERT INTO data_source (id, source_code, source_type, base_url, adapter_code, compliance_note)
VALUES (1, 'OPENALEX', 'OPENALEX', 'https://api.openalex.org', 'OPENALEX_REST_V1', '隔离合成查询数据，不请求来源');
INSERT INTO crawl_task (id, source_id, task_name, parameter_version, parameters_json, parameter_hash, created_by)
VALUES (1, 1, 'query-profile', 1, JSON_OBJECT(), REPEAT('1', 64), 1);
INSERT INTO crawl_run (id, task_id, run_number, trigger_type, status, requested_by, parameter_hash)
VALUES (1, 1, '00000000-0000-4000-8000-000000000111', 'MANUAL', 'SUCCEEDED', 1, REPEAT('1', 64));
INSERT INTO venue (id, display_name) SELECT n, CONCAT('Journal ', n, '#') FROM optimization_numbers WHERE n <= 10;
INSERT INTO author (id, display_name) SELECT n, CONCAT('Author ', n, '#') FROM optimization_numbers WHERE n <= 1000;
INSERT INTO organization (id, openalex_id, display_name)
SELECT n, CONCAT('I', n), CONCAT('Institute ', n, '#') FROM optimization_numbers WHERE n <= 100;
INSERT INTO organization_name_evidence (organization_id, source_id, name_hash, display_name, first_observed_at, last_observed_at)
SELECT id, 1, SHA2(CONCAT('Former Institute ', id, '#'), 256), CONCAT('Former Institute ', id, '#'), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM organization;
INSERT INTO subject (id, source_id, external_id, display_name)
SELECT n, 1, CONCAT('T', n), CONCAT('Topic ', n, '#') FROM optimization_numbers WHERE n <= 100;
INSERT INTO achievement (id, title_original, title_normalized, doi_normalized, match_fingerprint, achievement_type,
    publication_date, date_precision, primary_venue_id, first_seen_at, last_seen_at)
SELECT n, CONCAT('Study ', n), CONCAT('Study ', n), CONCAT('10.9999/query-', n), SHA2(CONCAT('work-', n), 256),
    IF(MOD(n, 2) = 1, 'article', 'dataset'), MAKEDATE(2015 + MOD(n, 10), 1), 'YEAR', 1 + MOD(n, 10), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM optimization_numbers;
INSERT INTO paper_detail (achievement_id, abstract_text, authorships_may_be_incomplete)
SELECT n, IF(MOD(n, 3) = 0, NULL, '用于数据库查询验证的合成摘要'), MOD(n, 20) = 0 FROM optimization_numbers;
INSERT INTO achievement_author (achievement_id, author_id, author_position)
SELECT n, 1 + MOD(n, 1000), 1 FROM optimization_numbers
UNION ALL SELECT n, 1 + MOD(n + 1, 1000), 2 FROM optimization_numbers;
INSERT INTO authorship_organization (achievement_id, author_id, organization_id)
SELECT n, 1 + MOD(n, 1000), 1 + MOD(n, 100) FROM optimization_numbers
UNION ALL SELECT n, 1 + MOD(n + 1, 1000), 1 + MOD(n + 1, 100) FROM optimization_numbers;
INSERT INTO achievement_subject (achievement_id, subject_id, subject_position)
SELECT n, 1 + MOD(n, 100), 1 FROM optimization_numbers;
INSERT INTO raw_record (id, source_id, run_id, external_record_id, source_url, fetched_at, payload_hash,
    parser_version, parse_status, payload_expires_at, first_seen_at, last_seen_at)
SELECT n, 1, 1, CONCAT('W', n), CONCAT('https://openalex.org/W', n), UTC_TIMESTAMP(6), SHA2(CONCAT('raw-', n), 256),
    'query-profile', 'PARSED', DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 90 DAY), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM optimization_numbers;
INSERT INTO achievement_source (id, achievement_id, source_id, raw_record_id, external_record_id,
    source_url, parser_version, first_seen_at, last_seen_at)
SELECT n, n, 1, n, CONCAT('W', n), CONCAT('https://openalex.org/W', n), 'query-profile', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM optimization_numbers;
INSERT INTO achievement_source_snapshot (achievement_source_id, normalized_payload, source_priority, observed_at)
SELECT n, JSON_OBJECT('scholarlyMetadata', JSON_OBJECT('observedAt', '2026-09-05T00:00:00Z',
    'citedByCount', MOD(n, 500), 'openAccess', CAST('true' AS JSON), 'retracted', CAST('false' AS JSON), 'versionRelations', JSON_ARRAY())),
    10, UTC_TIMESTAMP(6) FROM optimization_numbers WHERE MOD(n, 2) = 0;
