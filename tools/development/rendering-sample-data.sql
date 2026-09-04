SET NAMES utf8mb4;
START TRANSACTION;

-- 数据源只在缺失时创建；若开发库已有正式配置，则完整保留原配置。
INSERT INTO data_source (
    source_code, source_type, base_url, adapter_code, enabled,
    requests_per_second, max_concurrency, connect_timeout_seconds,
    response_timeout_seconds, max_retries, max_response_bytes, compliance_note
) VALUES (
    'OPENALEX', 'OPENALEX', 'https://api.openalex.org', 'OPENALEX_REST_V1', TRUE,
    2, 2, 10, 30, 3, 5242880, '[页面测试] OpenAlex 公开接口样例来源'
) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

INSERT INTO data_source (
    source_code, source_type, base_url, adapter_code, enabled,
    requests_per_second, max_concurrency, connect_timeout_seconds,
    response_timeout_seconds, max_retries, max_response_bytes, compliance_note
) VALUES (
    'CROSSREF', 'CROSSREF', 'https://api.crossref.org', 'CROSSREF_REST_V1', TRUE,
    2, 2, 10, 30, 3, 5242880, '[页面测试] Crossref 公开接口样例来源'
) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

SET @aacv_demo_openalex_source_id = (
    SELECT id FROM data_source WHERE source_code = 'OPENALEX'
);
SET @aacv_demo_crossref_source_id = (
    SELECT id FROM data_source WHERE source_code = 'CROSSREF'
);

SET @aacv_demo_openalex_parameters = JSON_OBJECT(
    'publicationDateFrom', '2021-01-01',
    'publicationDateTo', '2026-12-31',
    'keyword', '可信人工智能与科研数据治理',
    'authorIds', JSON_ARRAY(),
    'institutionIds', JSON_ARRAY(),
    'dois', JSON_ARRAY(),
    'orcids', JSON_ARRAY(),
    'rorIds', JSON_ARRAY(),
    'updatedFrom', NULL,
    'updatedUntil', NULL,
    'maxPages', 2,
    'maxRecords', 100
);
SET @aacv_demo_crossref_parameters = JSON_OBJECT(
    'publicationDateFrom', '2021-01-01',
    'publicationDateTo', '2026-12-31',
    'keyword', 'software engineering metadata',
    'authorIds', JSON_ARRAY(),
    'institutionIds', JSON_ARRAY(),
    'dois', JSON_ARRAY('10.9999/aacv-demo.001', '10.9999/aacv-demo.005'),
    'orcids', JSON_ARRAY(),
    'rorIds', JSON_ARRAY(),
    'updatedFrom', '2026-08-01T00:00:00Z',
    'updatedUntil', '2026-09-01T00:00:00Z',
    'maxPages', 1,
    'maxRecords', 50
);

INSERT INTO crawl_task (
    source_id, task_name, parameter_version, parameters_json,
    parameter_hash, enabled, created_by
) VALUES (
    @aacv_demo_openalex_source_id,
    '[页面测试] 可信人工智能成果采集',
    2,
    @aacv_demo_openalex_parameters,
    SHA2(CAST(@aacv_demo_openalex_parameters AS CHAR), 256),
    TRUE,
    @aacv_demo_actor_id
) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);
SET @aacv_demo_openalex_task_id = LAST_INSERT_ID();

INSERT INTO crawl_task (
    source_id, task_name, parameter_version, parameters_json,
    parameter_hash, enabled, created_by
) VALUES (
    @aacv_demo_crossref_source_id,
    '[页面测试] 软件工程 DOI 回补',
    2,
    @aacv_demo_crossref_parameters,
    SHA2(CAST(@aacv_demo_crossref_parameters AS CHAR), 256),
    TRUE,
    @aacv_demo_actor_id
) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);
SET @aacv_demo_crossref_task_id = LAST_INSERT_ID();

INSERT INTO crawl_run (
    task_id, run_number, trigger_type, status, requested_by, parameter_hash,
    range_start, range_end, read_count, parsed_count, created_count,
    updated_count, duplicate_count, failure_count, request_count,
    started_at, finished_at, created_at, updated_at
) VALUES (
    @aacv_demo_openalex_task_id,
    '10000000-0000-4000-8000-000000000001',
    'SCHEDULED', 'SUCCEEDED', @aacv_demo_actor_id,
    SHA2(CAST(@aacv_demo_openalex_parameters AS CHAR), 256),
    '2021-01-01', '2026-12-31', 8, 8, 8, 0, 0, 0, 2,
    UTC_TIMESTAMP(6) - INTERVAL 45 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 45 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE
) ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id),
    task_id = VALUES(task_id),
    status = VALUES(status),
    read_count = VALUES(read_count),
    parsed_count = VALUES(parsed_count),
    created_count = VALUES(created_count),
    updated_count = VALUES(updated_count),
    duplicate_count = VALUES(duplicate_count),
    failure_count = VALUES(failure_count),
    request_count = VALUES(request_count),
    started_at = VALUES(started_at),
    finished_at = VALUES(finished_at),
    updated_at = VALUES(updated_at);
SET @aacv_demo_openalex_run_id = LAST_INSERT_ID();

INSERT INTO crawl_run (
    task_id, run_number, trigger_type, status, requested_by, parameter_hash,
    range_start, range_end, read_count, parsed_count, created_count,
    updated_count, duplicate_count, failure_count, request_count,
    started_at, finished_at, created_at, updated_at
) VALUES (
    @aacv_demo_crossref_task_id,
    '10000000-0000-4000-8000-000000000002',
    'MANUAL', 'PARTIAL_SUCCESS', @aacv_demo_actor_id,
    SHA2(CAST(@aacv_demo_crossref_parameters AS CHAR), 256),
    '2021-01-01', '2026-12-31', 7, 6, 4, 2, 0, 2, 1,
    UTC_TIMESTAMP(6) - INTERVAL 25 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 25 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE
) ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id),
    task_id = VALUES(task_id),
    status = VALUES(status),
    read_count = VALUES(read_count),
    parsed_count = VALUES(parsed_count),
    created_count = VALUES(created_count),
    updated_count = VALUES(updated_count),
    duplicate_count = VALUES(duplicate_count),
    failure_count = VALUES(failure_count),
    request_count = VALUES(request_count),
    started_at = VALUES(started_at),
    finished_at = VALUES(finished_at),
    updated_at = VALUES(updated_at);
SET @aacv_demo_crossref_run_id = LAST_INSERT_ID();

INSERT INTO crawl_checkpoint (
    run_id, partition_key, cursor_value, cursor_hash,
    committed_pages, committed_records, committed_at
) VALUES
    (@aacv_demo_openalex_run_id, 'default', 'aacv-demo-openalex-cursor',
     SHA2('aacv-demo-openalex-cursor', 256), 2, 8, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    (@aacv_demo_crossref_run_id, 'default', 'aacv-demo-crossref-cursor',
     SHA2('aacv-demo-crossref-cursor', 256), 1, 6, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE)
ON DUPLICATE KEY UPDATE
    cursor_value = VALUES(cursor_value),
    cursor_hash = VALUES(cursor_hash),
    committed_pages = VALUES(committed_pages),
    committed_records = VALUES(committed_records),
    committed_at = VALUES(committed_at);

-- 成果载体同时保留 OpenAlex 与 ISSN 标识，便于目录筛选和详情追溯。
INSERT INTO venue (openalex_id, display_name, issn_l, venue_type) VALUES
    ('https://openalex.org/AACVDEMOV1', '可信计算学报', '2099-1001', 'journal'),
    ('https://openalex.org/AACVDEMOV2', 'Journal of Research Data Systems', '2099-1002', 'journal'),
    ('https://openalex.org/AACVDEMOV3', '智能软件工程国际会议', '2099-1003', 'conference')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

SET @aacv_demo_venue_1 = (SELECT id FROM venue WHERE openalex_id = 'https://openalex.org/AACVDEMOV1');
SET @aacv_demo_venue_2 = (SELECT id FROM venue WHERE openalex_id = 'https://openalex.org/AACVDEMOV2');
SET @aacv_demo_venue_3 = (SELECT id FROM venue WHERE openalex_id = 'https://openalex.org/AACVDEMOV3');

INSERT IGNORE INTO venue_external_id (venue_id, id_type, external_id) VALUES
    (@aacv_demo_venue_1, 'OPENALEX', 'https://openalex.org/AACVDEMOV1'),
    (@aacv_demo_venue_1, 'ISSN', '2099-1001'),
    (@aacv_demo_venue_2, 'OPENALEX', 'https://openalex.org/AACVDEMOV2'),
    (@aacv_demo_venue_2, 'ISSN', '2099-1002'),
    (@aacv_demo_venue_3, 'OPENALEX', 'https://openalex.org/AACVDEMOV3'),
    (@aacv_demo_venue_3, 'ISSN', '2099-1003');

-- 作者以专用外部标识保证重复执行不会产生重复实体。
INSERT INTO author (display_name)
SELECT '张晨' WHERE NOT EXISTS (
    SELECT 1 FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA1'
);
SET @aacv_demo_author_1 = COALESCE((
    SELECT author_id FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA1'
), LAST_INSERT_ID());
INSERT IGNORE INTO author_external_id (author_id, id_type, external_id) VALUES
    (@aacv_demo_author_1, 'OPENALEX', 'https://openalex.org/AACVDEMOA1'),
    (@aacv_demo_author_1, 'ORCID', '0000-0001-0000-0001');

INSERT INTO author (display_name)
SELECT '李明' WHERE NOT EXISTS (
    SELECT 1 FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA2'
);
SET @aacv_demo_author_2 = COALESCE((
    SELECT author_id FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA2'
), LAST_INSERT_ID());
INSERT IGNORE INTO author_external_id (author_id, id_type, external_id) VALUES
    (@aacv_demo_author_2, 'OPENALEX', 'https://openalex.org/AACVDEMOA2'),
    (@aacv_demo_author_2, 'ORCID', '0000-0001-0000-0002');

INSERT INTO author (display_name)
SELECT '王珂' WHERE NOT EXISTS (
    SELECT 1 FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA3'
);
SET @aacv_demo_author_3 = COALESCE((
    SELECT author_id FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA3'
), LAST_INSERT_ID());
INSERT IGNORE INTO author_external_id (author_id, id_type, external_id) VALUES
    (@aacv_demo_author_3, 'OPENALEX', 'https://openalex.org/AACVDEMOA3'),
    (@aacv_demo_author_3, 'ORCID', '0000-0001-0000-0003');

INSERT INTO author (display_name)
SELECT 'Emily Chen' WHERE NOT EXISTS (
    SELECT 1 FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA4'
);
SET @aacv_demo_author_4 = COALESCE((
    SELECT author_id FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA4'
), LAST_INSERT_ID());
INSERT IGNORE INTO author_external_id (author_id, id_type, external_id) VALUES
    (@aacv_demo_author_4, 'OPENALEX', 'https://openalex.org/AACVDEMOA4'),
    (@aacv_demo_author_4, 'ORCID', '0000-0001-0000-0004');

INSERT INTO author (display_name)
SELECT 'Michael Brown' WHERE NOT EXISTS (
    SELECT 1 FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA5'
);
SET @aacv_demo_author_5 = COALESCE((
    SELECT author_id FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA5'
), LAST_INSERT_ID());
INSERT IGNORE INTO author_external_id (author_id, id_type, external_id) VALUES
    (@aacv_demo_author_5, 'OPENALEX', 'https://openalex.org/AACVDEMOA5');

INSERT INTO author (display_name)
SELECT '周岚' WHERE NOT EXISTS (
    SELECT 1 FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA6'
);
SET @aacv_demo_author_6 = COALESCE((
    SELECT author_id FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA6'
), LAST_INSERT_ID());
INSERT IGNORE INTO author_external_id (author_id, id_type, external_id) VALUES
    (@aacv_demo_author_6, 'OPENALEX', 'https://openalex.org/AACVDEMOA6');

INSERT INTO author (display_name)
SELECT '赵启航' WHERE NOT EXISTS (
    SELECT 1 FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA7'
);
SET @aacv_demo_author_7 = COALESCE((
    SELECT author_id FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA7'
), LAST_INSERT_ID());
INSERT IGNORE INTO author_external_id (author_id, id_type, external_id) VALUES
    (@aacv_demo_author_7, 'OPENALEX', 'https://openalex.org/AACVDEMOA7');

INSERT INTO author (display_name)
SELECT '李铭' WHERE NOT EXISTS (
    SELECT 1 FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA8'
);
SET @aacv_demo_author_8 = COALESCE((
    SELECT author_id FROM author_external_id WHERE id_type = 'OPENALEX' AND external_id = 'https://openalex.org/AACVDEMOA8'
), LAST_INSERT_ID());
INSERT IGNORE INTO author_external_id (author_id, id_type, external_id) VALUES
    (@aacv_demo_author_8, 'OPENALEX', 'https://openalex.org/AACVDEMOA8');

INSERT INTO organization (openalex_id, display_name, country_code, organization_type) VALUES
    ('https://openalex.org/AACVDEMOI1', '东海大学可信计算实验室', 'CN', 'education'),
    ('https://openalex.org/AACVDEMOI2', '国家科研数据中心', 'CN', 'facility'),
    ('https://openalex.org/AACVDEMOI3', 'International Open Science Lab', 'US', 'education'),
    ('https://openalex.org/AACVDEMOI4', '智能软件工程联合中心', 'CN', 'education')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

SET @aacv_demo_org_1 = (SELECT id FROM organization WHERE openalex_id = 'https://openalex.org/AACVDEMOI1');
SET @aacv_demo_org_2 = (SELECT id FROM organization WHERE openalex_id = 'https://openalex.org/AACVDEMOI2');
SET @aacv_demo_org_3 = (SELECT id FROM organization WHERE openalex_id = 'https://openalex.org/AACVDEMOI3');
SET @aacv_demo_org_4 = (SELECT id FROM organization WHERE openalex_id = 'https://openalex.org/AACVDEMOI4');

INSERT IGNORE INTO organization_external_id (organization_id, id_type, external_id) VALUES
    (@aacv_demo_org_1, 'OPENALEX', 'https://openalex.org/AACVDEMOI1'),
    (@aacv_demo_org_1, 'ROR', 'https://ror.org/aacvdemo1'),
    (@aacv_demo_org_2, 'OPENALEX', 'https://openalex.org/AACVDEMOI2'),
    (@aacv_demo_org_2, 'ROR', 'https://ror.org/aacvdemo2'),
    (@aacv_demo_org_3, 'OPENALEX', 'https://openalex.org/AACVDEMOI3'),
    (@aacv_demo_org_3, 'ROR', 'https://ror.org/aacvdemo3'),
    (@aacv_demo_org_4, 'OPENALEX', 'https://openalex.org/AACVDEMOI4'),
    (@aacv_demo_org_4, 'ROR', 'https://ror.org/aacvdemo4');

INSERT INTO topic (openalex_id, display_name, subfield_name, field_name) VALUES
    ('https://openalex.org/AACVDEMOT1', '可信人工智能', '人工智能', '计算机科学'),
    ('https://openalex.org/AACVDEMOT2', '科研数据治理', '信息系统', '计算机科学'),
    ('https://openalex.org/AACVDEMOT3', '软件供应链安全', '软件工程', '计算机科学'),
    ('https://openalex.org/AACVDEMOT4', '知识图谱', '人工智能', '计算机科学'),
    ('https://openalex.org/AACVDEMOT5', '开放科学', '科学计量学', '社会科学')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

SET @aacv_demo_topic_1 = (SELECT id FROM topic WHERE openalex_id = 'https://openalex.org/AACVDEMOT1');
SET @aacv_demo_topic_2 = (SELECT id FROM topic WHERE openalex_id = 'https://openalex.org/AACVDEMOT2');
SET @aacv_demo_topic_3 = (SELECT id FROM topic WHERE openalex_id = 'https://openalex.org/AACVDEMOT3');
SET @aacv_demo_topic_4 = (SELECT id FROM topic WHERE openalex_id = 'https://openalex.org/AACVDEMOT4');
SET @aacv_demo_topic_5 = (SELECT id FROM topic WHERE openalex_id = 'https://openalex.org/AACVDEMOT5');

INSERT INTO subject (source_id, external_id, display_name, subject_path) VALUES
    (@aacv_demo_openalex_source_id, 'AACV-DEMO-SUBJECT-1', '可信人工智能', '计算机科学 > 人工智能 > 可信人工智能'),
    (@aacv_demo_openalex_source_id, 'AACV-DEMO-SUBJECT-2', '科研数据治理', '计算机科学 > 信息系统 > 科研数据治理'),
    (@aacv_demo_openalex_source_id, 'AACV-DEMO-SUBJECT-3', '软件供应链安全', '计算机科学 > 软件工程 > 软件供应链安全'),
    (@aacv_demo_openalex_source_id, 'AACV-DEMO-SUBJECT-4', '知识图谱', '计算机科学 > 人工智能 > 知识图谱'),
    (@aacv_demo_openalex_source_id, 'AACV-DEMO-SUBJECT-5', '开放科学', '社会科学 > 科学计量学 > 开放科学')
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

SET @aacv_demo_subject_1 = (SELECT id FROM subject WHERE source_id = @aacv_demo_openalex_source_id AND external_id = 'AACV-DEMO-SUBJECT-1');
SET @aacv_demo_subject_2 = (SELECT id FROM subject WHERE source_id = @aacv_demo_openalex_source_id AND external_id = 'AACV-DEMO-SUBJECT-2');
SET @aacv_demo_subject_3 = (SELECT id FROM subject WHERE source_id = @aacv_demo_openalex_source_id AND external_id = 'AACV-DEMO-SUBJECT-3');
SET @aacv_demo_subject_4 = (SELECT id FROM subject WHERE source_id = @aacv_demo_openalex_source_id AND external_id = 'AACV-DEMO-SUBJECT-4');
SET @aacv_demo_subject_5 = (SELECT id FROM subject WHERE source_id = @aacv_demo_openalex_source_id AND external_id = 'AACV-DEMO-SUBJECT-5');

-- 十二条成果覆盖多年份、多类型、中英文、长标题、双来源和引用关系。
INSERT INTO achievement (
    title_original, title_normalized, doi_normalized, match_fingerprint,
    fingerprint_version, achievement_type, language, publication_date,
    date_precision, primary_venue_id, first_seen_at, last_seen_at
) VALUES
    ('面向高可信人工智能系统的软件供应链风险识别、证据追踪与可解释治理框架研究',
     '面向高可信人工智能系统的软件供应链风险识别证据追踪与可解释治理框架研究',
     '10.9999/aacv-demo.001', SHA2('aacv-demo-achievement-001', 256), 1,
     'article', 'zh', '2026-06-15', 'DAY', @aacv_demo_venue_1,
     UTC_TIMESTAMP(6) - INTERVAL 60 DAY, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    ('Graph-based Collaboration Discovery for Open Research Communities',
     'graph based collaboration discovery for open research communities',
     '10.9999/aacv-demo.002', SHA2('aacv-demo-achievement-002', 256), 1,
     'article', 'en', '2026-03-20', 'DAY', @aacv_demo_venue_2,
     UTC_TIMESTAMP(6) - INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    ('开放学术元数据的跨来源融合与可追溯治理',
     '开放学术元数据的跨来源融合与可追溯治理',
     '10.9999/aacv-demo.003', SHA2('aacv-demo-achievement-003', 256), 1,
     'proceedings-article', 'zh', '2025-11-01', 'DAY', @aacv_demo_venue_3,
     UTC_TIMESTAMP(6) - INTERVAL 180 DAY, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    ('Evaluating Metadata Completeness Across Scholarly APIs',
     'evaluating metadata completeness across scholarly apis',
     '10.9999/aacv-demo.004', SHA2('aacv-demo-achievement-004', 256), 1,
     'review', 'en', '2025-07-18', 'DAY', @aacv_demo_venue_2,
     UTC_TIMESTAMP(6) - INTERVAL 240 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    ('跨来源 DOI 规范化与学术成果消歧方法',
     '跨来源doi规范化与学术成果消歧方法',
     '10.9999/aacv-demo.005', SHA2('aacv-demo-achievement-005', 256), 1,
     'article', 'zh', '2024-12-08', 'DAY', @aacv_demo_venue_1,
     UTC_TIMESTAMP(6) - INTERVAL 300 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    ('Reproducible Research Data Pipelines: Design and Operations',
     'reproducible research data pipelines design and operations',
     '10.9999/aacv-demo.006', SHA2('aacv-demo-achievement-006', 256), 1,
     'book-chapter', 'en', '2024-05-30', 'DAY', @aacv_demo_venue_2,
     UTC_TIMESTAMP(6) - INTERVAL 420 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    ('科研知识图谱的一致性投影与故障恢复',
     '科研知识图谱的一致性投影与故障恢复',
     '10.9999/aacv-demo.007', SHA2('aacv-demo-achievement-007', 256), 1,
     'article', 'zh', '2023-10-10', 'DAY', @aacv_demo_venue_1,
     UTC_TIMESTAMP(6) - INTERVAL 520 DAY, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    ('Human-in-the-loop Entity Resolution for Scholarly Records',
     'human in the loop entity resolution for scholarly records',
     '10.9999/aacv-demo.008', SHA2('aacv-demo-achievement-008', 256), 1,
     'proceedings-article', 'en', '2023-04-02', 'DAY', @aacv_demo_venue_3,
     UTC_TIMESTAMP(6) - INTERVAL 620 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    ('高校科研合作网络的多层次分析',
     '高校科研合作网络的多层次分析',
     '10.9999/aacv-demo.009', SHA2('aacv-demo-achievement-009', 256), 1,
     'article', 'zh', '2022-09-16', 'DAY', @aacv_demo_venue_1,
     UTC_TIMESTAMP(6) - INTERVAL 720 DAY, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    ('Secure Export Workflows for Research Information Systems',
     'secure export workflows for research information systems',
     '10.9999/aacv-demo.010', SHA2('aacv-demo-achievement-010', 256), 1,
     'article', 'en', '2022-02-11', 'DAY', @aacv_demo_venue_2,
     UTC_TIMESTAMP(6) - INTERVAL 820 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    ('面向长期运维的数据质量监测',
     '面向长期运维的数据质量监测',
     '10.9999/aacv-demo.011', SHA2('aacv-demo-achievement-011', 256), 1,
     'report', 'zh', '2021-08-25', 'DAY', NULL,
     UTC_TIMESTAMP(6) - INTERVAL 920 DAY, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    ('Accessibility-first Visualization of Complex Academic Networks',
     'accessibility first visualization of complex academic networks',
     '10.9999/aacv-demo.012', SHA2('aacv-demo-achievement-012', 256), 1,
     'article', 'en', '2021-01-14', 'DAY', @aacv_demo_venue_3,
     UTC_TIMESTAMP(6) - INTERVAL 1020 DAY, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE)
ON DUPLICATE KEY UPDATE
    id = LAST_INSERT_ID(id),
    last_seen_at = VALUES(last_seen_at);

SET @aacv_demo_achievement_1 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.001');
SET @aacv_demo_achievement_2 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.002');
SET @aacv_demo_achievement_3 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.003');
SET @aacv_demo_achievement_4 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.004');
SET @aacv_demo_achievement_5 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.005');
SET @aacv_demo_achievement_6 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.006');
SET @aacv_demo_achievement_7 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.007');
SET @aacv_demo_achievement_8 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.008');
SET @aacv_demo_achievement_9 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.009');
SET @aacv_demo_achievement_10 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.010');
SET @aacv_demo_achievement_11 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.011');
SET @aacv_demo_achievement_12 = (SELECT id FROM achievement WHERE doi_normalized = '10.9999/aacv-demo.012');

INSERT INTO paper_detail (achievement_id, abstract_text, authorships_may_be_incomplete)
SELECT id,
       CONCAT('这是用于页面渲染验收的非敏感摘要。成果“', title_original,
              '”用于验证中英文排版、详情字段、来源追溯和图谱跳转。'),
       doi_normalized IN ('10.9999/aacv-demo.004', '10.9999/aacv-demo.008')
FROM achievement
WHERE doi_normalized LIKE '10.9999/aacv-demo.%'
ON DUPLICATE KEY UPDATE
    abstract_text = VALUES(abstract_text),
    authorships_may_be_incomplete = VALUES(authorships_may_be_incomplete);

INSERT IGNORE INTO achievement_author (achievement_id, author_id, author_position) VALUES
    (@aacv_demo_achievement_1, @aacv_demo_author_1, 1),
    (@aacv_demo_achievement_1, @aacv_demo_author_2, 2),
    (@aacv_demo_achievement_2, @aacv_demo_author_1, 1),
    (@aacv_demo_achievement_2, @aacv_demo_author_3, 2),
    (@aacv_demo_achievement_3, @aacv_demo_author_2, 1),
    (@aacv_demo_achievement_3, @aacv_demo_author_3, 2),
    (@aacv_demo_achievement_4, @aacv_demo_author_4, 1),
    (@aacv_demo_achievement_4, @aacv_demo_author_5, 2),
    (@aacv_demo_achievement_5, @aacv_demo_author_1, 1),
    (@aacv_demo_achievement_5, @aacv_demo_author_2, 2),
    (@aacv_demo_achievement_5, @aacv_demo_author_6, 3),
    (@aacv_demo_achievement_6, @aacv_demo_author_3, 1),
    (@aacv_demo_achievement_6, @aacv_demo_author_6, 2),
    (@aacv_demo_achievement_7, @aacv_demo_author_1, 1),
    (@aacv_demo_achievement_7, @aacv_demo_author_7, 2),
    (@aacv_demo_achievement_8, @aacv_demo_author_4, 1),
    (@aacv_demo_achievement_8, @aacv_demo_author_8, 2),
    (@aacv_demo_achievement_9, @aacv_demo_author_2, 1),
    (@aacv_demo_achievement_9, @aacv_demo_author_7, 2),
    (@aacv_demo_achievement_10, @aacv_demo_author_5, 1),
    (@aacv_demo_achievement_10, @aacv_demo_author_8, 2),
    (@aacv_demo_achievement_11, @aacv_demo_author_6, 1),
    (@aacv_demo_achievement_11, @aacv_demo_author_7, 2),
    (@aacv_demo_achievement_12, @aacv_demo_author_1, 1),
    (@aacv_demo_achievement_12, @aacv_demo_author_8, 2);

INSERT IGNORE INTO authorship_organization (achievement_id, author_id, organization_id) VALUES
    (@aacv_demo_achievement_1, @aacv_demo_author_1, @aacv_demo_org_1),
    (@aacv_demo_achievement_1, @aacv_demo_author_2, @aacv_demo_org_1),
    (@aacv_demo_achievement_2, @aacv_demo_author_1, @aacv_demo_org_1),
    (@aacv_demo_achievement_2, @aacv_demo_author_3, @aacv_demo_org_2),
    (@aacv_demo_achievement_3, @aacv_demo_author_2, @aacv_demo_org_1),
    (@aacv_demo_achievement_3, @aacv_demo_author_3, @aacv_demo_org_2),
    (@aacv_demo_achievement_4, @aacv_demo_author_4, @aacv_demo_org_2),
    (@aacv_demo_achievement_4, @aacv_demo_author_5, @aacv_demo_org_3),
    (@aacv_demo_achievement_5, @aacv_demo_author_1, @aacv_demo_org_1),
    (@aacv_demo_achievement_5, @aacv_demo_author_2, @aacv_demo_org_1),
    (@aacv_demo_achievement_5, @aacv_demo_author_6, @aacv_demo_org_3),
    (@aacv_demo_achievement_6, @aacv_demo_author_3, @aacv_demo_org_2),
    (@aacv_demo_achievement_6, @aacv_demo_author_6, @aacv_demo_org_3),
    (@aacv_demo_achievement_7, @aacv_demo_author_1, @aacv_demo_org_1),
    (@aacv_demo_achievement_7, @aacv_demo_author_7, @aacv_demo_org_4),
    (@aacv_demo_achievement_8, @aacv_demo_author_4, @aacv_demo_org_2),
    (@aacv_demo_achievement_8, @aacv_demo_author_8, @aacv_demo_org_4),
    (@aacv_demo_achievement_9, @aacv_demo_author_2, @aacv_demo_org_1),
    (@aacv_demo_achievement_9, @aacv_demo_author_7, @aacv_demo_org_4),
    (@aacv_demo_achievement_10, @aacv_demo_author_5, @aacv_demo_org_3),
    (@aacv_demo_achievement_10, @aacv_demo_author_8, @aacv_demo_org_4),
    (@aacv_demo_achievement_11, @aacv_demo_author_6, @aacv_demo_org_3),
    (@aacv_demo_achievement_11, @aacv_demo_author_7, @aacv_demo_org_4),
    (@aacv_demo_achievement_12, @aacv_demo_author_1, @aacv_demo_org_1),
    (@aacv_demo_achievement_12, @aacv_demo_author_8, @aacv_demo_org_4);

INSERT IGNORE INTO achievement_topic (achievement_id, topic_id, topic_position) VALUES
    (@aacv_demo_achievement_1, @aacv_demo_topic_1, 1), (@aacv_demo_achievement_1, @aacv_demo_topic_3, 2),
    (@aacv_demo_achievement_2, @aacv_demo_topic_4, 1), (@aacv_demo_achievement_2, @aacv_demo_topic_5, 2),
    (@aacv_demo_achievement_3, @aacv_demo_topic_2, 1), (@aacv_demo_achievement_3, @aacv_demo_topic_5, 2),
    (@aacv_demo_achievement_4, @aacv_demo_topic_2, 1), (@aacv_demo_achievement_4, @aacv_demo_topic_5, 2),
    (@aacv_demo_achievement_5, @aacv_demo_topic_2, 1), (@aacv_demo_achievement_5, @aacv_demo_topic_4, 2),
    (@aacv_demo_achievement_6, @aacv_demo_topic_2, 1), (@aacv_demo_achievement_6, @aacv_demo_topic_5, 2),
    (@aacv_demo_achievement_7, @aacv_demo_topic_4, 1), (@aacv_demo_achievement_7, @aacv_demo_topic_3, 2),
    (@aacv_demo_achievement_8, @aacv_demo_topic_2, 1), (@aacv_demo_achievement_8, @aacv_demo_topic_4, 2),
    (@aacv_demo_achievement_9, @aacv_demo_topic_4, 1), (@aacv_demo_achievement_9, @aacv_demo_topic_5, 2),
    (@aacv_demo_achievement_10, @aacv_demo_topic_3, 1), (@aacv_demo_achievement_10, @aacv_demo_topic_2, 2),
    (@aacv_demo_achievement_11, @aacv_demo_topic_2, 1), (@aacv_demo_achievement_11, @aacv_demo_topic_3, 2),
    (@aacv_demo_achievement_12, @aacv_demo_topic_4, 1), (@aacv_demo_achievement_12, @aacv_demo_topic_1, 2);

INSERT IGNORE INTO achievement_subject (achievement_id, subject_id, subject_position) VALUES
    (@aacv_demo_achievement_1, @aacv_demo_subject_1, 1), (@aacv_demo_achievement_1, @aacv_demo_subject_3, 2),
    (@aacv_demo_achievement_2, @aacv_demo_subject_4, 1), (@aacv_demo_achievement_2, @aacv_demo_subject_5, 2),
    (@aacv_demo_achievement_3, @aacv_demo_subject_2, 1), (@aacv_demo_achievement_3, @aacv_demo_subject_5, 2),
    (@aacv_demo_achievement_4, @aacv_demo_subject_2, 1), (@aacv_demo_achievement_4, @aacv_demo_subject_5, 2),
    (@aacv_demo_achievement_5, @aacv_demo_subject_2, 1), (@aacv_demo_achievement_5, @aacv_demo_subject_4, 2),
    (@aacv_demo_achievement_6, @aacv_demo_subject_2, 1), (@aacv_demo_achievement_6, @aacv_demo_subject_5, 2),
    (@aacv_demo_achievement_7, @aacv_demo_subject_4, 1), (@aacv_demo_achievement_7, @aacv_demo_subject_3, 2),
    (@aacv_demo_achievement_8, @aacv_demo_subject_2, 1), (@aacv_demo_achievement_8, @aacv_demo_subject_4, 2),
    (@aacv_demo_achievement_9, @aacv_demo_subject_4, 1), (@aacv_demo_achievement_9, @aacv_demo_subject_5, 2),
    (@aacv_demo_achievement_10, @aacv_demo_subject_3, 1), (@aacv_demo_achievement_10, @aacv_demo_subject_2, 2),
    (@aacv_demo_achievement_11, @aacv_demo_subject_2, 1), (@aacv_demo_achievement_11, @aacv_demo_subject_3, 2),
    (@aacv_demo_achievement_12, @aacv_demo_subject_4, 1), (@aacv_demo_achievement_12, @aacv_demo_subject_1, 2);

INSERT IGNORE INTO achievement_reference (
    citing_achievement_id, referenced_external_work_id,
    referenced_id_type, referenced_id_value, cited_achievement_id
) VALUES
    (@aacv_demo_achievement_1, '10.9999/aacv-demo.005', 'DOI', '10.9999/aacv-demo.005', @aacv_demo_achievement_5),
    (@aacv_demo_achievement_2, '10.9999/aacv-demo.003', 'DOI', '10.9999/aacv-demo.003', @aacv_demo_achievement_3),
    (@aacv_demo_achievement_3, '10.9999/aacv-demo.006', 'DOI', '10.9999/aacv-demo.006', @aacv_demo_achievement_6),
    (@aacv_demo_achievement_5, '10.9999/aacv-demo.007', 'DOI', '10.9999/aacv-demo.007', @aacv_demo_achievement_7),
    (@aacv_demo_achievement_7, '10.9999/aacv-demo.012', 'DOI', '10.9999/aacv-demo.012', @aacv_demo_achievement_12);

-- 原始记录使用最小化非敏感载荷，不保存任何外部账号或真实用户信息。
INSERT INTO raw_record (
    source_id, run_id, external_record_id, source_url, fetched_at,
    payload_hash, parser_version, parse_status, payload, payload_expires_at,
    first_seen_at, last_seen_at
) VALUES
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_run_id, 'AACV-DEMO-OPENALEX-001', 'https://api.openalex.org/works/AACV-DEMO-001', UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE, SHA2('AACV-DEMO-OPENALEX-001', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.001'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 60 DAY, UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_run_id, 'AACV-DEMO-OPENALEX-002', 'https://api.openalex.org/works/AACV-DEMO-002', UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE, SHA2('AACV-DEMO-OPENALEX-002', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.002'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_run_id, 'AACV-DEMO-OPENALEX-003', 'https://api.openalex.org/works/AACV-DEMO-003', UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE, SHA2('AACV-DEMO-OPENALEX-003', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.003'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 180 DAY, UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_run_id, 'AACV-DEMO-OPENALEX-005', 'https://api.openalex.org/works/AACV-DEMO-005', UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE, SHA2('AACV-DEMO-OPENALEX-005', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.005'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 300 DAY, UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_run_id, 'AACV-DEMO-OPENALEX-007', 'https://api.openalex.org/works/AACV-DEMO-007', UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE, SHA2('AACV-DEMO-OPENALEX-007', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.007'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 520 DAY, UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_run_id, 'AACV-DEMO-OPENALEX-009', 'https://api.openalex.org/works/AACV-DEMO-009', UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE, SHA2('AACV-DEMO-OPENALEX-009', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.009'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 720 DAY, UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_run_id, 'AACV-DEMO-OPENALEX-011', 'https://api.openalex.org/works/AACV-DEMO-011', UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE, SHA2('AACV-DEMO-OPENALEX-011', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.011'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 920 DAY, UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_run_id, 'AACV-DEMO-OPENALEX-012', 'https://api.openalex.org/works/AACV-DEMO-012', UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE, SHA2('AACV-DEMO-OPENALEX-012', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.012'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 1020 DAY, UTC_TIMESTAMP(6) - INTERVAL 44 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, 'AACV-DEMO-CROSSREF-004', 'https://api.crossref.org/works/10.9999/aacv-demo.004', UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE, SHA2('AACV-DEMO-CROSSREF-004', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.004', 'authorsIncomplete', TRUE), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 240 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, 'AACV-DEMO-CROSSREF-006', 'https://api.crossref.org/works/10.9999/aacv-demo.006', UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE, SHA2('AACV-DEMO-CROSSREF-006', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.006'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 420 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, 'AACV-DEMO-CROSSREF-008', 'https://api.crossref.org/works/10.9999/aacv-demo.008', UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE, SHA2('AACV-DEMO-CROSSREF-008', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.008'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 620 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, 'AACV-DEMO-CROSSREF-010', 'https://api.crossref.org/works/10.9999/aacv-demo.010', UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE, SHA2('AACV-DEMO-CROSSREF-010', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.010'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 820 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, 'AACV-DEMO-CROSSREF-001', 'https://api.crossref.org/works/10.9999/aacv-demo.001', UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE, SHA2('AACV-DEMO-CROSSREF-001', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.001', 'titleConflict', TRUE), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 60 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, 'AACV-DEMO-CROSSREF-005', 'https://api.crossref.org/works/10.9999/aacv-demo.005', UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE, SHA2('AACV-DEMO-CROSSREF-005', 256), 'aacv-demo-v1', 'PARSED', JSON_OBJECT('fixture', TRUE, 'doi', '10.9999/aacv-demo.005', 'dateConflict', TRUE), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 300 DAY, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, 'AACV-DEMO-CROSSREF-FAILED-001', 'https://api.crossref.org/works/AACV-DEMO-MISSING-DOI', UTC_TIMESTAMP(6) - INTERVAL 23 MINUTE, SHA2('AACV-DEMO-CROSSREF-FAILED-001', 256), 'aacv-demo-v1', 'FAILED', JSON_OBJECT('fixture', TRUE, 'title', '缺少 DOI 的页面测试记录'), UTC_TIMESTAMP(6) + INTERVAL 90 DAY, UTC_TIMESTAMP(6) - INTERVAL 23 MINUTE, UTC_TIMESTAMP(6) - INTERVAL 23 MINUTE)
ON DUPLICATE KEY UPDATE
    run_id = VALUES(run_id),
    fetched_at = VALUES(fetched_at),
    payload_expires_at = VALUES(payload_expires_at),
    last_seen_at = VALUES(last_seen_at);

INSERT INTO achievement_source (
    achievement_id, source_id, raw_record_id, external_record_id,
    source_url, parser_version, first_seen_at, last_seen_at
)
SELECT achievement.id, mapping.source_id, raw_record.id, mapping.external_record_id,
       mapping.source_url, 'aacv-demo-v1', achievement.first_seen_at, UTC_TIMESTAMP(6)
FROM (
    SELECT '10.9999/aacv-demo.001' AS doi, @aacv_demo_openalex_source_id AS source_id, 'AACV-DEMO-OPENALEX-001' AS external_record_id, 'https://api.openalex.org/works/AACV-DEMO-001' AS source_url
    UNION ALL SELECT '10.9999/aacv-demo.002', @aacv_demo_openalex_source_id, 'AACV-DEMO-OPENALEX-002', 'https://api.openalex.org/works/AACV-DEMO-002'
    UNION ALL SELECT '10.9999/aacv-demo.003', @aacv_demo_openalex_source_id, 'AACV-DEMO-OPENALEX-003', 'https://api.openalex.org/works/AACV-DEMO-003'
    UNION ALL SELECT '10.9999/aacv-demo.005', @aacv_demo_openalex_source_id, 'AACV-DEMO-OPENALEX-005', 'https://api.openalex.org/works/AACV-DEMO-005'
    UNION ALL SELECT '10.9999/aacv-demo.007', @aacv_demo_openalex_source_id, 'AACV-DEMO-OPENALEX-007', 'https://api.openalex.org/works/AACV-DEMO-007'
    UNION ALL SELECT '10.9999/aacv-demo.009', @aacv_demo_openalex_source_id, 'AACV-DEMO-OPENALEX-009', 'https://api.openalex.org/works/AACV-DEMO-009'
    UNION ALL SELECT '10.9999/aacv-demo.011', @aacv_demo_openalex_source_id, 'AACV-DEMO-OPENALEX-011', 'https://api.openalex.org/works/AACV-DEMO-011'
    UNION ALL SELECT '10.9999/aacv-demo.012', @aacv_demo_openalex_source_id, 'AACV-DEMO-OPENALEX-012', 'https://api.openalex.org/works/AACV-DEMO-012'
    UNION ALL SELECT '10.9999/aacv-demo.004', @aacv_demo_crossref_source_id, 'AACV-DEMO-CROSSREF-004', 'https://api.crossref.org/works/10.9999/aacv-demo.004'
    UNION ALL SELECT '10.9999/aacv-demo.006', @aacv_demo_crossref_source_id, 'AACV-DEMO-CROSSREF-006', 'https://api.crossref.org/works/10.9999/aacv-demo.006'
    UNION ALL SELECT '10.9999/aacv-demo.008', @aacv_demo_crossref_source_id, 'AACV-DEMO-CROSSREF-008', 'https://api.crossref.org/works/10.9999/aacv-demo.008'
    UNION ALL SELECT '10.9999/aacv-demo.010', @aacv_demo_crossref_source_id, 'AACV-DEMO-CROSSREF-010', 'https://api.crossref.org/works/10.9999/aacv-demo.010'
    UNION ALL SELECT '10.9999/aacv-demo.001', @aacv_demo_crossref_source_id, 'AACV-DEMO-CROSSREF-001', 'https://api.crossref.org/works/10.9999/aacv-demo.001'
    UNION ALL SELECT '10.9999/aacv-demo.005', @aacv_demo_crossref_source_id, 'AACV-DEMO-CROSSREF-005', 'https://api.crossref.org/works/10.9999/aacv-demo.005'
) mapping
JOIN achievement ON achievement.doi_normalized = mapping.doi
JOIN raw_record ON raw_record.source_id = mapping.source_id
               AND raw_record.external_record_id = mapping.external_record_id
ON DUPLICATE KEY UPDATE
    achievement_id = VALUES(achievement_id),
    last_seen_at = VALUES(last_seen_at);

INSERT INTO achievement_source_snapshot (
    achievement_source_id, normalized_payload, source_priority, observed_at
)
SELECT source_record.id,
       JSON_OBJECT('fixture', 'rendering', 'doi', achievement.doi_normalized),
       CASE data_source.source_type WHEN 'OPENALEX' THEN 10 ELSE 20 END,
       UTC_TIMESTAMP(6)
FROM achievement_source source_record
JOIN achievement ON achievement.id = source_record.achievement_id
JOIN data_source ON data_source.id = source_record.source_id
WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%'
ON DUPLICATE KEY UPDATE
    normalized_payload = VALUES(normalized_payload),
    observed_at = VALUES(observed_at);

INSERT IGNORE INTO entity_field_provenance (
    entity_type, entity_id, field_name, source_id, raw_record_id,
    source_priority, field_value, selected, observed_at
)
SELECT 'ACHIEVEMENT', achievement.id, fields.field_name,
       source_record.source_id, source_record.raw_record_id,
       10,
       CASE fields.field_name
           WHEN 'title' THEN JSON_OBJECT('value', achievement.title_original)
           WHEN 'doi' THEN JSON_OBJECT('value', achievement.doi_normalized)
           ELSE JSON_OBJECT('value', achievement.publication_date)
       END,
       TRUE,
       UTC_TIMESTAMP(6)
FROM achievement
JOIN achievement_source source_record
  ON source_record.achievement_id = achievement.id
 AND source_record.id = (
     SELECT MIN(selected_source.id)
     FROM achievement_source selected_source
     WHERE selected_source.achievement_id = achievement.id
 )
CROSS JOIN (
    SELECT 'title' AS field_name
    UNION ALL SELECT 'doi'
    UNION ALL SELECT 'publicationDate'
) fields
WHERE achievement.doi_normalized LIKE '10.9999/aacv-demo.%';

SET @aacv_demo_failed_raw_id = (
    SELECT id FROM raw_record
    WHERE source_id = @aacv_demo_crossref_source_id
      AND external_record_id = 'AACV-DEMO-CROSSREF-FAILED-001'
);
SET @aacv_demo_incomplete_raw_id = (
    SELECT id FROM raw_record
    WHERE source_id = @aacv_demo_crossref_source_id
      AND external_record_id = 'AACV-DEMO-CROSSREF-004'
);
SET @aacv_demo_conflict_raw_id = (
    SELECT id FROM raw_record
    WHERE source_id = @aacv_demo_crossref_source_id
      AND external_record_id = 'AACV-DEMO-CROSSREF-001'
);

INSERT INTO crawl_failure (
    run_id, raw_record_id, external_record_id, failure_stage,
    error_category, safe_message, retryable, attempt_count,
    resolved, evidence_hash, created_at, updated_at
)
SELECT @aacv_demo_crossref_run_id, @aacv_demo_failed_raw_id,
       'AACV-DEMO-CROSSREF-FAILED-001', 'VALIDATE', 'MISSING_STABLE_IDENTIFIER',
       '[页面测试] 来源记录缺少可用 DOI，已保留有限证据供人工检查',
       FALSE, 1, FALSE, SHA2('aacv-demo-failure-validate', 256),
       UTC_TIMESTAMP(6) - INTERVAL 23 MINUTE, UTC_TIMESTAMP(6) - INTERVAL 23 MINUTE
WHERE NOT EXISTS (
    SELECT 1 FROM crawl_failure
    WHERE run_id = @aacv_demo_crossref_run_id
      AND evidence_hash = SHA2('aacv-demo-failure-validate', 256)
);

INSERT INTO crawl_failure (
    run_id, raw_record_id, external_record_id, failure_stage,
    error_category, safe_message, retryable, attempt_count,
    resolved, evidence_hash, created_at, updated_at
)
SELECT @aacv_demo_crossref_run_id, NULL,
       'AACV-DEMO-CROSSREF-RATE-LIMIT', 'FETCH', 'RATE_LIMITED',
       '[页面测试] 来源返回限流响应，等待受控重试窗口',
       TRUE, 2, FALSE, SHA2('aacv-demo-failure-fetch', 256),
       UTC_TIMESTAMP(6) - INTERVAL 22 MINUTE, UTC_TIMESTAMP(6) - INTERVAL 22 MINUTE
WHERE NOT EXISTS (
    SELECT 1 FROM crawl_failure
    WHERE run_id = @aacv_demo_crossref_run_id
      AND evidence_hash = SHA2('aacv-demo-failure-fetch', 256)
);

INSERT INTO quality_metric_snapshot (
    source_id, task_id, run_id, metric_code,
    numerator, denominator, metric_value, measured_at
) VALUES
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_task_id, @aacv_demo_openalex_run_id, 'TOTAL_RECORDS', 8, 8, 1.000000, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_task_id, @aacv_demo_openalex_run_id, 'VALID_RECORDS', 8, 8, 1.000000, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_task_id, @aacv_demo_openalex_run_id, 'AUTO_MATCHES', 2, 8, 0.250000, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    (@aacv_demo_openalex_source_id, @aacv_demo_openalex_task_id, @aacv_demo_openalex_run_id, 'NEW_CANDIDATES', 2, 8, 0.250000, UTC_TIMESTAMP(6) - INTERVAL 43 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_task_id, @aacv_demo_crossref_run_id, 'TOTAL_RECORDS', 7, 7, 1.000000, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_task_id, @aacv_demo_crossref_run_id, 'VALID_RECORDS', 6, 7, 0.857143, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_task_id, @aacv_demo_crossref_run_id, 'MISSING_OR_INVALID_DOI', 1, 7, 0.142857, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_task_id, @aacv_demo_crossref_run_id, 'MISSING_AUTHORS', 1, 7, 0.142857, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_task_id, @aacv_demo_crossref_run_id, 'FIELD_CONFLICTS', 2, 7, 0.285714, UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE)
ON DUPLICATE KEY UPDATE
    numerator = VALUES(numerator),
    denominator = VALUES(denominator),
    metric_value = VALUES(metric_value),
    measured_at = VALUES(measured_at);

INSERT IGNORE INTO quality_issue_sample (
    source_id, run_id, raw_record_id, metric_code,
    external_record_id, evidence_json
) VALUES
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, @aacv_demo_failed_raw_id,
     'MISSING_OR_INVALID_DOI', 'AACV-DEMO-CROSSREF-FAILED-001',
     JSON_OBJECT('field', 'DOI', 'reason', '来源记录未提供 DOI')),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, @aacv_demo_incomplete_raw_id,
     'MISSING_AUTHORS', 'AACV-DEMO-CROSSREF-004',
     JSON_OBJECT('field', 'author', 'reason', '作者列表不完整')),
    (@aacv_demo_crossref_source_id, @aacv_demo_crossref_run_id, @aacv_demo_conflict_raw_id,
     'FIELD_CONFLICTS', 'AACV-DEMO-CROSSREF-001',
     JSON_OBJECT('field', 'title', 'openAlex', '面向高可信人工智能系统', 'crossref', '可信人工智能系统'));

INSERT INTO duplicate_candidate (
    entity_type, left_entity_id, right_entity_id, match_basis,
    source_id, rule_version, evidence_json, evidence_hash, status
)
SELECT 'AUTHOR', LEAST(@aacv_demo_author_2, @aacv_demo_author_8),
       GREATEST(@aacv_demo_author_2, @aacv_demo_author_8), 'TEXT_NAME',
       @aacv_demo_crossref_source_id, 1,
       JSON_OBJECT('leftName', '李明', 'rightName', '李铭', 'similarity', 0.92,
                   'note', '仅供页面测试，需人工判断'),
       SHA2('aacv-demo-candidate-author', 256), 'PENDING'
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

INSERT INTO duplicate_candidate (
    entity_type, left_entity_id, right_entity_id, match_basis,
    source_id, rule_version, evidence_json, evidence_hash, status
)
SELECT 'ORGANIZATION', LEAST(@aacv_demo_org_1, @aacv_demo_org_4),
       GREATEST(@aacv_demo_org_1, @aacv_demo_org_4), 'TEXT_NAME',
       @aacv_demo_openalex_source_id, 1,
       JSON_OBJECT('leftName', '东海大学可信计算实验室',
                   'rightName', '智能软件工程联合中心',
                   'sharedAuthors', 2, 'note', '仅供页面测试，需人工判断'),
       SHA2('aacv-demo-candidate-organization', 256), 'PENDING'
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

-- 首次执行生成投影请求；重复执行不会重置已经完成或失败的事件状态。
INSERT INTO graph_projection_state (
    achievement_id, desired_version, applied_version, last_enqueued_at
) VALUES
    (@aacv_demo_achievement_1, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_2, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_3, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_4, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_5, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_6, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_7, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_8, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_9, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_10, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_11, 1, 0, UTC_TIMESTAMP(6)),
    (@aacv_demo_achievement_12, 2, 0, UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    desired_version = GREATEST(desired_version, VALUES(desired_version)),
    last_enqueued_at = VALUES(last_enqueued_at);

INSERT INTO graph_outbox_event (
    event_id, achievement_id, desired_version, event_type,
    status, attempts, next_attempt_at, created_at, updated_at
) VALUES
    ('20000000-0000-4000-8000-000000000001', @aacv_demo_achievement_1, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000002', @aacv_demo_achievement_2, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000003', @aacv_demo_achievement_3, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000004', @aacv_demo_achievement_4, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000005', @aacv_demo_achievement_5, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000006', @aacv_demo_achievement_6, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000007', @aacv_demo_achievement_7, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000008', @aacv_demo_achievement_8, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000009', @aacv_demo_achievement_9, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000010', @aacv_demo_achievement_10, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000011', @aacv_demo_achievement_11, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('20000000-0000-4000-8000-000000000012', @aacv_demo_achievement_12, 1, 'REFRESH', 'PENDING', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('2fffffff-0000-4000-8000-000000000012', @aacv_demo_achievement_12, 2, 'REFRESH', 'DEAD', 5, UTC_TIMESTAMP(6) - INTERVAL 8 MINUTE, UTC_TIMESTAMP(6) - INTERVAL 12 MINUTE, UTC_TIMESTAMP(6) - INTERVAL 8 MINUTE)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO graph_sync_dead_letter (
    event_id, error_code, error_summary, failed_at
) VALUES (
    '2fffffff-0000-4000-8000-000000000012',
    'GRAPH_PROJECTION_FAILED',
    '[页面测试] 模拟一次已达重试上限的图投影失败',
    UTC_TIMESTAMP(6) - INTERVAL 8 MINUTE
) ON DUPLICATE KEY UPDATE event_id = event_id;

INSERT INTO graph_maintenance_run (
    run_type, status, cursor_achievement_id, scanned_count,
    repaired_count, difference_count, requested_by,
    started_at, completed_at, created_at, updated_at
)
SELECT 'INITIAL_BACKFILL', 'SUCCEEDED', @aacv_demo_achievement_12,
       12, 12, 0, @aacv_demo_actor_id,
       UTC_TIMESTAMP(6) - INTERVAL 2 HOUR,
       UTC_TIMESTAMP(6) - INTERVAL 119 MINUTE,
       UTC_TIMESTAMP(6) - INTERVAL 2 HOUR,
       UTC_TIMESTAMP(6) - INTERVAL 119 MINUTE
WHERE NOT EXISTS (
    SELECT 1 FROM graph_maintenance_run
    WHERE run_type = 'INITIAL_BACKFILL'
      AND status = 'SUCCEEDED'
      AND scanned_count = 12
      AND repaired_count = 12
);

INSERT INTO graph_maintenance_run (
    run_type, status, cursor_achievement_id, scanned_count,
    repaired_count, difference_count, requested_by,
    error_code, error_summary, started_at, completed_at, created_at, updated_at
)
SELECT 'RECONCILE', 'FAILED', @aacv_demo_achievement_8,
       8, 1, 1, @aacv_demo_actor_id,
       'GRAPH_UNAVAILABLE', '[页面测试] 对账期间 Neo4j 暂不可用',
       UTC_TIMESTAMP(6) - INTERVAL 75 MINUTE,
       UTC_TIMESTAMP(6) - INTERVAL 74 MINUTE,
       UTC_TIMESTAMP(6) - INTERVAL 75 MINUTE,
       UTC_TIMESTAMP(6) - INTERVAL 74 MINUTE
WHERE NOT EXISTS (
    SELECT 1 FROM graph_maintenance_run
    WHERE run_type = 'RECONCILE'
      AND error_summary = '[页面测试] 对账期间 Neo4j 暂不可用'
);

INSERT INTO alert_event (
    alert_type, severity, status, subject_type, subject_id, dedup_key,
    summary, evidence_json, detected_signal_at, first_detected_at,
    last_detected_at, occurrence_count, version
)
VALUES (
    'PARSE_SUCCESS_RATE_DROP', 'WARNING', 'OPEN', 'CRAWL_TASK',
    CAST(@aacv_demo_crossref_task_id AS CHAR),
    CONCAT('PARSE_SUCCESS_RATE_DROP:CRAWL_TASK:', @aacv_demo_crossref_task_id),
    '[页面测试] 最近一次采集运行的解析成功率低于阈值',
    JSON_OBJECT('runId', @aacv_demo_crossref_run_id, 'readCount', 7,
                'parsedCount', 6, 'successRate', '0.857143'),
    UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 24 MINUTE,
    1, 0
) ON DUPLICATE KEY UPDATE id = id;

INSERT INTO alert_event (
    alert_type, severity, status, subject_type, subject_id, dedup_key,
    summary, evidence_json, detected_signal_at, first_detected_at,
    last_detected_at, occurrence_count, version
)
VALUES (
    'GRAPH_SYNC_BACKLOG', 'CRITICAL', 'OPEN', 'GRAPH_SYNC', NULL,
    'GRAPH_SYNC_BACKLOG:GRAPH_SYNC:SYSTEM',
    '[页面测试] 图同步存在死信事件',
    JSON_OBJECT('pendingCount', 12, 'processingCount', 0,
                'deadCount', 1, 'oldestPendingAgeSeconds', 720),
    UTC_TIMESTAMP(6) - INTERVAL 8 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 8 MINUTE,
    UTC_TIMESTAMP(6) - INTERVAL 8 MINUTE,
    1, 0
) ON DUPLICATE KEY UPDATE id = id;

INSERT INTO alert_event (
    alert_type, severity, status, subject_type, subject_id, dedup_key,
    summary, evidence_json, detected_signal_at, first_detected_at,
    last_detected_at, occurrence_count, acknowledged_by,
    acknowledged_at, acknowledgement_reason, version
)
SELECT 'CRAWL_CONSECUTIVE_FAILURES', 'WARNING', 'ACKNOWLEDGED',
       'SOURCE', CAST(@aacv_demo_crossref_source_id AS CHAR),
       CONCAT('CRAWL_CONSECUTIVE_FAILURES:SOURCE:', @aacv_demo_crossref_source_id),
       '[页面测试] 数据源连续采集失败达到阈值',
       JSON_OBJECT('consecutiveFailures', 3, 'note', '历史演示告警'),
       UTC_TIMESTAMP(6) - INTERVAL 1 DAY,
       UTC_TIMESTAMP(6) - INTERVAL 1 DAY,
       UTC_TIMESTAMP(6) - INTERVAL 1 DAY,
       3, @aacv_demo_actor_id,
       UTC_TIMESTAMP(6) - INTERVAL 23 HOUR,
       '[页面测试] 已确认是模拟告警', 1
WHERE NOT EXISTS (
    SELECT 1 FROM alert_event
    WHERE status = 'ACKNOWLEDGED'
      AND acknowledgement_reason = '[页面测试] 已确认是模拟告警'
);

INSERT INTO audit_log (
    actor_user_id, action, target_type, target_id,
    result, trace_id, summary_json, created_at
)
SELECT @aacv_demo_actor_id, 'CRAWL_TASK_CREATED', 'CRAWL_TASK',
       CAST(@aacv_demo_openalex_task_id AS CHAR), 'SUCCESS',
       'aacv-demo-trace-crawl-task',
       JSON_OBJECT('fixture', 'rendering', 'sourceCode', 'OPENALEX'),
       UTC_TIMESTAMP(6) - INTERVAL 2 HOUR
WHERE NOT EXISTS (SELECT 1 FROM audit_log WHERE trace_id = 'aacv-demo-trace-crawl-task');

INSERT INTO audit_log (
    actor_user_id, action, target_type, target_id,
    result, trace_id, summary_json, created_at
)
SELECT @aacv_demo_actor_id, 'EXPORT_COMPLETED', 'EXPORT_TASK',
       'aacv-demo-export', 'SUCCESS', 'aacv-demo-trace-export',
       JSON_OBJECT('fixture', 'rendering', 'format', 'CSV', 'exportedCount', 12),
       UTC_TIMESTAMP(6) - INTERVAL 90 MINUTE
WHERE NOT EXISTS (SELECT 1 FROM audit_log WHERE trace_id = 'aacv-demo-trace-export');

INSERT INTO audit_log (
    actor_user_id, action, target_type, target_id,
    result, trace_id, summary_json, created_at
)
SELECT @aacv_demo_actor_id, 'GRAPH_EVENT_REPLAYED', 'GRAPH_OUTBOX_EVENT',
       '2fffffff-0000-4000-8000-000000000012', 'FAILURE',
       'aacv-demo-trace-graph',
       JSON_OBJECT('fixture', 'rendering', 'errorCode', 'GRAPH_UNAVAILABLE'),
       UTC_TIMESTAMP(6) - INTERVAL 70 MINUTE
WHERE NOT EXISTS (SELECT 1 FROM audit_log WHERE trace_id = 'aacv-demo-trace-graph');

INSERT INTO audit_log (
    actor_user_id, action, target_type, target_id,
    result, trace_id, summary_json, created_at
)
SELECT @aacv_demo_actor_id, 'ALERT_ACKNOWLEDGED', 'ALERT_EVENT',
       'aacv-demo-alert', 'SUCCESS', 'aacv-demo-trace-alert',
       JSON_OBJECT('fixture', 'rendering', 'alertType', 'CRAWL_CONSECUTIVE_FAILURES'),
       UTC_TIMESTAMP(6) - INTERVAL 23 HOUR
WHERE NOT EXISTS (SELECT 1 FROM audit_log WHERE trace_id = 'aacv-demo-trace-alert');

COMMIT;

SELECT 'actor_user_id' AS sample_key, CAST(@aacv_demo_actor_id AS CHAR) AS sample_value
UNION ALL SELECT 'openalex_source_id', CAST(@aacv_demo_openalex_source_id AS CHAR)
UNION ALL SELECT 'crossref_source_id', CAST(@aacv_demo_crossref_source_id AS CHAR)
UNION ALL SELECT 'openalex_task_id', CAST(@aacv_demo_openalex_task_id AS CHAR)
UNION ALL SELECT 'crossref_task_id', CAST(@aacv_demo_crossref_task_id AS CHAR)
UNION ALL SELECT 'openalex_run_id', CAST(@aacv_demo_openalex_run_id AS CHAR)
UNION ALL SELECT 'crossref_run_id', CAST(@aacv_demo_crossref_run_id AS CHAR)
UNION ALL SELECT 'graph_center_achievement_id', CAST(@aacv_demo_achievement_1 AS CHAR)
UNION ALL SELECT 'sample_achievement_count', CAST((
    SELECT COUNT(*) FROM achievement WHERE doi_normalized LIKE '10.9999/aacv-demo.%'
) AS CHAR);
