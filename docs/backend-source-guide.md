# 学术成果信息采集及可视化系统：后端目录与文件职责说明

> 本文依据当前工作区源码整理，说明目标包内每个实际存在的目录和 Java 文件。文件存在、代码可读与运行版本已生效是不同证据；本文不作为构建、数据库或线上功能验收结果。

| 项目 | 内容 |
| --- | --- |
| 核对时间 | 2026-09-13 10:46:11 +08:00 |
| 目标 Java 包 | `com.aacv.system` |
| 源码根目录 | `E:\Program\Java\course_design\XSGXZSTP-KS\systems\crawler\backend\src\main\java\com\aacv\system` |
| 文件数量 | 247 个 Java 文件 |
| 子目录数量 | 82 个，均含文件或下级有效目录；不含源码根目录本身 |
| 顶层模块数量 | 13 个；另有根目录启动类 1 个 |
| 说明范围 | 目标包中的全部目录与 Java 文件；不逐项展开资源文件、测试、前端和部署脚本 |

本文中的源码链接相对于本文件所在的 `docs` 目录，随仓库一起移动仍可定位；目录树中的名称相对于上表的源码根目录。

**目录**

- [1. 当前结构与分层](#structure)
- [2. 主要业务调用链](#flows)
- [3. 完整目录树](#tree)
- [4. 逐目录、逐文件职责](#inventory)
  - [4.1 authorimport：作者文件导入](#module-authorimport)
  - [4.2 catalog：成果检索与实体编目](#module-catalog)
  - [4.3 graph：图谱查询、投影与维护](#module-graph)
  - [4.4 analytics：统计分析](#module-analytics)
  - [4.5 export：成果导出](#module-export)
  - [4.6 identity：账号、认证与权限](#module-identity)
  - [4.7 operations：运行状态、告警与审计](#module-operations)
  - [4.8 infrastructure：公共技术配置](#module-infrastructure)
  - [4.9 shared：公共模型与 Web 支持](#module-shared)
  - [4.10 ingestion：历史原始数据到期清理](#module-ingestion)
  - [4.11 source：来源与学术元数据模型](#module-source)
  - [4.12 crawl：历史采集调度兼容](#module-crawl)
  - [4.13 authororcid：历史 ORCID 调度兼容](#module-authororcid)
- [5. 源码阅读顺序](#reading)
- [6. 维护、核对与适用边界](#verification)

<a id="structure"></a>

## 1. 当前结构与分层

后端以业务模块分包，同一模块通常包含 HTTP 接口、业务用例、领域模型和技术适配。MySQL 保存权威业务记录，Neo4j 保存可重建的图投影。当前新增成果的主要入口为知网文件导入。

| 顶层模块 | 当前职责 | Java 文件数 |
| --- | --- | ---: |
| [authorimport](#module-authorimport) | 作者文件导入 | 13 |
| [catalog](#module-catalog) | 成果检索与实体编目 | 17 |
| [graph](#module-graph) | 图谱查询、投影与维护 | 54 |
| [analytics](#module-analytics) | 统计分析 | 20 |
| [export](#module-export) | 成果导出 | 29 |
| [identity](#module-identity) | 账号、认证与权限 | 46 |
| [operations](#module-operations) | 运行状态、告警与审计 | 41 |
| [infrastructure](#module-infrastructure) | 公共技术配置 | 5 |
| [shared](#module-shared) | 公共模型与 Web 支持 | 9 |
| [ingestion](#module-ingestion) | 历史原始数据到期清理 | 6 |
| [source](#module-source) | 来源与学术元数据模型 | 2 |
| [crawl](#module-crawl) | 历史采集调度兼容 | 2 |
| [authororcid](#module-authororcid) | 历史 ORCID 调度兼容 | 2 |
| 根目录 | Spring Boot 启动入口 | 1 |

**常用分层及名称含义**

| 目录或名称 | 作用 | 阅读时的区分 |
| --- | --- | --- |
| `api` | Controller、请求及响应模型 | 面向 HTTP 协议；通常调用应用服务 |
| `application` | 业务用例、流程编排和权限检查 | 组织事务、状态变化及协作关系 |
| `application/port` | 业务依赖的接口约定 | `port` 是架构接口，不是网络端口 |
| `domain` | 领域记录、枚举、值对象及校验规则 | 描述业务含义，不等于数据库表结构 |
| `infrastructure` | 数据库、文件、框架及外部系统适配 | 顶层是公共设施；模块内部是本模块的技术实现 |
| `persistence` | MyBatis Mapper、Row 和 Repository 实现 | Mapper 声明查询，Row 接收结果，Repository 负责转换与组合 |
| `batch` | Spring Batch 作业及启动设施 | 当前用于图谱维护，旧采集批处理已退役 |
| `quartz` | Quartz Job、触发器及调度配置 | 区分现用定时任务与仅自清理的旧 Job 类名 |
| `config` | 组件注册及配置属性绑定 | `Configuration` 负责装配，`Properties` 负责参数与校验 |
| `security` / `web` | 身份适配、过滤器、请求上下文及错误响应 | 支撑业务调用的认证、审计和追踪 |
| `Request` / `Response` | 接口输入与输出结构 | 不能把响应对象当成数据库实体 |
| `Row` / `Mapper` / `MyBatis*Repository` | 数据库结果、SQL 操作接口、存储实现 | 具体 SQL 通常还需查看资源目录中的映射文件 |

当前目录中已没有 `governance`、`quality` 模块，也没有旧来源网络适配器和采集业务服务。`source`、`crawl`、`ingestion`、`authororcid` 的保留用途在下文单独说明，不按旧名称推断仍提供完整采集能力。

<a id="flows"></a>

## 2. 主要业务调用链

### 2.1 文件导入与图谱同步

```text
知网 XLSX / XLS / CSV
  → AuthorImportController
  → ScholarImportParser（单表）/ ScholarBundleParser（同批多文件）
  → 预览字段、问题行与共同作者候选
  → AuthorImportService 确认预览和身份
  → 同一 MySQL 事务：
      AuthorImportMapper 写入成果、作者、导师、机构、原始证据及批次
      MyBatisGraphProjectionRequestAdapter 写入目标版本与 Outbox 事件
  → 事务提交
  → GraphOutboxQuartzJob 定时触发 GraphOutboxProcessor
  → GraphOutboxService 认领事件并维护租约
  → Neo4jGraphProjectionWriter 读取 MySQL 完整快照
  → Neo4jProjectionTransaction 写入图投影
  → 登记成功，或进入有界重试、死信处理
```

本人署名、导师指导、成果机构分别保存为有证据的关系。指导不等于共同署名，机构列表不直接证明作者当前任职。刚提交的编目和统计数据可能早于 Neo4j 投影更新。

### 2.2 检索、统计与图查询

| 场景 | 调用路径 | 主要数据来源 |
| --- | --- | --- |
| 成果和实体编目 | `CatalogController → CatalogService → MyBatisCatalogRepository → CatalogMapper` | MySQL |
| 概览、趋势与合作统计 | `AnalyticsController → AnalyticsService → MyBatisAnalyticsRepository → AnalyticsMapper` | MySQL |
| 作者图、子图和路径 | `GraphController → GraphQueryService → Neo4j`，由 `GraphPresentationService` 附加类型配置和派生合作边 | Neo4j 拓扑及 MySQL 类型配置 |
| 图回填、对账和重建 | `GraphMaintenanceController → GraphMaintenanceService → SpringBatchGraphMaintenanceLauncher`，分页处理后请求投影刷新 | MySQL 成果与维护状态、Neo4j 投影 |

图查询中的合作证据受本次查询范围和分页限制。`GraphPresentationService` 生成的 `COAUTHORED` 边用于展示，不写回 Neo4j。

### 2.3 导出、认证与审计

```text
导出：ExportController → ExportService → ExportTaskDispatcher
      → ExportTaskProcessor → LocalExportFileStore → ExportTaskFinalizer
      → 用户查询任务状态并下载有效结果

认证：AuthController → Spring Security → DatabaseUserDetailsService
      → UserAccountRepository → UserPrincipal / 数据库会话

请求：TraceIdFilter → 安全过滤器链与 AccountFreshnessFilter
      → 业务 Controller / Service
      → AuditService 记录业务审计
      → 异常由 ApiExceptionHandler 或 ProblemResponseWriter 统一响应
```

<a id="tree"></a>

## 3. 完整目录树

下列树覆盖当前全部 82 个子目录。每个目录的具体职责和其下每个 Java 文件的说明见第 4 节。

```text
com/aacv/system/
├─ AacvSystemApplication.java
├─ analytics/  统计分析（20 文件）
│  ├─ api/
│  ├─ application/
│  │  └─ port/
│  ├─ domain/
│  └─ infrastructure/
│     └─ persistence/
├─ authorimport/  作者文件导入（13 文件）
│  ├─ api/
│  ├─ application/
│  ├─ domain/
│  └─ infrastructure/
├─ authororcid/  历史 ORCID 调度兼容（2 文件）
│  └─ infrastructure/
├─ catalog/  成果检索与实体编目（17 文件）
│  ├─ api/
│  ├─ application/
│  │  └─ port/
│  ├─ domain/
│  └─ infrastructure/
│     └─ persistence/
├─ crawl/  历史采集调度兼容（2 文件）
│  └─ infrastructure/
│     └─ quartz/
├─ export/  成果导出（29 文件）
│  ├─ api/
│  ├─ application/
│  │  └─ port/
│  ├─ domain/
│  └─ infrastructure/
│     ├─ async/
│     ├─ config/
│     ├─ file/
│     ├─ persistence/
│     └─ security/
├─ graph/  图谱查询、投影与维护（54 文件）
│  ├─ api/
│  ├─ application/
│  │  └─ port/
│  ├─ domain/
│  └─ infrastructure/
│     ├─ batch/
│     ├─ neo4j/
│     ├─ persistence/
│     └─ quartz/
├─ identity/  账号、认证与权限（46 文件）
│  ├─ api/
│  ├─ application/
│  │  └─ port/
│  ├─ domain/
│  └─ infrastructure/
│     ├─ config/
│     ├─ persistence/
│     └─ security/
├─ infrastructure/  公共技术配置（5 文件）
│  ├─ batch/
│  ├─ database/
│  └─ quartz/
├─ ingestion/  历史原始数据到期清理（6 文件）
│  ├─ application/
│  │  └─ port/
│  └─ infrastructure/
│     ├─ persistence/
│     └─ retention/
├─ operations/  运行状态、告警与审计（41 文件）
│  ├─ api/
│  ├─ application/
│  │  └─ port/
│  ├─ domain/
│  └─ infrastructure/
│     ├─ config/
│     ├─ persistence/
│     ├─ quartz/
│     ├─ security/
│     └─ web/
├─ shared/  公共模型与 Web 支持（9 文件）
│  ├─ application/
│  ├─ domain/
│  └─ infrastructure/
│     └─ web/
└─ source/  来源与学术元数据模型（2 文件）
   └─ domain/
```

<a id="inventory"></a>

## 4. 逐目录、逐文件职责

**根目录启动文件**

| 文件 | 具体作用 |
| --- | --- |
| [AacvSystemApplication.java](../systems/crawler/backend/src/main/java/com/aacv/system/AacvSystemApplication.java) | Spring Boot 启动入口，创建应用上下文并扫描本包及子包组件。 |

<a id="module-authorimport"></a>

### 4.1 authorimport：作者文件导入

接收知网信息表，解析并预览成果，确认学者身份后写入 MySQL，保存署名、指导、成果机构及原始证据。

当前共 **13 个 Java 文件、5 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [authorimport/](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport) | 接收知网信息表，解析并预览成果，确认学者身份后写入 MySQL，保存署名、指导、成果机构及原始证据。 |
| [authorimport/api/](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/api) | HTTP 接口与请求、响应模型 |
| [authorimport/application/](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/application) | 业务用例处理与流程编排 |
| [authorimport/domain/](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain) | 业务数据模型、枚举与规则 |
| [authorimport/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/infrastructure) | 数据库、框架及外部系统适配 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [api/AuthorImportController.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/api/AuthorImportController.java) | 提供单文件与多文件预览、确认导入、近期批次及导入证据接口。 |
| [application/AuthorImportService.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/application/AuthorImportService.java) | 在事务中处理学者身份复用、成果去重、署名与指导关系，保存原始证据、批次、审计并请求图谱同步。 |
| [application/ScholarBundleParser.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/application/ScholarBundleParser.java) | 解析同批多文件，识别共同作者候选，区分本人署名与硕博指导模式，生成整批预览。 |
| [application/ScholarImportParser.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/application/ScholarImportParser.java) | 识别单表表头和字段映射，解析成果类型、作者、日期等字段，收集问题行并生成预览校验标识。 |
| [domain/ImportBundle.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportBundle.java) | 集中定义多文件设置、整批选项、文件预览、整批预览与导入汇总记录。 |
| [domain/ImportOptions.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportOptions.java) | 表示单文件导入的学者、机构、作者编号、工作表、表头、字段映射和关系模式。 |
| [domain/ImportPreview.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportPreview.java) | 承载工作表、表头、映射、预览行、错误警告及预览校验标识。 |
| [domain/ImportRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportRow.java) | 表示一条已解析成果，包含作者、机构、关键词、原始列值、错误与警告。 |
| [domain/ImportSummary.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/domain/ImportSummary.java) | 表示一次导入批次的学者编号、文件、模式、数量统计和创建时间。 |
| [infrastructure/AuthorImportMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/infrastructure/AuthorImportMapper.java) | 声明导入批次、作者、成果、署名、导师、机构、关键词与原始证据的数据库读写。 |
| [infrastructure/ImportId.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/infrastructure/ImportId.java) | 接收 MyBatis 插入记录后回填的数据库主键。 |
| [infrastructure/ImportUploadConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/infrastructure/ImportUploadConfiguration.java) | 配置 multipart 上传的单文件与请求体大小上限。 |
| [infrastructure/ScholarTableReader.java](../systems/crawler/backend/src/main/java/com/aacv/system/authorimport/infrastructure/ScholarTableReader.java) | 读取 XLSX、XLS、CSV，兼容知网 HTML 格式的 XLS，并限制文件大小、行列数和公式输入。 |

**使用边界：** 当前新增学者资料的主要入口；导入不访问知网网络。导师关系不等于署名关系，成果机构不直接证明当前任职。

<a id="module-catalog"></a>

### 4.2 catalog：成果检索与实体编目

查询成果列表与详情，提供作者、机构、期刊、主题、专利和指导硕博编目，以及实体和导入来源证据。

当前共 **17 个 Java 文件、7 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [catalog/](../systems/crawler/backend/src/main/java/com/aacv/system/catalog) | 查询成果列表与详情，提供作者、机构、期刊、主题、专利和指导硕博编目，以及实体和导入来源证据。 |
| [catalog/api/](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/api) | HTTP 接口与请求、响应模型 |
| [catalog/application/](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/application) | 业务用例处理与流程编排 |
| [catalog/application/port/](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/application/port) | 业务依赖的接口约定 |
| [catalog/domain/](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/domain) | 业务数据模型、枚举与规则 |
| [catalog/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/infrastructure) | 数据库、框架及外部系统适配 |
| [catalog/infrastructure/persistence/](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/infrastructure/persistence) | MyBatis 数据访问和结果映射 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [api/AchievementDetailResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/api/AchievementDetailResponse.java) | 返回成果详情，包含摘要、署名机构、参考成果、来源记录及字段来源状态。 |
| [api/AchievementPageResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/api/AchievementPageResponse.java) | 封装成果列表及分页元数据。 |
| [api/AchievementSummaryResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/api/AchievementSummaryResponse.java) | 返回成果列表项的编号、题名、DOI、类型、日期、载体、作者和主题。 |
| [api/CatalogController.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/api/CatalogController.java) | 提供成果列表/详情、实体编目、关联成果以及作者和机构证据查询。 |
| [api/CatalogEntityPageResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/api/CatalogEntityPageResponse.java) | 返回作者、机构、期刊、主题、专利、硕博论文等编目项及分页，含适用的导师姓名。 |
| [application/CatalogService.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/application/CatalogService.java) | 检查编目读取权限，组织查询并处理实体或成果不存在的情况。 |
| [application/port/CatalogRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/application/port/CatalogRepository.java) | 定义成果列表、详情、实体编目及实体证据查询接口。 |
| [domain/AchievementCatalogDetail.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/domain/AchievementCatalogDetail.java) | 成果详情的内部模型，包含署名、机构、引用、来源追踪和字段状态。 |
| [domain/AchievementCatalogItem.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/domain/AchievementCatalogItem.java) | 成果列表项的内部模型，供查询结果与接口响应转换使用。 |
| [domain/CatalogEntityEvidence.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/domain/CatalogEntityEvidence.java) | 表示机构名称来源、作者关联机构及发表年份范围，并标记证据是否截断。 |
| [domain/CatalogEntityItem.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/domain/CatalogEntityItem.java) | 表示编目实体的编号、外部标识、名称、类型、成果数及导师姓名。 |
| [domain/CatalogEntityKind.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/domain/CatalogEntityKind.java) | 定义可查询的编目类别，包括作者、机构、期刊、主题及指定成果类别。 |
| [domain/CatalogQuery.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/domain/CatalogQuery.java) | 封装并校验成果检索字段、实体编号、发表年份和分页参数。 |
| [infrastructure/persistence/CatalogEvidenceRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/infrastructure/persistence/CatalogEvidenceRow.java) | 承接名称来源、观察时间和作者机构关联统计的 SQL 查询结果。 |
| [infrastructure/persistence/CatalogMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/infrastructure/persistence/CatalogMapper.java) | 声明成果、实体、导师、来源证据与关联成果的 MyBatis 查询。 |
| [infrastructure/persistence/CatalogRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/infrastructure/persistence/CatalogRow.java) | 承接编目 SQL 的成果、作者、机构、来源追踪和字段状态数据。 |
| [infrastructure/persistence/MyBatisCatalogRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/catalog/infrastructure/persistence/MyBatisCatalogRepository.java) | 组合查询结果，解析元数据并组装成果详情、编目项、导师名单与证据。 |

**使用边界：** 导师姓名来自 achievement_advisor，经规范身份归并后去重；不从作者名单推断导师。

<a id="module-graph"></a>

### 4.3 graph：图谱查询、投影与维护

读取 Neo4j 图谱，消费 MySQL Outbox 更新投影，执行图回填、对账与重建，并管理图类型显示配置。

当前共 **54 个 Java 文件、10 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [graph/](../systems/crawler/backend/src/main/java/com/aacv/system/graph) | 读取 Neo4j 图谱，消费 MySQL Outbox 更新投影，执行图回填、对账与重建，并管理图类型显示配置。 |
| [graph/api/](../systems/crawler/backend/src/main/java/com/aacv/system/graph/api) | HTTP 接口与请求、响应模型 |
| [graph/application/](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application) | 业务用例处理与流程编排 |
| [graph/application/port/](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/port) | 业务依赖的接口约定 |
| [graph/domain/](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain) | 业务数据模型、枚举与规则 |
| [graph/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure) | 数据库、框架及外部系统适配 |
| [graph/infrastructure/batch/](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/batch) | Spring Batch 批任务执行 |
| [graph/infrastructure/neo4j/](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/neo4j) | Neo4j 模式初始化、投影事务和一致性检查 |
| [graph/infrastructure/persistence/](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence) | MyBatis 数据访问和结果映射 |
| [graph/infrastructure/quartz/](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/quartz) | Quartz 定时任务及调度装配 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [api/GraphController.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/api/GraphController.java) | 提供全局概览、子图、路径和以作者为中心的分页图谱接口。 |
| [api/GraphMaintenanceController.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/api/GraphMaintenanceController.java) | 提供图谱回填、对账、重建及维护任务查询入口。 |
| [api/GraphOperationsController.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/api/GraphOperationsController.java) | 提供同步状态、图事件列表及失败事件重放接口。 |
| [api/GraphTypeController.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/api/GraphTypeController.java) | 提供图节点和关系类型的显示名称、颜色、尺寸及审核状态配置接口。 |
| [application/GraphMaintenancePageService.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphMaintenancePageService.java) | 按成果编号每页处理 100 项，对比投影或强制刷新，提交同步事件并更新游标。 |
| [application/GraphMaintenanceService.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphMaintenanceService.java) | 创建与执行图维护任务，控制回填、对账、重建流程及互斥状态。 |
| [application/GraphMaintenanceStateService.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphMaintenanceStateService.java) | 读取维护游标，以独立事务更新运行中、成功或失败状态。 |
| [application/GraphOperationsService.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphOperationsService.java) | 汇总图谱可用性、同步积压和事件信息，控制并审计事件重放。 |
| [application/GraphOutboxProcessor.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphOutboxProcessor.java) | 批量认领 Outbox 事件，调用图写入器，再登记成功或失败。 |
| [application/GraphOutboxService.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphOutboxService.java) | 管理事件租约、认领、重试退避、成功确认、死信及重放。 |
| [application/GraphPresentationService.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphPresentationService.java) | 附加图类型显示配置，并按当前结果中的真实共同署名派生合作边，不写回 Neo4j。 |
| [application/GraphQueryService.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphQueryService.java) | 执行 Neo4j 概览、子图、路径和作者图谱查询，限制规模与超时，组装节点、边及逐篇机构。 |
| [application/GraphQueryTimeoutException.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphQueryTimeoutException.java) | 表示图查询超过允许执行时间。 |
| [application/GraphRebuildInProgressException.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphRebuildInProgressException.java) | 表示图谱正在重建，当前查询受到限制。 |
| [application/GraphTypeService.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphTypeService.java) | 校验并读写图类型样式及审核状态，处理版本冲突并记录审计。 |
| [application/GraphUnavailableException.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphUnavailableException.java) | 表示 Neo4j 或图查询服务不可用。 |
| [application/port/GraphMaintenanceLaunchPort.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/port/GraphMaintenanceLaunchPort.java) | 定义数据库事务提交后启动图维护任务的接口。 |
| [application/port/GraphProjectionRequestPort.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/port/GraphProjectionRequestPort.java) | 定义为成果或关联实体请求图投影更新的接口。 |
| [application/port/GraphProjectionWriter.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/application/port/GraphProjectionWriter.java) | 定义将指定成果及版本写入图数据库的接口。 |
| [domain/AuthorGraphView.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/AuthorGraphView.java) | 为作者中心图附带成果分页信息，并定义论文、专利、硕博等查询类别。 |
| [domain/GraphAchievementSnapshot.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphAchievementSnapshot.java) | 汇总构建成果投影所需的作者、导师、机构、期刊、主题、引用和摘要快照。 |
| [domain/GraphAggregateType.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphAggregateType.java) | 定义图事件关联的聚合类型，当前为成果。 |
| [domain/GraphEventType.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphEventType.java) | 定义图同步事件类型，当前用于成果投影刷新。 |
| [domain/GraphEventView.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphEventView.java) | 表示运维页面读取的图事件，含重试次数、错误、重放来源及时间。 |
| [domain/GraphMaintenanceRun.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphMaintenanceRun.java) | 表示一次图维护的类型、状态、游标、扫描和修复计数及错误。 |
| [domain/GraphMaintenanceStatus.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphMaintenanceStatus.java) | 定义图维护任务的生命周期状态。 |
| [domain/GraphMaintenanceType.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphMaintenanceType.java) | 定义回填、对账和重建三类图维护操作。 |
| [domain/GraphNodeType.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphNodeType.java) | 定义成果、作者、机构、期刊载体和主题节点类型。 |
| [domain/GraphOutboxEvent.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphOutboxEvent.java) | 表示消费者处理事件所需的事件编号、成果编号、目标版本及尝试次数。 |
| [domain/GraphOutboxStatus.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphOutboxStatus.java) | 定义 Outbox 等待、处理中、成功和死信状态。 |
| [domain/GraphRelationshipType.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphRelationshipType.java) | 定义持久化关系，包括署名、指导、成果机构、隶属、发表、主题和引用。 |
| [domain/GraphSyncStatus.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphSyncStatus.java) | 描述 Neo4j 可用性、模式版本、事件积压、同步延迟与重建状态。 |
| [domain/GraphTypeDefinition.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphTypeDefinition.java) | 描述节点或关系类型的名称、颜色、尺寸、审核状态及版本。 |
| [domain/GraphView.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/GraphView.java) | 统一图查询结果，包含节点、边、根节点、规模限制、截断说明与同步信息。 |
| [infrastructure/batch/GraphMaintenanceBatchConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/batch/GraphMaintenanceBatchConfiguration.java) | 配置图维护 Spring Batch Job 与 Step，调用维护业务流程。 |
| [infrastructure/batch/SpringBatchGraphMaintenanceLauncher.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/batch/SpringBatchGraphMaintenanceLauncher.java) | 在业务事务提交后通过公共 batchJobOperator 启动图维护，记录启动失败。 |
| [infrastructure/neo4j/GraphSchemaInitializer.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/neo4j/GraphSchemaInitializer.java) | 加载 Neo4j 模式脚本，初始化约束并维护模式就绪状态。 |
| [infrastructure/neo4j/GraphSchemaState.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/neo4j/GraphSchemaState.java) | 保存图模式是否就绪，并作为健康检查指标暴露。 |
| [infrastructure/neo4j/Neo4jGraphProjectionWriter.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/neo4j/Neo4jGraphProjectionWriter.java) | 检查模式就绪，读取 MySQL 成果快照，调用 Neo4j 事务写入。 |
| [infrastructure/neo4j/Neo4jProjectionInspector.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/neo4j/Neo4jProjectionInspector.java) | 对比投影版本与关系数量；重建时可清除本系统托管的图节点。 |
| [infrastructure/neo4j/Neo4jProjectionTransaction.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/neo4j/Neo4jProjectionTransaction.java) | 在 Neo4j 事务内更新成果节点及其托管关系，记录投影版本。 |
| [infrastructure/neo4j/Neo4jTransactionConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/neo4j/Neo4jTransactionConfiguration.java) | 配置 Neo4j 专用事务管理器。 |
| [infrastructure/persistence/GraphMaintenanceMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphMaintenanceMapper.java) | 声明维护任务、成果扫描、进度更新与状态迁移的数据库操作。 |
| [infrastructure/persistence/GraphMaintenanceRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphMaintenanceRow.java) | 承接图维护任务表字段。 |
| [infrastructure/persistence/GraphOperationsMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphOperationsMapper.java) | 查询事件列表、状态数量、最早积压时间及重建状态。 |
| [infrastructure/persistence/GraphOutboxMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphOutboxMapper.java) | 执行事件认领、租约恢复、成功确认、重试、死信与重放关联的 SQL 操作。 |
| [infrastructure/persistence/GraphProjectionRequestMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphProjectionRequestMapper.java) | 推进成果目标投影版本、查关联成果并插入 Outbox 事件。 |
| [infrastructure/persistence/GraphSnapshotMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphSnapshotMapper.java) | 从 MySQL 查询规范成果及其作者、导师、机构、主题和引用数据。 |
| [infrastructure/persistence/GraphSnapshotRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphSnapshotRow.java) | 承接成果基础字段及发表载体的投影快照查询结果。 |
| [infrastructure/persistence/GraphTypeMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphTypeMapper.java) | 查询并按版本更新图节点和关系类型配置。 |
| [infrastructure/persistence/MyBatisGraphProjectionRequestAdapter.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/MyBatisGraphProjectionRequestAdapter.java) | 在 MySQL 事务内推进投影版本并创建 Outbox 事件，支持关联成果刷新。 |
| [infrastructure/persistence/MyBatisGraphSnapshotReader.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/MyBatisGraphSnapshotReader.java) | 组装用于 Neo4j 写入的完整成果快照。 |
| [infrastructure/quartz/GraphOutboxQuartzJob.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/quartz/GraphOutboxQuartzJob.java) | 定时触发一批 Outbox 消费，并尝试确保图模式就绪。 |
| [infrastructure/quartz/GraphOutboxQuartzSchedule.java](../systems/crawler/backend/src/main/java/com/aacv/system/graph/infrastructure/quartz/GraphOutboxQuartzSchedule.java) | 在启用条件满足时注册每 10 秒执行的 Outbox 消费计划。 |

**使用边界：** MySQL 是业务数据来源；Neo4j 为异步投影。COAUTHORED 从本次查询的真实共同署名派生，不写回 Neo4j，也不代表全库合作次数。

<a id="module-analytics"></a>

### 4.4 analytics：统计分析

从 MySQL 汇总实体数量、年度趋势、类型分布、字段覆盖及作者和机构合作数据。

当前共 **20 个 Java 文件、7 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [analytics/](../systems/crawler/backend/src/main/java/com/aacv/system/analytics) | 从 MySQL 汇总实体数量、年度趋势、类型分布、字段覆盖及作者和机构合作数据。 |
| [analytics/api/](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/api) | HTTP 接口与请求、响应模型 |
| [analytics/application/](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/application) | 业务用例处理与流程编排 |
| [analytics/application/port/](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/application/port) | 业务依赖的接口约定 |
| [analytics/domain/](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain) | 业务数据模型、枚举与规则 |
| [analytics/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/infrastructure) | 数据库、框架及外部系统适配 |
| [analytics/infrastructure/persistence/](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/infrastructure/persistence) | MyBatis 数据访问和结果映射 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [api/AnalyticsCollaborationResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/api/AnalyticsCollaborationResponse.java) | 返回作者合作对、机构合作对及统计范围。 |
| [api/AnalyticsController.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/api/AnalyticsController.java) | 提供统计概览、年度趋势、分类分布和合作统计接口。 |
| [api/AnalyticsDistributionResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/api/AnalyticsDistributionResponse.java) | 返回成果类型、来源、机构和主题的分布统计。 |
| [api/AnalyticsOverviewResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/api/AnalyticsOverviewResponse.java) | 返回成果、作者、机构、来源数量及元数据覆盖情况。 |
| [api/AnalyticsScopeResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/api/AnalyticsScopeResponse.java) | 说明统计的数据来源与本次生效的筛选条件。 |
| [api/AnalyticsTrendResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/api/AnalyticsTrendResponse.java) | 返回按发表年份汇总的成果趋势与统计范围。 |
| [application/AnalyticsService.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/application/AnalyticsService.java) | 检查统计权限，调用仓储并组装带筛选条件和更新时间的结果。 |
| [application/port/AnalyticsRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/application/port/AnalyticsRepository.java) | 定义概览、趋势、分布、合作与更新时间的查询接口。 |
| [domain/AnalyticsCollaboration.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain/AnalyticsCollaboration.java) | 封装作者合作统计与机构合作统计两组结果。 |
| [domain/AnalyticsCollaborationItem.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain/AnalyticsCollaborationItem.java) | 记录一对作者或机构的编号、名称及共同成果数量。 |
| [domain/AnalyticsCoverage.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain/AnalyticsCoverage.java) | 统计 DOI、年份、摘要、被引信息等字段的覆盖情况。 |
| [domain/AnalyticsDistributionItem.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain/AnalyticsDistributionItem.java) | 表示一个统计分组的键、名称和成果数量。 |
| [domain/AnalyticsDistributions.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain/AnalyticsDistributions.java) | 聚合成果类型、来源、机构、主题四类分布。 |
| [domain/AnalyticsOverview.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain/AnalyticsOverview.java) | 表示平台实体数量及元数据覆盖情况的领域结果。 |
| [domain/AnalyticsQuery.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain/AnalyticsQuery.java) | 封装并校验年份、成果类型、来源、机构及主题筛选条件。 |
| [domain/AnalyticsSnapshot.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain/AnalyticsSnapshot.java) | 为任意统计结果附带筛选条件和数据更新时间。 |
| [domain/AnalyticsTrend.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/domain/AnalyticsTrend.java) | 表示某一发表年份的成果数量。 |
| [infrastructure/persistence/AnalyticsMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/infrastructure/persistence/AnalyticsMapper.java) | 声明 MyBatis 统计查询，包括计数、分布、年度趋势与合作关系。 |
| [infrastructure/persistence/AnalyticsRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/infrastructure/persistence/AnalyticsRow.java) | 接收统计 SQL 返回的计数、分组、合作对与覆盖率原始字段。 |
| [infrastructure/persistence/MyBatisAnalyticsRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/analytics/infrastructure/persistence/MyBatisAnalyticsRepository.java) | 调用统计 Mapper，把数据库结果转换为统计领域对象。 |

**使用边界：** 统计结果附带生效筛选条件和数据更新时间；其数据读取不依赖图谱已经同步完成。

<a id="module-export"></a>

### 4.5 export：成果导出

依据筛选条件创建异步导出任务，生成 CSV/JSON 文件，并处理任务归属、下载凭据和过期控制。

当前共 **29 个 Java 文件、11 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [export/](../systems/crawler/backend/src/main/java/com/aacv/system/export) | 依据筛选条件创建异步导出任务，生成 CSV/JSON 文件，并处理任务归属、下载凭据和过期控制。 |
| [export/api/](../systems/crawler/backend/src/main/java/com/aacv/system/export/api) | HTTP 接口与请求、响应模型 |
| [export/application/](../systems/crawler/backend/src/main/java/com/aacv/system/export/application) | 业务用例处理与流程编排 |
| [export/application/port/](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/port) | 业务依赖的接口约定 |
| [export/domain/](../systems/crawler/backend/src/main/java/com/aacv/system/export/domain) | 业务数据模型、枚举与规则 |
| [export/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure) | 数据库、框架及外部系统适配 |
| [export/infrastructure/async/](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/async) | 有容量限制的异步任务执行 |
| [export/infrastructure/config/](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/config) | 组件装配及配置属性 |
| [export/infrastructure/file/](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/file) | 导出文件生成与路径管理 |
| [export/infrastructure/persistence/](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/persistence) | MyBatis 数据访问和结果映射 |
| [export/infrastructure/security/](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/security) | Spring Security 身份与权限适配 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [api/ExportController.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/api/ExportController.java) | 提供创建导出任务、读取任务状态与下载结果文件的接口。 |
| [api/ExportCreateRequest.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/api/ExportCreateRequest.java) | 表示创建导出任务时提交的格式与筛选条件。 |
| [api/ExportTaskResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/api/ExportTaskResponse.java) | 返回导出任务状态、数量、时间、错误及下载是否可用等信息。 |
| [application/ExportConcurrencyLimitException.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/ExportConcurrencyLimitException.java) | 表示导出并发数或排队容量达到限制。 |
| [application/ExportExpiredException.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/ExportExpiredException.java) | 表示导出任务或下载结果已过期。 |
| [application/ExportLimitExceededException.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/ExportLimitExceededException.java) | 表示待导出的记录数量超过允许上限。 |
| [application/ExportRecoveryService.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/ExportRecoveryService.java) | 启动时将被中断的运行任务标为失败，并按容量重新投递待执行任务。 |
| [application/ExportService.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/ExportService.java) | 校验筛选和数量、创建异步任务，检查任务归属、下载令牌及有效期。 |
| [application/ExportTaskFinalizer.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/ExportTaskFinalizer.java) | 在事务中保存导出成功或失败状态，并记录对应审计。 |
| [application/ExportTaskProcessor.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/ExportTaskProcessor.java) | 认领任务、读取成果并写文件，校验记录数量，失败时处理残留文件并结束任务。 |
| [application/port/ExportActorProvider.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/port/ExportActorProvider.java) | 定义读取当前导出操作者编号及管理员身份的接口。 |
| [application/port/ExportFileStore.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/port/ExportFileStore.java) | 定义导出文件生成、路径解析和删除接口。 |
| [application/port/ExportRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/port/ExportRepository.java) | 定义导出任务持久化、并发统计、状态迁移及待导出成果查询。 |
| [application/port/ExportTaskDispatcher.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/application/port/ExportTaskDispatcher.java) | 定义提交导出任务到异步执行器的接口。 |
| [domain/ExportDownload.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/domain/ExportDownload.java) | 描述下载文件的实际路径、下载文件名与内容类型。 |
| [domain/ExportFilter.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/domain/ExportFilter.java) | 封装并校验成果导出筛选条件，包括名称、实体编号、年份和来源。 |
| [domain/ExportFormat.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/domain/ExportFormat.java) | 定义 CSV、JSON 两种导出格式。 |
| [domain/ExportRecord.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/domain/ExportRecord.java) | 定义一条导出成果的编号、题名、DOI、类型、语言、日期及载体。 |
| [domain/ExportStatus.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/domain/ExportStatus.java) | 定义导出任务的等待、执行、成功、失败与过期状态。 |
| [domain/ExportTask.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/domain/ExportTask.java) | 表示导出任务完整状态，包括筛选、操作者、文件、令牌、期限及版本。 |
| [infrastructure/async/BoundedExportTaskDispatcher.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/async/BoundedExportTaskDispatcher.java) | 使用有限容量线程池提交导出任务，处理线程池拒绝提交的情况。 |
| [infrastructure/config/ExportConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/config/ExportConfiguration.java) | 注册导出配置和专用异步执行线程池。 |
| [infrastructure/config/ExportProperties.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/config/ExportProperties.java) | 绑定并校验导出目录、记录上限、并发数、队列容量和保留时长。 |
| [infrastructure/file/LocalExportFileStore.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/file/LocalExportFileStore.java) | 生成 CSV/JSON 文件，处理 CSV 公式注入、临时文件原子替换与路径越界校验。 |
| [infrastructure/persistence/ExportMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/persistence/ExportMapper.java) | 声明导出任务与待导出成果的 MyBatis 数据库操作。 |
| [infrastructure/persistence/ExportRecordRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/persistence/ExportRecordRow.java) | 承接单条待导出成果的 SQL 查询字段。 |
| [infrastructure/persistence/ExportTaskRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/persistence/ExportTaskRow.java) | 承接导出任务表字段及筛选条件 JSON。 |
| [infrastructure/persistence/MyBatisExportRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/persistence/MyBatisExportRepository.java) | 转换导出任务与查询记录，序列化筛选条件并调用 Mapper 更新状态。 |
| [infrastructure/security/SecurityExportActorProvider.java](../systems/crawler/backend/src/main/java/com/aacv/system/export/infrastructure/security/SecurityExportActorProvider.java) | 从 Spring Security 中读取当前用户及管理员身份。 |

**使用边界：** 任务创建、异步执行、状态收尾和文件存储由不同类负责；筛选模型与接口模型分离。

<a id="module-identity"></a>

### 4.6 identity：账号、认证与权限

管理账号、角色、个人资料及登录会话，提供 Spring Security 权限检查、CSRF 保护与账号状态即时校验。

当前共 **46 个 Java 文件、9 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [identity/](../systems/crawler/backend/src/main/java/com/aacv/system/identity) | 管理账号、角色、个人资料及登录会话，提供 Spring Security 权限检查、CSRF 保护与账号状态即时校验。 |
| [identity/api/](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api) | HTTP 接口与请求、响应模型 |
| [identity/application/](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application) | 业务用例处理与流程编排 |
| [identity/application/port/](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/port) | 业务依赖的接口约定 |
| [identity/domain/](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain) | 业务数据模型、枚举与规则 |
| [identity/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure) | 数据库、框架及外部系统适配 |
| [identity/infrastructure/config/](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/config) | 组件装配及配置属性 |
| [identity/infrastructure/persistence/](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/persistence) | MyBatis 数据访问和结果映射 |
| [identity/infrastructure/security/](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/security) | Spring Security 身份与权限适配 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [api/AuthController.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/AuthController.java) | 提供 CSRF 令牌、登录、退出与当前用户接口，管理会话并记录登录审计。 |
| [api/CreateUserRequest.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/CreateUserRequest.java) | 校验创建用户请求中的账号、密码、角色和个人资料。 |
| [api/CsrfTokenResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/CsrfTokenResponse.java) | 返回 CSRF 请求头名称、参数名称和令牌。 |
| [api/CurrentUserResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/CurrentUserResponse.java) | 返回当前登录用户的编号、账号、角色和权限集合。 |
| [api/LoginRequest.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/LoginRequest.java) | 封装并校验登录账号与密码字段。 |
| [api/ReplaceRolesRequest.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/ReplaceRolesRequest.java) | 封装角色替换请求及乐观锁版本。 |
| [api/ResetPasswordRequest.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/ResetPasswordRequest.java) | 封装重置密码请求及乐观锁版本。 |
| [api/UpdateUserRequest.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/UpdateUserRequest.java) | 封装用户资料、角色、状态和版本的编辑请求。 |
| [api/UserController.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/UserController.java) | 提供用户列表、统计、创建、编辑、启停、密码重置和角色调整接口。 |
| [api/UserPageResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/UserPageResponse.java) | 封装用户列表及分页元数据。 |
| [api/UserResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/UserResponse.java) | 返回账号状态、角色、个人资料和版本等公开信息，不包含密码哈希。 |
| [api/VersionRequest.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/api/VersionRequest.java) | 为账号启停等操作提供预期版本号，防止覆盖并发修改。 |
| [application/AdminUserService.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/AdminUserService.java) | 作为管理用例的权限边界，将用户管理操作委托给账号服务。 |
| [application/CreateUserCommand.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/CreateUserCommand.java) | 应用层创建用户命令，承载账号、密码、角色和个人资料。 |
| [application/InvalidCredentialsException.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/InvalidCredentialsException.java) | 统一表示账号或密码错误，供登录失败处理使用。 |
| [application/port/SessionInvalidator.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/port/SessionInvalidator.java) | 定义按账号使既有登录会话失效的接口。 |
| [application/port/UserAccountRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/port/UserAccountRepository.java) | 定义账号、角色、用户统计、版本更新和管理员引导锁的存储接口。 |
| [application/UpdateUserCommand.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/UpdateUserCommand.java) | 应用层更新用户命令，承载版本、资料、角色和状态。 |
| [application/UserAccountService.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/UserAccountService.java) | 处理账号创建与更新、密码哈希、管理员保护、并发版本检查、会话失效及审计。 |
| [application/UsernameConflictException.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/UsernameConflictException.java) | 表示规范化后的用户名已存在。 |
| [application/UserNotFoundException.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/UserNotFoundException.java) | 表示指定用户不存在。 |
| [application/VersionConflictException.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/application/VersionConflictException.java) | 表示用户数据版本与提交时的预期版本不一致。 |
| [domain/AuthorizationPolicy.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/AuthorizationPolicy.java) | 定义管理员、数据运营人员、科研用户的权限集合及角色权限映射。 |
| [domain/PasswordPolicy.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/PasswordPolicy.java) | 校验密码长度及空白、控制字符等限制。 |
| [domain/Permission.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/Permission.java) | 定义功能级权限编码；仍包含部分历史模块权限。 |
| [domain/RoleCode.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/RoleCode.java) | 定义 ADMIN、DATA_OPERATOR、RESEARCHER 三种角色。 |
| [domain/UserAccount.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/UserAccount.java) | 账号领域模型，包含密码哈希、状态、角色、资料及安全版本。 |
| [domain/Username.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/Username.java) | 执行用户名 Unicode 规范化、转小写以及格式长度校验。 |
| [domain/UserProfile.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/UserProfile.java) | 表示并校验姓名、邮箱、电话、单位、部门和备注。 |
| [domain/UserStatistics.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/UserStatistics.java) | 表示用户总数及三个角色的数量统计。 |
| [domain/UserStatus.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/domain/UserStatus.java) | 定义启用、停用、要求重置密码等状态，并判断能否登录。 |
| [infrastructure/config/IdentityConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/config/IdentityConfiguration.java) | 注册密码编码器、UTC 时钟及管理员初始化配置。 |
| [infrastructure/config/InitialAdminBootstrap.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/config/InitialAdminBootstrap.java) | 在明确启用初始化且用户表为空时创建初始管理员。 |
| [infrastructure/config/InitialAdminProperties.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/config/InitialAdminProperties.java) | 绑定初始管理员引导的开关和输入配置。 |
| [infrastructure/persistence/JdbcSessionInvalidator.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/JdbcSessionInvalidator.java) | 通过数据库删除指定账号的会话记录。 |
| [infrastructure/persistence/MyBatisUserAccountRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/MyBatisUserAccountRepository.java) | 实现账号及角色查询、更新、统计和引导锁，转换数据库行与领域对象。 |
| [infrastructure/persistence/SessionMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/SessionMapper.java) | 声明按账号删除数据库会话的 SQL 操作。 |
| [infrastructure/persistence/UserAccountMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/UserAccountMapper.java) | 声明用户、角色、乐观锁更新和管理员保护锁的 MyBatis 操作。 |
| [infrastructure/persistence/UserAccountRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/UserAccountRow.java) | 承接用户表字段，包含账号状态、个人资料和安全版本等信息。 |
| [infrastructure/persistence/UserRoleRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/UserRoleRow.java) | 表示用户编号与角色编码之间的对应关系。 |
| [infrastructure/security/AccountFreshnessFilter.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/security/AccountFreshnessFilter.java) | 每次请求核对账号状态和安全版本；账号变化时销毁旧会话并返回未登录错误。 |
| [infrastructure/security/ApiAccessDeniedHandler.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/security/ApiAccessDeniedHandler.java) | 把权限不足或相关安全拒绝转换为统一 HTTP 403 错误响应。 |
| [infrastructure/security/ApiAuthenticationEntryPoint.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/security/ApiAuthenticationEntryPoint.java) | 把未认证访问转换为统一 HTTP 401 错误响应。 |
| [infrastructure/security/DatabaseUserDetailsService.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/security/DatabaseUserDetailsService.java) | 从数据库读取账号并转换为 Spring Security 认证主体。 |
| [infrastructure/security/SecurityConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/security/SecurityConfiguration.java) | 配置认证管理器、会话、CSRF、接口权限规则和安全过滤器链。 |
| [infrastructure/security/UserPrincipal.java](../systems/crawler/backend/src/main/java/com/aacv/system/identity/infrastructure/security/UserPrincipal.java) | 适配 Spring Security 用户主体，携带用户编号、角色、权限及安全版本。 |

**使用边界：** 使用数据库会话；账号安全状态变化后旧会话失效。Permission 中存在历史编码，不表示对应退役接口仍可调用。

<a id="module-operations"></a>

### 4.7 operations：运行状态、告警与审计

汇总运行及数据库健康状态，评估告警，记录和查询操作、登录及失败审计。

当前共 **41 个 Java 文件、11 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [operations/](../systems/crawler/backend/src/main/java/com/aacv/system/operations) | 汇总运行及数据库健康状态，评估告警，记录和查询操作、登录及失败审计。 |
| [operations/api/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/api) | HTTP 接口与请求、响应模型 |
| [operations/application/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application) | 业务用例处理与流程编排 |
| [operations/application/port/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application/port) | 业务依赖的接口约定 |
| [operations/domain/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain) | 业务数据模型、枚举与规则 |
| [operations/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure) | 数据库、框架及外部系统适配 |
| [operations/infrastructure/config/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/config) | 组件装配及配置属性 |
| [operations/infrastructure/persistence/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/persistence) | MyBatis 数据访问和结果映射 |
| [operations/infrastructure/quartz/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/quartz) | Quartz 定时任务及调度装配 |
| [operations/infrastructure/security/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/security) | Spring Security 身份与权限适配 |
| [operations/infrastructure/web/](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/web) | 请求信息、过滤器与错误响应 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [api/AlertAcknowledgeRequest.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/api/AlertAcknowledgeRequest.java) | 封装人工确认告警的理由与预期版本号。 |
| [api/AlertEventPageResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/api/AlertEventPageResponse.java) | 封装告警列表及分页元数据。 |
| [api/AlertEventResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/api/AlertEventResponse.java) | 返回告警类型、级别、证据、出现次数、确认信息和版本。 |
| [api/AuditController.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/api/AuditController.java) | 提供按分类、账号、时间、结果和动作筛选的审计日志分页查询。 |
| [api/AuditLogResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/api/AuditLogResponse.java) | 返回审计动作、目标、结果、追踪号、摘要、账号和请求来源信息。 |
| [api/AuditPageResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/api/AuditPageResponse.java) | 封装审计日志列表及分页元数据。 |
| [api/OperationsController.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/api/OperationsController.java) | 提供运行概览、告警列表及人工确认告警接口。 |
| [application/AlertCondition.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application/AlertCondition.java) | 表示一次告警检测条件及其去重键。 |
| [application/AlertEvaluationService.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application/AlertEvaluationService.java) | 评估连续采集失败、解析成功率下降和图同步积压等告警条件。 |
| [application/AlertService.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application/AlertService.java) | 归并重复告警、更新检测次数，提供查询、版本校验和人工确认。 |
| [application/AuditService.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application/AuditService.java) | 记录业务操作、登录和失败审计，关联操作者与请求信息并提供日志查询。 |
| [application/OperationsService.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application/OperationsService.java) | 汇总应用、MySQL、Neo4j 健康状态及采集、图同步、告警数量。 |
| [application/port/AuditLogRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application/port/AuditLogRepository.java) | 定义审计记录追加和分页查询接口。 |
| [application/port/CurrentActorProvider.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application/port/CurrentActorProvider.java) | 定义获取当前操作者用户编号的接口。 |
| [application/port/OperationsRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/application/port/OperationsRepository.java) | 定义运维计数、告警信号、去重、状态更新及告警查询接口。 |
| [domain/AlertEvent.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AlertEvent.java) | 告警领域模型，保存证据、级别、次数、检测时间和人工确认信息。 |
| [domain/AlertSeverity.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AlertSeverity.java) | 定义 WARNING、CRITICAL 告警级别。 |
| [domain/AlertStatus.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AlertStatus.java) | 定义未确认和已确认告警状态。 |
| [domain/AlertSubjectType.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AlertSubjectType.java) | 定义告警关联的数据源、采集任务或图同步对象。 |
| [domain/AlertType.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AlertType.java) | 定义连续采集失败、解析成功率下降和图同步积压三类告警。 |
| [domain/AuditAction.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AuditAction.java) | 定义可记录的审计动作，保留历史采集、治理和 ORCID 动作以解释历史记录。 |
| [domain/AuditCategory.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AuditCategory.java) | 将审计动作分为登录日志与操作日志。 |
| [domain/AuditLogEntry.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AuditLogEntry.java) | 表示从数据库读取的完整审计日志条目。 |
| [domain/AuditQuery.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AuditQuery.java) | 校验审计分类、账号、时间和动作筛选，并转义账号模糊查询特殊字符。 |
| [domain/AuditRecord.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AuditRecord.java) | 表示待写入审计记录，校验摘要字段并限制敏感键和文本长度。 |
| [domain/AuditResult.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/AuditResult.java) | 定义审计操作的成功与失败结果。 |
| [domain/HealthStatus.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/HealthStatus.java) | 定义 UP、DOWN、DEGRADED、UNKNOWN 健康状态。 |
| [domain/OperationsOverview.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/domain/OperationsOverview.java) | 表示运行概览的健康状态、采集计数、图事件积压与未确认告警数量。 |
| [infrastructure/config/OperationsConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/config/OperationsConfiguration.java) | 注册运维配置属性。 |
| [infrastructure/config/OperationsProperties.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/config/OperationsProperties.java) | 绑定并校验告警周期、失败次数、解析成功率和图积压时间阈值。 |
| [infrastructure/persistence/AlertEventRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/AlertEventRow.java) | 承接告警事件表字段。 |
| [infrastructure/persistence/AuditLogMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/AuditLogMapper.java) | 声明审计日志插入、计数与分页查询操作。 |
| [infrastructure/persistence/AuditLogRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/AuditLogRow.java) | 承接审计表字段及联表取得的账号信息。 |
| [infrastructure/persistence/MyBatisAuditLogRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/MyBatisAuditLogRepository.java) | 转换审计记录与数据库行，并序列化或解析摘要 JSON。 |
| [infrastructure/persistence/MyBatisOperationsRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/MyBatisOperationsRepository.java) | 实现运维统计与告警存储，转换告警证据 JSON 和领域对象。 |
| [infrastructure/persistence/OperationsMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/OperationsMapper.java) | 声明运行计数、告警信号、告警去重、确认及分页查询的 SQL 操作。 |
| [infrastructure/quartz/AlertEvaluationQuartzJob.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/quartz/AlertEvaluationQuartzJob.java) | 通过 Quartz 执行一轮告警评估，禁止同一任务并发执行。 |
| [infrastructure/quartz/AlertEvaluationQuartzSchedule.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/quartz/AlertEvaluationQuartzSchedule.java) | 在告警启用时注册周期评估任务，并按配置调整执行间隔。 |
| [infrastructure/security/SecurityCurrentActorProvider.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/security/SecurityCurrentActorProvider.java) | 从 Spring Security 上下文提取当前操作者编号。 |
| [infrastructure/web/AuditRequestMetadata.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/web/AuditRequestMetadata.java) | 读取连接 IP 和 User-Agent，限制长度并移除控制字符。 |
| [infrastructure/web/FailedOperationAuditFilter.java](../systems/crawler/backend/src/main/java/com/aacv/system/operations/infrastructure/web/FailedOperationAuditFilter.java) | 识别受关注的失败业务请求，根据响应状态和错误码追加失败审计。 |

**使用边界：** 保留历史采集计数、告警类型和审计动作，以继续解释历史记录；这些定义不恢复旧采集业务入口。

<a id="module-infrastructure"></a>

### 4.8 infrastructure：公共技术配置

配置跨模块共享的 JDBC 事务、Spring Batch 执行器，以及支持 Spring 依赖注入的 Quartz JobFactory。

当前共 **5 个 Java 文件、4 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/infrastructure) | 配置跨模块共享的 JDBC 事务、Spring Batch 执行器，以及支持 Spring 依赖注入的 Quartz JobFactory。 |
| [infrastructure/batch/](../systems/crawler/backend/src/main/java/com/aacv/system/infrastructure/batch) | Spring Batch 批任务执行 |
| [infrastructure/database/](../systems/crawler/backend/src/main/java/com/aacv/system/infrastructure/database) | 数据库事务配置和连接探测 |
| [infrastructure/quartz/](../systems/crawler/backend/src/main/java/com/aacv/system/infrastructure/quartz) | Quartz 定时任务及调度装配 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [batch/BatchJobLauncherConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/infrastructure/batch/BatchJobLauncherConfiguration.java) | 配置公共 Batch 注册表、有限容量线程池及异步 batchJobOperator，供图维护使用。 |
| [database/DatabaseProbeMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/infrastructure/database/DatabaseProbeMapper.java) | 定义 selectOne 数据库连通性探测查询。 |
| [database/TransactionConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/infrastructure/database/TransactionConfiguration.java) | 将 JDBC/MySQL 事务管理器注册为默认事务管理器。 |
| [quartz/AutowiringSpringBeanJobFactory.java](../systems/crawler/backend/src/main/java/com/aacv/system/infrastructure/quartz/AutowiringSpringBeanJobFactory.java) | 使 Quartz 创建的 Job 实例能够注入 Spring 管理的依赖。 |
| [quartz/QuartzJobFactoryConfiguration.java](../systems/crawler/backend/src/main/java/com/aacv/system/infrastructure/quartz/QuartzJobFactoryConfiguration.java) | 把支持依赖注入的 JobFactory 接入 Quartz 调度器。 |

**使用边界：** 该目录服务于现有图维护和定时任务，不能因名称与原采集实现相关就删除。

<a id="module-shared"></a>

### 4.9 shared：公共模型与 Web 支持

提供统一分页、错误码、通用异常、ORCID 值校验及请求追踪、异常响应能力。

当前共 **9 个 Java 文件、5 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [shared/](../systems/crawler/backend/src/main/java/com/aacv/system/shared) | 提供统一分页、错误码、通用异常、ORCID 值校验及请求追踪、异常响应能力。 |
| [shared/application/](../systems/crawler/backend/src/main/java/com/aacv/system/shared/application) | 业务用例处理与流程编排 |
| [shared/domain/](../systems/crawler/backend/src/main/java/com/aacv/system/shared/domain) | 业务数据模型、枚举与规则 |
| [shared/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/shared/infrastructure) | 数据库、框架及外部系统适配 |
| [shared/infrastructure/web/](../systems/crawler/backend/src/main/java/com/aacv/system/shared/infrastructure/web) | 请求信息、过滤器与错误响应 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [application/ResourceConflictException.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/application/ResourceConflictException.java) | 通用资源状态或并发冲突异常。 |
| [application/ResourceNotFoundException.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/application/ResourceNotFoundException.java) | 通用资源不存在异常。 |
| [domain/ErrorCode.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/domain/ErrorCode.java) | 定义对外响应使用的统一机器可读错误码。 |
| [domain/OrcidId.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/domain/OrcidId.java) | 规范化并校验 ORCID 标识及校验位，不负责联网获取编号。 |
| [domain/PageResult.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/domain/PageResult.java) | 统一分页结果模型，携带条目、页号、页大小、总数及总页数。 |
| [infrastructure/web/ApiExceptionHandler.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/infrastructure/web/ApiExceptionHandler.java) | 将参数、认证、资源、图谱、导出和未知异常转换为统一 ProblemDetail 响应。 |
| [infrastructure/web/ProblemResponseWriter.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/infrastructure/web/ProblemResponseWriter.java) | 供安全过滤器等非控制器环节写入统一错误 JSON。 |
| [infrastructure/web/TraceContext.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/infrastructure/web/TraceContext.java) | 规范化或生成 traceId，并管理日志 MDC 中的追踪上下文。 |
| [infrastructure/web/TraceIdFilter.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/infrastructure/web/TraceIdFilter.java) | 为每个 HTTP 请求设置追踪号、传递响应头，并在请求结束后清理上下文。 |

**使用边界：** 只承载可复用基础能力；OrcidId 校验已有编号，不执行 ORCID 网络查询。

<a id="module-ingestion"></a>

### 4.10 ingestion：历史原始数据到期清理

按到期时间有界清空历史 raw_record.payload，保留原始记录、哈希及证据关联。

当前共 **6 个 Java 文件、6 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [ingestion/](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion) | 按到期时间有界清空历史 raw_record.payload，保留原始记录、哈希及证据关联。 |
| [ingestion/application/](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/application) | 业务用例处理与流程编排 |
| [ingestion/application/port/](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/application/port) | 业务依赖的接口约定 |
| [ingestion/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/infrastructure) | 数据库、框架及外部系统适配 |
| [ingestion/infrastructure/persistence/](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/infrastructure/persistence) | MyBatis 数据访问和结果映射 |
| [ingestion/infrastructure/retention/](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/infrastructure/retention) | 历史原始数据的保留清理调度 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [application/port/IngestionRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/application/port/IngestionRepository.java) | 目前只定义历史原始 payload 的到期清理接口。 |
| [application/RawPayloadRetentionService.java](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/application/RawPayloadRetentionService.java) | 校验清理批次大小，在事务中清理到期 payload 并返回清理计数。 |
| [infrastructure/persistence/IngestionMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/infrastructure/persistence/IngestionMapper.java) | 目前只声明按到期时间和批次数量清空 payload 的数据库操作。 |
| [infrastructure/persistence/MyBatisIngestionRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/infrastructure/persistence/MyBatisIngestionRepository.java) | 校验到期时间与批量上限，并调用 Mapper 清理历史 payload。 |
| [infrastructure/retention/RawPayloadRetentionJob.java](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/infrastructure/retention/RawPayloadRetentionJob.java) | 执行一批 500 条上限的历史 payload 清理，记录清理数量。 |
| [infrastructure/retention/RawPayloadRetentionScheduler.java](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/infrastructure/retention/RawPayloadRetentionScheduler.java) | 注册每天 UTC 03:15 执行的历史 payload 保留清理计划。 |

**使用边界：** 旧采集解析、标准化和成果入库实现已删除。当前仅保留清理服务、最小数据访问接口及定时任务。

<a id="module-source"></a>

### 4.11 source：来源与学术元数据模型

保留历史来源枚举以及被引、撤稿、开放获取和版本关系等数据模型，供现有编目、统计及导出读取。

当前共 **2 个 Java 文件、2 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [source/](../systems/crawler/backend/src/main/java/com/aacv/system/source) | 保留历史来源枚举以及被引、撤稿、开放获取和版本关系等数据模型，供现有编目、统计及导出读取。 |
| [source/domain/](../systems/crawler/backend/src/main/java/com/aacv/system/source/domain) | 业务数据模型、枚举与规则 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [domain/ScholarlyMetadata.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/domain/ScholarlyMetadata.java) | 保留历史成果学术元数据模型，包含被引次数、撤稿、开放获取和 DOI 版本关系。 |
| [domain/SourceType.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/domain/SourceType.java) | 保留 OPENALEX、CROSSREF 来源枚举，供历史数据及统计、导出筛选使用。 |

**使用边界：** 当前仅有两个领域模型文件；已没有 OpenAlex/Crossref 网络客户端、解析器或数据源管理接口。

<a id="module-crawl"></a>

### 4.12 crawl：历史采集调度兼容

保留数据库中已持久化的两个 Quartz Job 类名，旧任务触发后删除自身调度。

当前共 **2 个 Java 文件、3 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [crawl/](../systems/crawler/backend/src/main/java/com/aacv/system/crawl) | 保留数据库中已持久化的两个 Quartz Job 类名，旧任务触发后删除自身调度。 |
| [crawl/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure) | 数据库、框架及外部系统适配 |
| [crawl/infrastructure/quartz/](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/quartz) | Quartz 定时任务及调度装配 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [infrastructure/quartz/QuartzCrawlTriggerJob.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/quartz/QuartzCrawlTriggerJob.java) | 兼容历史采集调度类名，执行时只删除旧任务，不发起采集。 |
| [infrastructure/quartz/QuartzQuotaResumeJob.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/quartz/QuartzQuotaResumeJob.java) | 兼容历史配额恢复调度类名，执行时只删除旧任务，不恢复采集。 |

**使用边界：** 两个类均不发起网络采集、不恢复配额等待运行。类名中的 Trigger、Resume 是历史兼容名称。

<a id="module-authororcid"></a>

### 4.13 authororcid：历史 ORCID 调度兼容

启动时清理旧 ORCID 任务，并兼容可能先于清理被触发的历史 Quartz 类名。

当前共 **2 个 Java 文件、2 个目录**（目录数包含模块根目录）。

**目录职责**

| 目录 | 具体作用 |
| --- | --- |
| [authororcid/](../systems/crawler/backend/src/main/java/com/aacv/system/authororcid) | 启动时清理旧 ORCID 任务，并兼容可能先于清理被触发的历史 Quartz 类名。 |
| [authororcid/infrastructure/](../systems/crawler/backend/src/main/java/com/aacv/system/authororcid/infrastructure) | 数据库、框架及外部系统适配 |

**文件职责**

| 文件（相对于模块目录） | 具体作用 |
| --- | --- |
| [infrastructure/OrcidQuartzCleanup.java](../systems/crawler/backend/src/main/java/com/aacv/system/authororcid/infrastructure/OrcidQuartzCleanup.java) | 应用启动时删除历史 ORCID Quartz 任务及其调度。 |
| [infrastructure/OrcidQuartzJob.java](../systems/crawler/backend/src/main/java/com/aacv/system/authororcid/infrastructure/OrcidQuartzJob.java) | 兼容 Quartz 已持久化的旧类名；触发后只删除自身任务，不查询 ORCID。 |

**使用边界：** 当前没有获取、绑定、回填或重试 ORCID 的业务实现，已有外部编号与历史数据继续保留。

<a id="reading"></a>

## 5. 源码阅读顺序

按要解决的问题选择入口，优先阅读 Controller、Service 和存储接口，再进入具体 Mapper 或框架实现。

| 阅读目标 | 建议顺序 |
| --- | --- |
| 理解当前资料如何进入系统 | `authorimport/api → authorimport/application → authorimport/infrastructure → graph/application/port` |
| 理解页面数据从哪里来 | `catalog/api → catalog/application → catalog/infrastructure/persistence`，统计同理查看 `analytics` |
| 理解为什么目录有数据但图谱稍后更新 | `MyBatisGraphProjectionRequestAdapter → GraphOutboxService → GraphOutboxProcessor → Neo4jGraphProjectionWriter` |
| 理解三类作者图及合作关系 | `GraphController → GraphQueryService → GraphPresentationService → GraphView / AuthorGraphView` |
| 理解登录、权限和会话失效 | `SecurityConfiguration → AuthController → DatabaseUserDetailsService → AccountFreshnessFilter` |
| 理解导出任务失败或下载过期 | `ExportService → ExportTaskProcessor → ExportTaskFinalizer → LocalExportFileStore` |
| 理解审计、告警和健康信息 | `operations/api → operations/application → operations/infrastructure` |
| 理解历史兼容为什么保留 | `crawl`、`authororcid`、`ingestion`、`source` 的说明及项目基线 |

<a id="verification"></a>

## 6. 维护、核对与适用边界

### 6.1 本文的完整性检查

本文按当前实际目录清单生成，文件说明与源码名称一一对应：**247 个 Java 文件、82 个子目录、13 个顶层模块及 1 个启动文件**。已移除的空目录和旧实现不计入当前清单。

维护时需要核对目录和文件是否新增、移动、重命名或删除，并同时校验源码链接；只改文件数量会留下过时说明。后端源码发生变化后，应重新检查相关职责和调用链。

### 6.2 当前证据边界

- 依据为指定包的源码、数据库映射及当前项目文档；不依据文件名推断旧模块仍可使用。
- 源码职责说明不等于已执行构建、单元测试、真实数据库验证或登录试用。
- 运行中的 JAR 或编译输出可能与源码不同，是否加载当前代码需按现有运行流程另行核对。
- 本文不记录账号、密码、令牌、外部配置内容或业务库临时状态。
- 本次文档整理只新增本说明并在现有文档索引中增加入口，不修改业务代码、依赖、数据库、运行配置或 Git 历史。

### 6.3 与项目记忆的关系

仓库指定记忆仍为 [集成基线](integration-baseline.md) 和 [开发与运行](development.md)。本文是源码阅读说明，不建立新的项目记忆体系。当前模块边界及退役兼容用途已与这两份指定记忆核对一致；本次没有改变架构、接口或运行行为，因此不改写两份记忆。文档入口登记在 [项目文档索引](README.md)。

进一步阅读：[作者导入](author-import.md)、[三类学术图谱](academic-graphs.md)、[认证与权限](unified-login.md)、[ORCID 停用边界](author-orcid.md)、[验证指南](crawler-acceptance.md)。
