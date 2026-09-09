# 学术关系知识图谱构建平台 — 系统设计

> 与实现同步（2026-09-09 对照代码核对）。代码与本文档冲突时，以验证后的当前实现为准。
> 数据库表结构详见 `docs/database-design.md`，接口契约详见 `docs/openapi.yaml`。

## 1. 系统概述

面向计算机领域学术数据（论文、作者、机构、关键词、发表渠道），提供数据管理、学术知识图谱构建、多维度学术关系分析与可视化交互的 B/S 平台。数据来源两条链路：**在线爬取 OpenAlex**（关键词搜索单页入库）与 **LLM 实体抽取**（从论文标题/摘要抽取方法/数据集/工具等六类实体与关系，归并入图谱）。

## 2. 技术栈

- 后端：Java 17、Spring Boot 3.5.16、Spring Security（Session + CSRF）、Spring Data JPA、Spring Data Neo4j 7（Neo4j 4.4）、OpenAI 兼容 LLM 客户端（RestClient）
- 存储：MySQL 8.0（权威数据源，16 张表）、Neo4j 4.4（图投影，可随时重建）
- 前端：Vue 3.5、Vue Router、Element Plus、原生 fetch、ECharts、Cytoscape
- 接口文档：springdoc-openapi（`docs/openapi.yaml` 为人工维护的契约基线）

## 3. 架构与数据一致性

- MySQL 保存用户、角色与学术实体主数据；Neo4j 保存可重建的图投影。
- **事务性 Outbox（已实现）**：业务服务写 MySQL 时在同一事务里插入 `graph_sync_event`（PENDING）；`GraphSyncService` 定时轮询（启动 15 秒后，每批间隔 5 秒，每批 20 条），按 `businessId`（= MySQL 主键）用 MERGE 幂等投影到 Neo4j。单条失败标记 FAILED 并记录原因，不阻塞后续事件；管理员可调用 `POST /api/v1/admin/sync-graph` 手动触发。MySQL 提交成功 ⇒ 事件必在，图数据因此"最终一致"且可重建。
- 受并发写影响的资源（论文/作者/机构）使用 `version` 乐观锁，冲突返回 409 CONFLICT。
- 论文投影整体重建其持有的边（署名/关键词/发表渠道/引用/抽取边），先删旧边再按 MySQL 最新数据重建——抽取边（EXTRACTED_FROM 与六类 LLM 关系边）都带 `paperId` 属性，靠它精确清理；`AFFILIATED_WITH` 边只 MERGE 不删除（作者可能历任多机构，证据链保留）。

## 4. 功能模块

1. 认证与权限：注册、登录（成功旋转 Session ID）、登出、会话恢复（`/auth/me`）；角色：ADMIN（管理员）、ANALYST（普通用户）；管理员可管理用户、调整角色、禁用账号。
2. 学术数据管理：论文、作者、机构、关键词、发表渠道五类实体的增删改查（分页、条件搜索）；论文维护署名（含排序与署名机构）、关键词（含排序）、引用（库内引用或外部 DOI）三条关系。
3. 图谱构建与同步：业务写操作自动产生 Outbox 事件 → Neo4j 图投影（节点 MERGE，边重建），支持管理端手动触发。
4. 多维分析（除合作关系边为物化派生边外，其余均在图投影上查询时即时计算）：
   - 作者合作网络：AUTHORED 共现计数（`/analytics/collaborations`）
   - 合作关系边：读取物化的 COAUTHOR_WITH（`/analytics/coauthor-edges`），供图谱直接画作者间连线
   - 引用分析：论文热度（`/analytics/citations`）
   - 主题演化：关键词按年趋势（`/analytics/topic-evolution`）
   - 机构影响力：发文量聚合（`/analytics/institution-impact`）
5. 可视化（阶段2）：Cytoscape 力导向图（≤300 节点，查询/筛选/缩放/钻取，可按实体类型过滤）+ 「合作关系」开关（作者↔作者直接连线，线宽与标签代表合著篇数）+ ECharts 图表。
6. 关系分析板块（左侧导航五个板块，围绕"某位作者"做纵深钻取，默认主角作者=姚期智；除合作关系边外全部查询时即时计算，不落库）：
   - 综合情况 `/relations/overview`：作者的学术关系知识图谱总览（后四板块的关系总和）。前端把论文全量 + `/relations/author-topics`（关键词→领域映射）组装成自我网络：作者/论文/关键词/机构四类节点 + AUTHORED/AFFILIATED/HAS_KEYWORD/COAUTHOR_WITH 边，**只收录与中心作者有直接关系的内容**（机构节点仅保留中心作者的署名机构，合作者的机构等无直接关系的内容不进图，仅作文字信息留在合作者弹窗；发表渠道不建节点/边，渠道名仅作文字信息留在论文弹窗）；点击任意节点在节点旁弹出详情卡（论文：发行时间/研究领域/渠道/被引/作者；合作者：合著篇数/机构/核心伙伴标签）；顶部统计条（论文/合作者/领域/总被引/机构）。**点击节点同时触发「聚焦过滤」**：图谱只保留该节点直接相连的关系（点作者→只显示作者与论文的署名关系；点论文→论文+署名作者+关键词；点关键词/机构→带上关联论文与作者的 AUTHORED 二级边），聚焦节点橙色描边高亮，顶部聚焦条说明当前范围并可「返回完整图谱」（刷新/切换作者自动复位）。**图例同时是「类型高亮」开关**：点击某类型（论文/作者/关键词/机构）→ **始终点亮中心作者** + 该类型的全部节点及两端都在点亮区内的线（点论文→中心+论文+署名线；点机构→中心+机构+隶属线；点作者→中心+合作者+合作线；关键词与中心隔着论文，把相连论文作桥一起点亮），**点亮的关系线打 `hl-lit` 类加粗至 3px，颜色跟随该类型节点色**（论文蓝 #2d9df5 / 关键词橙 #f5a04b / 机构紫 #a77af2，运行时按类型 bypass 覆盖线色；合作边保持原绿色粗线，其宽度仍编码合著篇数），其余点线留在原位但压到低透明度变灰（节点 0.15 / 边 0.07，Cytoscape 集合运算打 `hl-dim` 类实现），再点同一图例项恢复全彩；高亮与聚焦互斥（点图例自动退出聚焦、点节点自动取消高亮）。顶部搜索框**数据库远程检索**（`authorsApi.list`/`papersApi.list` 的 keyword 参数，作者/论文分组下拉）：选作者→以其为中心重建图谱，选论文→自动定位第一作者并把该论文弹窗打开；右侧历史面板：**最近搜索**（作者去重置顶，最多 8 条，localStorage 键 `overview.recentAuthors`）与**浏览记录**（点击过的图谱节点，按节点去重置顶，最多 10 条，键 `overview.browseHistory`），点击记录可回跳（进入该节点聚焦视图 + 重开弹窗；节点已不在当前图谱时给出中文提示），各自带「清空」按钮
   - 发行论文及时间 `/relations/timeline`：按发行时间整理的作者全部论文散点图（横轴=时间轴按年标注，纵轴=同年错行，点色=研究领域；年份区间交替底色分区、散点带同色辉光、悬停出现竖向虚线指示时间位置、点击的点白描边放大并保持选中、点按时间顺序逐个浮现，工具栏领域图例为胶囊徽章样式，详情卡领域标签与图例同色）；点击数据点弹出论文详情卡（名称/发行时间/领域/渠道/被引/作者/摘要）
   - 合作者 `/relations/coauthors`：某作者的合作者自我网络图（中心作者+合作者，线宽∝合著篇数）+ 明细表（合著篇数、合作跨度、同机构、**核心长期合作伙伴**=合著≥2篇且跨越≥2个年份、共同论文）；支持最小合著篇数过滤与"设为中心"钻取
   - 研究趋向 `/relations/fields`：全局领域分区（`/relations/field-partition`，缺失归「未分类」）+ 单作者主题画像（`/relations/author-topics`：关键词频次 + 出现年份区间 → 主题迁移时间条 + 领域归属）；默认选中作者，下拉可清空回全局视图
   - 引用影响 `/relations/citations`：论文施引数 vs 被引数排行（复用 `/analytics/citations`），默认按作者过滤"哪些论文被学术界认可"，清空后为全库排行
   - 后端支撑接口 `/relations/*`：coauthors / field-partition / author-topics / institution-authors / institution-collaborations（见 openapi.yaml）
   - 左侧导航仅这五个板块；数据管理（五实体 CRUD）与权限管理（仅 ADMIN 可见）收进顶栏快捷按钮（课程设计要求的功能入口保留）；原图谱可视化/分析总览/科研机构页路由保留但不再挂导航
   - 机构钻取页（科研机构，路由保留未挂导航）：机构内部二部图（机构→作者→论文）+ 机构间合作图 + 影响力排名
7. 权限管理页（仅 ADMIN 可见，顶栏入口）改为四个标签页：
   - 用户管理：用户列表（分页/搜索）+ 创建用户 + 修改角色（整体替换）+ 启用/禁用 + 删除
   - 数据导入：两条入库途径共用一个结果区。① 上传「论文为中心」的嵌套 JSON 文件批量入库（`POST /api/v1/admin/import/papers`，multipart，单文件上限 5MB）；② 在线爬取 OpenAlex（`POST /api/v1/admin/crawl/openalex`，关键词搜索单页 ≤25 条，需联网，服务端带指数退避重试）。两条途径共用同一套规则：作者/机构/关键词/渠道**按名称去重复用**（库里已有同名记录复用 id，没有才新建），论文按 DOI/标题判重（已存在则 SKIPPED，重复导入幂等）；**逐条隔离**——单篇失败（标题为空/类型非法/数据库冲突）只记 FAILED 不影响其他篇，全部写入同一事务并发出图同步事件（导入与手写共用一个 Outbox 投影链路，5 秒内上图）。爬取时 OpenAlex 的引用线索（referenced_works 的文献 URL）写入 `paper_reference.external_cited_doi`；OpenAlex 上游错误统一转 502 UPSTREAM_ERROR。页面提供模板下载、本地 JSON 预览（未上传即校验格式）、导入/爬取结果逐条报告（成功/跳过/失败 + 新建对象计数）与手动同步图谱按钮
   - 数据记录：复用数据管理页的五实体 CRUD（管理员可直接修改库内记录）
   - 实体抽取：LLM 实体抽取面板（`/admin/extraction/*`，详见下条）

8. LLM 实体抽取（feature/Du 移植）：
   - 触发 `POST /api/v1/admin/extraction/trigger`（paperIds 数组，缺省取最早 10 篇 PENDING）→ 异步线程池逐篇执行状态机 `paper.extraction_status` PENDING→IN_PROGRESS→COMPLETED/FAILED；前端每 2 秒轮询 `GET /status/{paperId}`，抽取完成后展开查看台账明细（`GET /result/{paperId}`）
   - 抽取内容：调用 OpenAI 兼容 LLM（gpt-4o-mini，`LLM_BASE_URL`/`LLM_API_KEY` 环境变量配置，60s 读超时，失败重试 3 次），从标题+摘要抽取六类实体（METHOD 方法 / DATASET 数据集 / TOOL 工具 / TOPIC 主题 / ORGANIZATION 机构 / PERSON 人员）与六类关系（USES/EXTENDS/EVALUATES_ON/APPLIED_TO/PROPOSED_BY/COMPARED_WITH）
   - 结果落库：台账 `extracted_entities`（六类实体行 + resolved_entity_type/id 归并定位）与 `entity_relationship`（端点回填名称、evidence 原文依据、confidence）；同时归并业务实体——PERSON→author（paper_author 追加署名）、ORGANIZATION→institution（仅 upsert 不建链接）、TOPIC→keyword（paper_keyword 追加）、METHOD/DATASET/TOOL→research_entity + paper_research_entity；重跑先清台账/关系/research 关联再全量覆盖（paper_author/paper_keyword 追加行保留，幂等无害）
   - 无摘要论文直接 FAILED 不调用 LLM；未配置 LLM key 时任务走失败路径（状态可见）
   - 图谱投影：`research_entity` 发 RESEARCH_ENTITY 事件 → `(:ResearchEntity)` 节点；论文重建时附带清理/重建抽取边——`(:ResearchEntity|:Institution)-[:EXTRACTED_FROM {paperId}]->(:Paper)` 证据边与六类 LLM 关系边（标签/关系类型双白名单，逐条 MERGE 带 evidence/confidence）
   - 只读图谱数据 `GET /api/v1/extraction/graph-data`（登录即可，paperIds 可选）：GraphView 叠加展示研究实体节点（#57bcff）+ EXTRACTED_FROM 亮蓝虚线 + LLM 关系粉红虚线（带类型标签）

9. GraphView 精选功能（feature/Luo 可视化移植 + 抽取展示）：
   - 数据层：主图谱（论文/作者/关键词/机构 + 引用）+ 抽取图谱数据并行加载（失败降级不阻塞）；新增「研究实体」类型过滤与对应节点/边样式
   - 双击两跳展开：双击节点 BFS 展开两跳邻域（含合作关系边），超 300 节点截断提示，工具栏「退出聚焦」按钮，双击空白退出；聚焦时可见集=聚焦集∩类型过滤
   - 节点详情抽屉（el-drawer）：论文（类型/年份/被引/DOI/渠道 + 作者/关键词/引用/抽取实体邻居分组可点聚焦）、作者（所属机构 + 合著者分组）、研究实体（方法/数据集/工具类别 + 来源论文）
   - 保存查询：localStorage（键 ag.graph.savedQueries，手写结构校验），一键回填重查、可删除；关键词输入 200ms 防抖 + AbortController 取消旧请求

## 5. 数据模型

- MySQL 16 张表：`sys_user`、`sys_role`、`sys_user_role`、`paper`、`author`、`institution`、`keyword`、`venue`、`paper_author`、`paper_keyword`、`paper_reference`、`graph_sync_event`（Outbox）、`research_entity`、`paper_research_entity`、`extracted_entities`、`entity_relationship`（抽取台账）。列级注释 100% 覆盖，DDL 见 `docs/sql/mysql-schema.sql`；既有库升级见 `docs/sql/extraction-migration.sql`。
- 图模式（Neo4j）：
  - 节点：`(:Author)`、`(:Paper)`、`(:Institution)`、`(:Keyword)`、`(:Venue)`、`(:ResearchEntity)`（每个节点 `businessId` 唯一约束 = MySQL 主键）
  - 关系：`(:Author)-[:AUTHORED]->(:Paper)`（含 position 属性）、`(:Author)-[:AFFILIATED_WITH]->(:Institution)`、`(:Paper)-[:PUBLISHED_IN]->(:Venue)`、`(:Paper)-[:HAS_KEYWORD]->(:Keyword)`、`(:Paper)-[:CITES]->(:Paper)`
  - 抽取关系：`(:ResearchEntity)-[:EXTRACTED_FROM {paperId}]->(:Paper)`、`(:Institution)-[:EXTRACTED_FROM {paperId}]->(:Paper)`（抽取证据边），以及六类 LLM 关系边（USES/EXTENDS/EVALUATES_ON/APPLIED_TO/PROPOSED_BY/COMPARED_WITH，端点落在 Author/Institution/Keyword/ResearchEntity 节点上，边带 paperId/evidence/confidence 属性，标签与关系类型双白名单校验）。论文投影重建时先删除该论文全部带 paperId 属性的边再重建，重跑抽取不会残留旧边
  - 派生关系：`(:Author)-[:COAUTHOR_WITH {weight, computedAt}]->(:Author)`，由 `AUTHORED` 共现物化而来；方向固定为 `businessId` 小 → 大，保证每对作者只有一条边。重算方式为**全量重算**（先删除已无共现证据的旧边，再 MERGE + SET），而非增量累加——增量在论文删除/署名变更时容易产生权重漂移，全量重算天然幂等。触发时机：批次中含论文事件、或管理员手动调用 `POST /admin/sync-graph`。
  - 除 `COAUTHOR_WITH` 外的分析结果仍在查询时即时计算，不落盘中间结果。

## 6. 安全设计

- HttpOnly `JSESSIONID` Cookie（Spring 默认，SameSite=Lax），登录成功后旋转 Session ID。
- 密码只存 BCrypt 哈希；账号禁用（DISABLED）后无法登录。
- 非安全方法必须携带 `X-CSRF-TOKEN` 头（Token 经 `GET /auth/csrf` 获取，仅存前端内存）；登录/注册接口豁免 CSRF。
- 后端为最终权限边界：`SecurityConfig` 按路径授权（`/admin/**` 仅 ADMIN），业务层支持 `@PreAuthorize` 细粒度控制。当前不做对象级所有权限制（见 `docs/authorization-matrix.md`）。
- 错误响应：`application/problem+json`（status/title/detail/instance/errorCode/traceId/fieldErrors），不泄露堆栈/SQL/Cypher/口令。

## 7. API 契约

- 基础路径 `/api/v1`；成功响应直接返回 DTO 或分页对象，无通用包装。
- 分页：`{items,page,size,totalElements,totalPages}`，`page` 从 0 起，`size` 默认 20 最大 100。
- 全部接口清单、请求/响应结构、错误码见 `docs/openapi.yaml`；权限边界见 `docs/authorization-matrix.md`。
