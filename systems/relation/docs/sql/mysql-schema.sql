-- =====================================================================
-- 学术关系知识图谱构建平台 — MySQL 8.0 建库脚本
-- 说明：MySQL 是权威数据源；Neo4j 为可重建图投影（同步走 graph_sync_event Outbox）
-- 用法：mysql --default-character-set=utf8mb4 -uroot -p academic_graph < docs/sql/mysql-schema.sql
-- 注意：必须带 --default-character-set=utf8mb4，否则中文注释会被 GBK 控制台编码误读而乱码
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------
-- 1. 认证与权限
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希，禁止存明文',
    display_name  VARCHAR(64)  NULL COMMENT '显示名称',
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    version       BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    CONSTRAINT pk_sys_user PRIMARY KEY (id),
    CONSTRAINT uk_sys_user_username UNIQUE (username),
    CONSTRAINT ck_sys_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户账号表';

CREATE TABLE IF NOT EXISTS sys_role (
    id           BIGINT      NOT NULL COMMENT '1=ADMIN 管理员, 2=ANALYST 普通用户',
    role_code    VARCHAR(32) NOT NULL COMMENT '角色编码：ADMIN/ANALYST',
    display_name VARCHAR(64) NOT NULL COMMENT '角色显示名',
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    CONSTRAINT pk_sys_role PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色字典';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id     BIGINT      NOT NULL COMMENT '用户 id',
    role_id     BIGINT      NOT NULL COMMENT '角色 id',
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '授权时间',
    CONSTRAINT pk_sys_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户-角色关联';

CREATE INDEX ix_sys_user_role_role_user ON sys_user_role (role_id, user_id);

-- 角色种子数据
INSERT INTO sys_role (id, role_code, display_name) VALUES (1, 'ADMIN', '管理员')
    ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);
INSERT INTO sys_role (id, role_code, display_name) VALUES (2, 'ANALYST', '普通用户')
    ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

-- ---------------------------------------------------------------
-- 2. 学术实体
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS venue (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    display_name VARCHAR(500) NULL COMMENT '期刊/会议名称',
    issn         VARCHAR(16)  NULL COMMENT '期刊 ISSN',
    venue_type   VARCHAR(64)  NULL COMMENT 'journal/conference',
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    CONSTRAINT pk_venue PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='发表渠道（期刊/会议）';

CREATE INDEX ix_venue_name ON venue (display_name(191), id);

CREATE TABLE IF NOT EXISTS paper (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title            VARCHAR(500) NOT NULL COMMENT '标题',
    doi              VARCHAR(255) NULL COMMENT 'DOI，同库唯一，可空',
    paper_type       VARCHAR(32)  NOT NULL DEFAULT 'JOURNAL_ARTICLE' COMMENT 'JOURNAL_ARTICLE/CONFERENCE_PAPER/PATENT/OTHER',
    language         VARCHAR(16)  NULL COMMENT '语种（如 zh/en）',
    publication_date DATE         NULL COMMENT '发表日期',
    publication_year SMALLINT     GENERATED ALWAYS AS (YEAR(publication_date)) STORED COMMENT '由日期生成，按年分析用',
    abstract_text    MEDIUMTEXT   NULL COMMENT '摘要',
    citation_count   INT          NOT NULL DEFAULT 0 COMMENT '被引次数（引用热度）',
    venue_id         BIGINT       NULL COMMENT '发表渠道，可空',
    extraction_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '实体抽取状态：PENDING/IN_PROGRESS/COMPLETED/FAILED',
    version          BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by       BIGINT       NULL COMMENT '创建者用户 id（对象级权限）',
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    CONSTRAINT pk_paper PRIMARY KEY (id),
    CONSTRAINT uk_paper_doi UNIQUE (doi),
    CONSTRAINT fk_paper_venue FOREIGN KEY (venue_id) REFERENCES venue (id) ON DELETE SET NULL,
    CONSTRAINT fk_paper_creator FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_paper_type CHECK (paper_type IN ('JOURNAL_ARTICLE', 'CONFERENCE_PAPER', 'PATENT', 'OTHER')),
    CONSTRAINT ck_paper_extraction_status CHECK (extraction_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='论文/专利等学术成果';

CREATE INDEX ix_paper_title ON paper (title(191), id);
CREATE INDEX ix_paper_year_type ON paper (publication_year, paper_type, id);
CREATE INDEX ix_paper_venue ON paper (venue_id, id);
CREATE INDEX ix_paper_extraction_status ON paper (extraction_status, id);

CREATE TABLE IF NOT EXISTS author (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    display_name VARCHAR(255) NOT NULL COMMENT '作者姓名',
    orcid        VARCHAR(64)  NULL COMMENT 'ORCID 标识，可空',
    version      BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    CONSTRAINT pk_author PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='作者';

CREATE INDEX ix_author_name ON author (display_name(191), id);
CREATE INDEX ix_author_orcid ON author (orcid, id);

CREATE TABLE IF NOT EXISTS institution (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    display_name     VARCHAR(255) NOT NULL COMMENT '机构名称',
    country_code     CHAR(2)      NULL COMMENT 'ISO 国家码',
    institution_type VARCHAR(64)  NULL COMMENT 'university/institute/company',
    version          BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    CONSTRAINT pk_institution PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='机构';

CREATE INDEX ix_institution_name ON institution (display_name(191), id);

CREATE TABLE IF NOT EXISTS keyword (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(255) NOT NULL COMMENT '关键词/主题名',
    field_name VARCHAR(255) NULL COMMENT '所属学科领域（主题演化分析分类用）',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    CONSTRAINT pk_keyword PRIMARY KEY (id),
    CONSTRAINT uk_keyword_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='关键词/主题';

CREATE INDEX ix_keyword_field ON keyword (field_name(191), id);

-- ---------------------------------------------------------------
-- 3. 关系表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS paper_author (
    paper_id        BIGINT      NOT NULL COMMENT '论文 id',
    author_id       BIGINT      NOT NULL COMMENT '作者 id',
    author_position INT         NOT NULL COMMENT '作者位次，从 1 开始',
    institution_id  BIGINT      NULL COMMENT '署名机构（作者-机构从属的证据）',
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    CONSTRAINT pk_paper_author PRIMARY KEY (paper_id, author_id),
    CONSTRAINT fk_paper_author_paper FOREIGN KEY (paper_id) REFERENCES paper (id) ON DELETE CASCADE,
    CONSTRAINT fk_paper_author_author FOREIGN KEY (author_id) REFERENCES author (id),
    CONSTRAINT fk_paper_author_institution FOREIGN KEY (institution_id) REFERENCES institution (id),
    CONSTRAINT uk_paper_author_position UNIQUE (paper_id, author_position),
    CONSTRAINT ck_paper_author_position CHECK (author_position > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='论文-作者署名';

CREATE INDEX ix_paper_author_author ON paper_author (author_id, paper_id);
CREATE INDEX ix_paper_author_institution ON paper_author (institution_id, author_id);

CREATE TABLE IF NOT EXISTS paper_keyword (
    paper_id         BIGINT      NOT NULL COMMENT '论文 id',
    keyword_id       BIGINT      NOT NULL COMMENT '关键词 id',
    keyword_position INT         NOT NULL DEFAULT 1 COMMENT '关键词位次',
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    CONSTRAINT pk_paper_keyword PRIMARY KEY (paper_id, keyword_id),
    CONSTRAINT fk_paper_keyword_paper FOREIGN KEY (paper_id) REFERENCES paper (id) ON DELETE CASCADE,
    CONSTRAINT fk_paper_keyword_keyword FOREIGN KEY (keyword_id) REFERENCES keyword (id),
    CONSTRAINT uk_paper_keyword_position UNIQUE (paper_id, keyword_position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='论文-关键词关联';

CREATE INDEX ix_paper_keyword_keyword ON paper_keyword (keyword_id, paper_id);

CREATE TABLE IF NOT EXISTS paper_reference (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    citing_paper_id      BIGINT       NOT NULL COMMENT '施引论文',
    cited_paper_id       BIGINT       NULL COMMENT '被引论文（在库内时）',
    external_cited_doi   VARCHAR(255) NULL COMMENT '被引文献不在库内时的 DOI 线索',
    created_at           TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    CONSTRAINT pk_paper_reference PRIMARY KEY (id),
    CONSTRAINT fk_paper_reference_citing FOREIGN KEY (citing_paper_id) REFERENCES paper (id) ON DELETE CASCADE,
    CONSTRAINT fk_paper_reference_cited FOREIGN KEY (cited_paper_id) REFERENCES paper (id) ON DELETE SET NULL,
    CONSTRAINT uk_paper_reference UNIQUE (citing_paper_id, cited_paper_id, external_cited_doi)
    -- 注：MySQL 不允许对参与外键 SET NULL 动作的列再加 CHECK，目标非空校验由应用层保证
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='论文引用关系';

CREATE INDEX ix_paper_reference_cited ON paper_reference (cited_paper_id, citing_paper_id);

-- ---------------------------------------------------------------
-- 4. 图同步 Outbox（MySQL -> Neo4j 投影事件）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS graph_sync_event (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    entity_type  VARCHAR(32)  NOT NULL COMMENT 'PAPER/AUTHOR/INSTITUTION/VENUE/KEYWORD',
    entity_id    BIGINT       NOT NULL COMMENT '实体业务主键（对应 MySQL 表主键 id）',
    event_type   VARCHAR(16)  NOT NULL DEFAULT 'UPSERT' COMMENT 'UPSERT/DELETE',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSED/FAILED',
    attempts     INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    last_error   VARCHAR(512) NULL COMMENT '上次失败原因',
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    processed_at TIMESTAMP(6) NULL COMMENT '处理完成时间',
    CONSTRAINT pk_graph_sync_event PRIMARY KEY (id),
    CONSTRAINT ck_graph_sync_event_type CHECK (entity_type IN ('PAPER', 'AUTHOR', 'INSTITUTION', 'VENUE', 'KEYWORD', 'RESEARCH_ENTITY')),
    CONSTRAINT ck_graph_sync_event_event CHECK (event_type IN ('UPSERT', 'DELETE')),
    CONSTRAINT ck_graph_sync_event_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_graph_sync_event_attempts CHECK (attempts >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='图同步 Outbox 事件';

CREATE INDEX ix_graph_sync_event_claim ON graph_sync_event (status, created_at, id);
CREATE INDEX ix_graph_sync_event_entity ON graph_sync_event (entity_type, entity_id, status);

-- ---------------------------------------------------------------
-- 5. LLM 实体抽取（调用 OpenAI 兼容接口从论文标题+摘要抽取实体与关系）
--    说明：PERSON/ORGANIZATION/TOPIC 抽取后按名称归并进 author/institution/keyword 表；
--          METHOD/DATASET/TOOL 无对应表，独立建 research_entity 承载；
--          extracted_entities 是六类全记账的台账，entity_relationship 的端点指向台账行。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS research_entity (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(255) NOT NULL COMMENT '实体名称',
    entity_type  VARCHAR(32)  NOT NULL COMMENT 'METHOD/DATASET/TOOL',
    version      BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    CONSTRAINT pk_research_entity PRIMARY KEY (id),
    CONSTRAINT uk_research_entity_name_type UNIQUE (name, entity_type),
    CONSTRAINT ck_research_entity_type CHECK (entity_type IN ('METHOD', 'DATASET', 'TOOL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='研究实体（方法/数据集/工具，LLM 抽取）';

CREATE INDEX ix_research_entity_name ON research_entity (name(191), id);

CREATE TABLE IF NOT EXISTS paper_research_entity (
    paper_id           BIGINT      NOT NULL COMMENT '论文 id',
    research_entity_id BIGINT      NOT NULL COMMENT '研究实体 id',
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    CONSTRAINT pk_paper_research_entity PRIMARY KEY (paper_id, research_entity_id),
    CONSTRAINT fk_paper_research_entity_paper FOREIGN KEY (paper_id) REFERENCES paper (id) ON DELETE CASCADE,
    CONSTRAINT fk_paper_research_entity_entity FOREIGN KEY (research_entity_id) REFERENCES research_entity (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='论文-研究实体关联（LLM 抽取）';

CREATE INDEX ix_paper_research_entity_entity ON paper_research_entity (research_entity_id, paper_id);

CREATE TABLE IF NOT EXISTS extracted_entities (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（台账行 id，关系表的外键端点）',
    paper_id             BIGINT       NOT NULL COMMENT '来源论文 id',
    entity_name          VARCHAR(512) NOT NULL COMMENT 'LLM 抽出的实体名称',
    entity_type          VARCHAR(32)  NOT NULL COMMENT 'PERSON/ORGANIZATION/TOPIC/METHOD/DATASET/TOOL',
    properties           JSON         NULL COMMENT 'LLM 附带属性（JSON 原文）',
    resolved_entity_type VARCHAR(32)  NULL COMMENT '解析归属：AUTHOR/INSTITUTION/KEYWORD/RESEARCH_ENTITY',
    resolved_entity_id   BIGINT       NULL COMMENT '解析归属的业务主键',
    version              BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at           TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    CONSTRAINT pk_extracted_entities PRIMARY KEY (id),
    CONSTRAINT fk_extracted_entities_paper FOREIGN KEY (paper_id) REFERENCES paper (id) ON DELETE CASCADE,
    CONSTRAINT ck_extracted_entities_type CHECK (entity_type IN ('PERSON', 'ORGANIZATION', 'TOPIC', 'METHOD', 'DATASET', 'TOOL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='LLM 抽取实体台账（六类全记账）';

CREATE INDEX ix_extracted_entities_paper ON extracted_entities (paper_id, id);
CREATE INDEX ix_extracted_entities_resolved ON extracted_entities (resolved_entity_type, resolved_entity_id);

CREATE TABLE IF NOT EXISTS entity_relationship (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    paper_id          BIGINT       NOT NULL COMMENT '来源论文 id（关系归属）',
    source_entity_id  BIGINT       NOT NULL COMMENT '源实体台账行 id',
    target_entity_id  BIGINT       NOT NULL COMMENT '目标实体台账行 id',
    relationship_type VARCHAR(32)  NOT NULL COMMENT 'USES/EXTENDS/EVALUATES_ON/APPLIED_TO/PROPOSED_BY/COMPARED_WITH',
    evidence_text     TEXT         NULL COMMENT 'LLM 给出的证据原文',
    confidence        DOUBLE       NOT NULL DEFAULT 1.0 COMMENT '置信度（LLM 未给时固定 1.0）',
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    CONSTRAINT pk_entity_relationship PRIMARY KEY (id),
    CONSTRAINT fk_entity_relationship_paper FOREIGN KEY (paper_id) REFERENCES paper (id) ON DELETE CASCADE,
    CONSTRAINT fk_entity_relationship_source FOREIGN KEY (source_entity_id) REFERENCES extracted_entities (id) ON DELETE CASCADE,
    CONSTRAINT fk_entity_relationship_target FOREIGN KEY (target_entity_id) REFERENCES extracted_entities (id) ON DELETE CASCADE,
    CONSTRAINT uk_entity_relationship UNIQUE (paper_id, source_entity_id, target_entity_id, relationship_type),
    CONSTRAINT ck_entity_relationship_type CHECK (relationship_type IN ('USES', 'EXTENDS', 'EVALUATES_ON', 'APPLIED_TO', 'PROPOSED_BY', 'COMPARED_WITH'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='LLM 抽取实体间关系（带证据）';

CREATE INDEX ix_entity_relationship_paper ON entity_relationship (paper_id, id);
