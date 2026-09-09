-- =====================================================================
-- 实体抽取功能升级脚本（对「既有 academic_graph 库」执行一次即可）
-- 用法：mysql --default-character-set=utf8mb4 -uroot -p academic_graph < docs/sql/extraction-migration.sql
-- 注意：必须带 --default-character-set=utf8mb4，否则中文注释会被 GBK 控制台编码误读而乱码
-- 要求：MySQL 8.0.19+（DROP CHECK 语法）；低于 8.0.19 见文件末尾的替代语句
-- 全新库无需本脚本：直接执行 mysql-schema.sql 即已包含以下全部内容
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------
-- 1. paper 表加抽取状态列（与 mysql-schema.sql 里的定义保持一致）
-- ---------------------------------------------------------------
ALTER TABLE paper
    ADD COLUMN extraction_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        COMMENT '实体抽取状态：PENDING/IN_PROGRESS/COMPLETED/FAILED',
    ADD CONSTRAINT ck_paper_extraction_status
        CHECK (extraction_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'));

CREATE INDEX ix_paper_extraction_status ON paper (extraction_status, id);

-- ---------------------------------------------------------------
-- 2. graph_sync_event 的实体类型枚举加入 RESEARCH_ENTITY
--    （MySQL 8.0.19+ 支持 DROP CHECK / ADD CONSTRAINT ... CHECK）
-- ---------------------------------------------------------------
ALTER TABLE graph_sync_event
    DROP CHECK ck_graph_sync_event_type,
    ADD CONSTRAINT ck_graph_sync_event_type
        CHECK (entity_type IN ('PAPER', 'AUTHOR', 'INSTITUTION', 'VENUE', 'KEYWORD', 'RESEARCH_ENTITY'));

-- ---------------------------------------------------------------
-- 3. LLM 实体抽取四张新表（与 mysql-schema.sql 第 5 节完全一致）
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

-- =====================================================================
-- 附录：MySQL 8.0.19 以下版本的替代做法（DROP CHECK 语法不可用）
-- 第 2 节改为先删掉整个约束、重建表，或按下列步骤手工操作：
--   1. SHOW CREATE TABLE graph_sync_event;    -- 找到 ck_graph_sync_event_type 的定义
--   2. 直接删表重建：由于 graph_sync_event 只存待处理事件（瞬态数据），
--      可以 DROP TABLE graph_sync_event; 然后从 mysql-schema.sql 复制该表的
--      新定义（已含 RESEARCH_ENTITY）重新 CREATE。
-- =====================================================================
