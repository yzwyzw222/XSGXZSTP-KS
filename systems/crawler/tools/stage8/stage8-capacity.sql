CREATE TABLE stage8_sequence (
    n INT NOT NULL,
    CONSTRAINT pk_stage8_sequence PRIMARY KEY (n)
) ENGINE=InnoDB;

INSERT INTO stage8_sequence (n)
SELECT ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000
       + ten_thousands.n * 10000 + hundred_thousands.n * 100000 + 1
FROM
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) thousands
CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ten_thousands
CROSS JOIN
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundred_thousands
WHERE ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000
      + ten_thousands.n * 10000 + hundred_thousands.n * 100000 < 300000;

INSERT INTO venue (id, openalex_id, display_name, issn_l, venue_type)
SELECT n, CONCAT('https://openalex.org/S8V', n), CONCAT('Stage8 Venue ', n),
       CONCAT(LPAD(MOD(n, 10000), 4, '0'), '-', LPAD(MOD(n * 7, 10000), 4, '0')), 'journal'
FROM stage8_sequence WHERE n <= 1000;

INSERT INTO organization (id, openalex_id, display_name, country_code, organization_type)
SELECT n, CONCAT('https://openalex.org/S8I', n), CONCAT('Stage8 Organization ', n),
       CASE MOD(n, 4) WHEN 0 THEN 'CN' WHEN 1 THEN 'US' WHEN 2 THEN 'DE' ELSE 'GB' END,
       'education'
FROM stage8_sequence WHERE n <= 10000;

INSERT INTO topic (id, openalex_id, display_name, subfield_name, field_name)
SELECT n, CONCAT('https://openalex.org/S8T', n), CONCAT('Stage8 Topic ', n),
       CONCAT('Stage8 Subfield ', MOD(n, 100)), CONCAT('Stage8 Field ', MOD(n, 20))
FROM stage8_sequence WHERE n <= 2000;

INSERT INTO author (id, display_name)
SELECT n, CONCAT('Stage8 Author ', n)
FROM stage8_sequence WHERE n <= 300000;

INSERT INTO author_external_id (author_id, id_type, external_id)
SELECT n, 'OPENALEX', CONCAT('https://openalex.org/S8A', n)
FROM stage8_sequence WHERE n <= 300000;

INSERT INTO achievement (
    id, title_original, title_normalized, doi_normalized, match_fingerprint,
    achievement_type, language, publication_date, date_precision, primary_venue_id,
    first_seen_at, last_seen_at
)
SELECT n, CONCAT('Stage8 Deterministic Achievement ', n), CONCAT('stage8 deterministic achievement ', n),
       CONCAT('10.8000/stage8.', n), SHA2(CONCAT('stage8-achievement-', n), 256),
       CASE MOD(n, 3) WHEN 0 THEN 'article' WHEN 1 THEN 'book-chapter' ELSE 'proceedings-article' END,
       'en', DATE_ADD('2010-01-01', INTERVAL MOD(n, 5478) DAY), 'DAY', MOD(n - 1, 1000) + 1,
       '2026-09-03 00:00:00.000000', '2026-09-03 00:00:00.000000'
FROM stage8_sequence WHERE n <= 100000;

INSERT INTO paper_detail (achievement_id, abstract_text, authorships_may_be_incomplete)
SELECT n, CONCAT('Deterministic non-sensitive stage8 abstract ', n), FALSE
FROM stage8_sequence WHERE n <= 100000;

INSERT INTO achievement_author (achievement_id, author_id, author_position)
SELECT CEIL(n / 3), n, MOD(n - 1, 3) + 1
FROM stage8_sequence WHERE n <= 300000;

INSERT INTO authorship_organization (achievement_id, author_id, organization_id)
SELECT CEIL(n / 3), n, MOD(n - 1, 10000) + 1
FROM stage8_sequence WHERE n <= 300000;

INSERT INTO achievement_topic (achievement_id, topic_id, topic_position)
SELECT CEIL(n / 2), MOD(n - 1, 2000) + 1, MOD(n - 1, 2) + 1
FROM stage8_sequence WHERE n <= 200000;

INSERT INTO achievement_reference (
    citing_achievement_id, referenced_external_work_id, referenced_id_type,
    referenced_id_value, cited_achievement_id
)
SELECT n, CONCAT('https://openalex.org/S8W', MOD(n, 100000) + 1), 'OPENALEX',
       CONCAT('https://openalex.org/S8W', MOD(n, 100000) + 1), MOD(n, 100000) + 1
FROM stage8_sequence WHERE n <= 100000;

INSERT INTO graph_projection_state (
    achievement_id, desired_version, applied_version, last_enqueued_at, last_projected_at
)
SELECT n, 1, 1, '2026-09-03 00:00:00.000000', '2026-09-03 00:00:00.000000'
FROM stage8_sequence WHERE n <= 100000;

SELECT 'achievement' AS entity_name, COUNT(*) AS row_count FROM achievement
UNION ALL SELECT 'author', COUNT(*) FROM author
UNION ALL SELECT 'organization', COUNT(*) FROM organization
UNION ALL SELECT 'venue', COUNT(*) FROM venue
UNION ALL SELECT 'topic', COUNT(*) FROM topic
UNION ALL SELECT 'achievement_author', COUNT(*) FROM achievement_author
UNION ALL SELECT 'authorship_organization', COUNT(*) FROM authorship_organization
UNION ALL SELECT 'achievement_topic', COUNT(*) FROM achievement_topic
UNION ALL SELECT 'achievement_reference', COUNT(*) FROM achievement_reference;
