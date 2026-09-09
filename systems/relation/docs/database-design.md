# 数据库设计说明

> 参考 feature/Luo 分支（AACV 参考系统）的架构并做课程项目级简化。
> 可执行脚本：`docs/sql/mysql-schema.sql`（MySQL 8.0 建库）、`docs/sql/extraction-migration.sql`（既有库升级：抽取四表 + paper.extraction_status）、`docs/sql/neo4j-schema.cypher`（Neo4j 4.4 约束与索引）。

## 1. 设计原则

- **双存储分工**：MySQL 是业务权威数据源（用户/角色/学术实体主数据/抽取台账，JPA 管理）；Neo4j 只保存可重建的**图投影**（图谱节点与关系），供图谱可视化与图分析查询使用。
- **跨库同步（Outbox 思想）**：对 MySQL 的写操作在同一事务内往 `graph_sync_event` 表写入事件；后台任务读取 PENDING 事件，幂等投影到 Neo4j，成功后标记 PROCESSED。Neo4j 数据可随时全量重建，不承载不可再生数据。
- **并发保护**：核心实体带 `version` 乐观锁列，冲突更新返回 409。
- **审计与归属**：业务实体带 `created_by`（创建者），用于对象级权限校验；`created_at/updated_at` 全表统一。
- **编码规范**：库/表/列 utf8mb4 + `utf8mb4_0900_ai_ci`，时间统一 `TIMESTAMP(6)`（对应 Java 端 ISO 8601 UTC）。

## 2. MySQL 表清单（16 张）

### 2.1 认证与权限（3 张）

| 表 | 说明 |
|---|---|
| `sys_user` | 用户账号（密码 BCrypt 哈希存储，绝不存明文） |
| `sys_role` | 角色字典（ADMIN 管理员 / ANALYST 普通用户） |
| `sys_user_role` | 用户-角色多对多 |

`sys_user` 关键字段：`username`（唯一）、`password_hash`、`display_name`、`status`（ACTIVE/DISABLED）、`version`（乐观锁）。角色变化会使既有会话失效（登录态校验时比对）。

### 2.2 学术实体（5 张）

| 表 | 说明 |
|---|---|
| `paper` | 论文/专利等学术成果（`paper_type`：JOURNAL_ARTICLE / CONFERENCE_PAPER / PATENT / OTHER） |
| `author` | 作者 |
| `institution` | 机构（学校/研究所/公司） |
| `venue` | 发表渠道（期刊/会议） |
| `keyword` | 关键词/主题（`field_name` 存学科领域，用于主题演化分析） |

`paper` 关键字段：`title`、`doi`（唯一，可空）、`publication_date`、`publication_year`（由日期生成列，按年分析用）、`abstract_text`、`citation_count`（引用热度）、`venue_id`、`extraction_status`（LLM 抽取状态机：PENDING/IN_PROGRESS/COMPLETED/FAILED，默认 PENDING，带索引供抽取面板筛选）、`version`、`created_by`。知网导入扩展字段：`volume`（卷）、`period`（期）、`page_count`（页码）、`clc_number`（中图分类号）、`url`（原文链接），均为可空字符串（期号/页码/分类号含字母与区间，不是纯数字）。

### 2.3 关系表（3 张）

| 表 | 说明 |
|---|---|
| `paper_author` | 论文-作者署名（`author_position` 作者位次；`institution_id` 署名机构） |
| `paper_keyword` | 论文-关键词 |
| `paper_reference` | 引用关系（`citing_paper_id`→`cited_paper_id`；被引文献不在库内时用 `external_cited_doi` 保留线索——手工导入存外部 DOI，在线爬取存 OpenAlex 文献 URL） |

### 2.4 图同步（1 张）

| 表 | 说明 |
|---|---|
| `graph_sync_event` | Outbox 事件：`entity_type`（PAPER/AUTHOR/INSTITUTION/VENUE/KEYWORD/RESEARCH_ENTITY）、`entity_id`、`event_type`（UPSERT/DELETE）、`status`（PENDING/PROCESSED/FAILED）、`attempts`、`last_error` |

### 2.5 LLM 抽取台账（4 张）

| 表 | 说明 |
|---|---|
| `research_entity` | 研究实体（方法/数据集/工具）：`entity_type`（METHOD/DATASET/TOOL）+ `name` + `version`，UNIQUE(name, entity_type) 按名去重 |
| `paper_research_entity` | 论文-研究实体多对多（复合主键，两端外键 CASCADE） |
| `extracted_entities` | 抽取台账：每行一次 LLM 抽取结果（`entity_name` + 六类 `entity_type` + `properties` JSON + `resolved_entity_type`/`resolved_entity_id` 归并定位 + `version`） |
| `entity_relationship` | 抽取关系台账：`source_entity_id`/`target_entity_id` 指向台账行，`relationship_type`（六类白名单）、`evidence_text`（原文依据）、`confidence`；UNIQUE(paper_id, source, target, type) 防重 |

抽取重跑语义：先按 paper_id 删除台账两表与 `paper_research_entity` 关联再全量覆盖（`paper_author`/`paper_keyword` 追加行保留，追加位次从该论文现有最大位次 +1 递增，幂等无害）。

## 3. 表关系（ER 摘要）

```
sys_user ──< sys_user_role >── sys_role

paper >── paper_author ──< author
paper >── paper_keyword ──< keyword
paper >── paper_reference ──> paper(被引)
paper >── venue（发表渠道，可空）
paper_author.institution_id ──> institution（署名机构，可空）

paper >── extracted_entities（台账行）
paper >── entity_relationship（台账关系，端点指向 extracted_entities）
paper >── paper_research_entity ──< research_entity（方法/数据集/工具）
```

## 4. Neo4j 图模式（图投影）

### 节点（businessId = MySQL 对应表主键，保证可重建）

| 标签 | 关键属性 |
|---|---|
| `(:Paper)` | businessId, title, doi, paperType, publicationDate, publicationYear, citationCount |
| `(:Author)` | businessId, name, orcid |
| `(:Institution)` | businessId, name, countryCode |
| `(:Venue)` | businessId, name, venueType, issn |
| `(:Keyword)` | businessId, name, fieldName |
| `(:ResearchEntity)` | businessId, name, entityType（METHOD/DATASET/TOOL） |

### 关系

| 类型 | 方向 | 属性 | 来源 |
|---|---|---|---|
| `[:AUTHORED]` | Author→Paper | position（作者位次） | paper_author |
| `[:AFFILIATED_WITH]` | Author→Institution | — | paper_author.institution_id 聚合 |
| `[:PUBLISHED_IN]` | Paper→Venue | — | paper.venue_id |
| `[:HAS_KEYWORD]` | Paper→Keyword | — | paper_keyword |
| `[:CITES]` | Paper→Paper | — | paper_reference |
| `[:EXTRACTED_FROM]` | ResearchEntity/Institution→Paper | paperId（来源论文 id） | 抽取台账（已归并行） |
| `[:USES]/[:EXTENDS]/[:EVALUATES_ON]/[:APPLIED_TO]/[:PROPOSED_BY]/[:COMPARED_WITH]` | 业务实体→业务实体（Author/Institution/Keyword/ResearchEntity） | paperId, evidence, confidence | entity_relationship |

抽取边由论文投影重建时维护：先删除全部带 `paperId` 属性的边，再按台账 MERGE（标签与关系类型双白名单校验，端点未归并的跳过），因此重跑抽取不会残留旧边。

### 约束与索引（见 neo4j-schema.cypher）

- 每个节点标签 `businessId` 唯一约束（幂等投影的 MERGE 锚点；ResearchEntity 约束需先在 Neo4j Browser 执行）
- 索引：Paper(publicationYear)、Paper(doi)、Author(name)、Keyword(name)、Institution(name)、ResearchEntity(name)

## 5. 数据来源对接（在线爬取 + LLM 抽取）

### 5.1 OpenAlex 在线爬取（feature/Luo 精简移植）

`POST /api/v1/admin/crawl/openalex`（关键词搜索单页 ≤25 条，服务端指数退避重试）→ 解析 → 复用数据导入服务落库。字段映射：

| OpenAlex 字段 | 本库落点 |
|---|---|
| title/doi/publication_date/type | `paper`（type=article→JOURNAL_ARTICLE；venue 为 conference→CONFERENCE_PAPER；其余 OTHER） |
| authorships[].author.display_name/orcid | `author`（按名去重复用） |
| authorships[].institutions[0] | `institution`（按名去重复用）+ `paper_author.institution_id` |
| primary_location.source | `venue` |
| topics[].field.display_name | `keyword`（fieldName 学科领域） |
| referenced_works（OpenAlex 文献 URL） | `paper_reference.external_cited_doi`（外部线索，cited_paper_id 留空） |
| cited_by_count | `paper.citation_count` |

论文按 DOI 判重（已存在 SKIPPED），同一批数据重复爬取天然幂等。

### 5.2 LLM 实体抽取（feature/Du 移植）

抽取读 `paper.title + abstract_text` → LLM 输出六类实体与六类关系 → 台账落库 + 业务实体归并：

| LLM 实体类型 | 归并落点 |
|---|---|
| PERSON 人员 | `author`（按名复用）+ `paper_author` 追加署名 |
| ORGANIZATION 机构 | `institution`（按名复用，仅 upsert，MySQL 不建链接） |
| TOPIC 主题 | `keyword`（按名复用）+ `paper_keyword` 追加 |
| METHOD/DATASET/TOOL | `research_entity`（按 名称+类型 复用）+ `paper_research_entity` |

归并即发出对应 `graph_sync_event`，论文重建时同步抽取边到 Neo4j（见第 4 节）。

## 6. 常见分析查询（Cypher 思路）

- **合作强度**：两作者共同署名的论文数（`MATCH (a1)-[:AUTHORED]->(p)<-[:AUTHORED]-(a2)` 按对聚合）
- **引用热度**：`Paper.citationCount` 排序 + `[:CITES]` 变长路径（限制深度）看引用脉络
- **主题演化**：按 `publicationYear` 分组统计 `HAS_KEYWORD` 频次，看关键词逐年趋势
- **机构影响力**：按机构聚合论文数与被引总量（`AFFILIATED_WITH` + `AUTHORED` + `CITES`）
- **聚类分析**：Neo4j 社区版 4.4 无 GDS 插件，社区发现（连通分量/标签传播）在应用层实现（阶段1分析服务内）
- **方法/工具使用脉络**：`MATCH (m:ResearchEntity)-[:EXTRACTED_FROM]->(p:Paper)<-[:EXTRACTED_FROM]-(t:ResearchEntity)` 看哪些方法/工具被同篇论文使用，或沿 `[:USES]` 边看实体间直接关系
