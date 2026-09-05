# 学术成果爬虫与可视化系统设计

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 系统名称 | 学术成果爬虫与可视化系统 |
| 文档版本 | 0.8（导航、采集恢复、治理证据与查询优化同步） |
| 文档状态 | 阶段0至7已验收；2026-09-05优化基线 |
| 编制日期 | 2026-09-05 |
| 关联需求 | [需求分析](./requirements-analysis.md) |
| 设计范围 | 第一阶段 MVP |

本设计以已确认的第一阶段本地开发需求为前提。MySQL 使用 MyBatis、数据库迁移使用 Flyway、Neo4j 使用 Spring Data Neo4j；首批只实现 OpenAlex 和 Crossref。服务器部署、生产 HTTPS、服务器资源和生产数据库版本不属于当前基线，实际部署前必须重新评审。

## 2. 设计前提

2026-09-05优化基线：导航统一划分为可视化、爬虫管理、系统状态、用户管理四组，统一配置驱动侧栏与命令面板。图谱名称查找复用权限保护的目录接口，主题目录与图投影统一使用`subject.id`；常用图查询只保存在按账号隔离的本机浏览器存储。OpenAlex密钥作为可选本机配置，仅进入固定官方请求的Authorization头。验收细节见[优化验收记录](./optimization-acceptance.md)。

V12为采集运行增加结束原因及最多三次的额度延后恢复。`SOURCE_EXHAUSTED`只表示当前查询范围的游标耗尽；`PAGE_LIMIT`/`RECORD_LIMIT`属于`PARTIAL_SUCCESS`。每日计划实际重复固定范围，已将模式更正为`FIXED_SCOPE_REFRESH`；没有自动移动日期或推进全量同步水位。额度恢复复用MySQL检查点和现有Quartz，每60秒扫描最多20个到期运行；手动暂停或取消优先。运行结果写入与页面事务、事务提交后的Batch启动边界详见优化验收记录。

V13增加机构来源名称证据表，只按已确认ROR或来源标识归属名称，保留首次和最近观测时间，并支持机构别名检索。作者编目的机构区间由当前署名论文的出版年份计算，不代表任职时间。两类证据通过目录读权限接口提供，每类最多100项并报告截断。治理候选增加当前字段对照，明确DOI版本关系禁止重复合并；来源指标、机构证据和治理保护的语义见`optimization-acceptance.md`。

统计总览增加当前范围内的字段可用数量，覆盖DOI、出版年份、摘要、来源被引量、开放和撤稿状态，并报告署名可能不完整的成果数量。按规范成果去重，零和false均视为有效来源值；空分母没有覆盖百分比。机构、主题、来源采用完整计数，分类间可以重叠，不能把它们相加当作成果总数或将字段完整度当作全球采集覆盖率。

来源解析版本2在已有规范来源快照中增加`scholarlyMetadata`，按来源记录被引量与采集时间、可用的撤稿/开放状态以及明确的DOI版本关系。目录详情返回各来源信息，不跨来源求和；旧快照和缺失字段保留未知。版本关系只作来源证据展示，不触发同名归一或自动合并。

采集和图维护的Batch启动器都在业务提交回调内临时挂起尚未解绑的事务资源，让Batch独立创建元数据事务。图维护长循环也挂起Batch外层事务：状态和每页操作继续沿用既有独立事务，随后在独立只读事务和SqlSession中读取已提交游标，并检查游标严格递增，防止旧快照或一级缓存导致重复处理同一页。重建仍通过认证、权限、CSRF和显式确认值启动，投影仍经Outbox处理后对账。

### 账号与日志 V14 增量

`identity`继续负责账号、权限和会话；用户资料作为`UserProfile`保存在`sys_user`的可空字段中，不关联学术作者或机构实体。`PUT /api/v1/users/{userId}`以`USER_UPDATE`授权，一次事务保存资料、角色、状态和成功审计；用户名不变，密码独立管理。没有实际变化的保存不推进版本，角色未变时保留角色分配时间。新建UI要求姓名，原创建API允许省略新增资料字段以兼容旧调用与管理员引导。

`version`用于并发控制，`security_version`用于会话有效性；资料修改只推进前者，角色、状态和密码变化推进安全版本。保留会话序列化兼容性，旧会话安全版本缺失时按401会话过期处理，重新登录后生效。管理员角色行作为账号安全写入的固定事务锁，所有相关入口按相同顺序锁定后检查当前管理员数量，阻止自我停用/降权及移除最后一个可用管理员。

`GET /api/v1/users/statistics`在MySQL全库按ADMIN、DATA_OPERATOR、RESEARCHER优先级对每个账号互斥归类，包含所有状态。统计、列表、最近登录分别加载，失败不伪造零值。账号页面复用ECharts和现有主题、Sheet组件；日志页面位于系统状态组并使用`AUDIT_READ`。

`operations`复用`audit_log`，由事件类型导出LOGIN或OPERATION分类，历史记录无需重写。查询支持字面账号关键字、半开时间区间、结果和已登记操作；按原操作筛选同时包含相应OPERATION_FAILED事件。关键业务成功审计仍与业务提交保持原事务关系，API失败由显式端点允许列表的过滤器记录，使用REQUIRES_NEW事务；如果失败审计本身不可写，保留原失败响应并输出仅含traceId和异常类型的故障日志。普通查询、翻页和后台查询不产生操作日志。异步受理事件不代表任务已完成。

登录来源保存实际连接地址和清理控制字符、限制长度后的User-Agent；不信任转发头，不做外部定位。认证失败的尝试账号与操作人分开，未到认证阶段的无效请求不解析正文猜测账号。所有日志仍禁止密码、会话标识和完整请求体，资料修改审计只记录变更类别。当前OpenAPI增量版本为7.1.0，详细验证见`account-management-acceptance.md`。

### 2.1 当前建议基线

- 面向单一学校、科研机构或内部研究团队；
- 第一阶段采集期刊论文、会议论文和预印本元数据；
- 首批按 OpenAlex、Crossref 的顺序接入两个结构化开放数据源；
- 不下载、不保存论文 PDF 全文；
- 后端使用 Java 21 和 Spring Boot；
- 前端使用 Vue 3 和 TypeScript；
- 本地 MySQL80保存业务权威数据；兼容基线为8.0.42，当前验收主机实测为8.0.41；
- MySQL 数据交互使用 MyBatis；
- MySQL 表、索引、约束和必要基础数据使用 Flyway 版本迁移；
- Neo4j 保存用于查询和可视化的图投影；
- Neo4j 数据交互使用 Spring Data Neo4j；
- 第一阶段在 Windows 本机开发运行：后端和前端直接启动，MySQL 使用本机服务，Neo4j 使用 Docker；
- 本地只允许 localhost 或经用户确认的同一内网访问，服务器 HTTPS 部署延后确认；
- 建议容量为 10 万条成果和 100 万条图关系；
- 图查询单次最多返回 300 个节点；
- Neo4j 生产基线使用 5.26 LTS，不使用 4.4。

### 2.2 设计目标

1. 模块边界明确，允许在不重写核心业务的情况下新增数据源和成果类型。
2. 采集、解析、标准化、去重、持久化和图同步可以分别定位故障。
3. 任务支持限流、检查点、重试、暂停、恢复和取消。
4. MySQL 与 Neo4j 之间不依赖脆弱的跨数据库分布式事务。
5. 外部数据始终按不可信输入处理。
6. 保持第一阶段部署简单，避免在规模未验证前引入微服务和消息队列。

### 2.3 设计非目标

- 不设计多租户数据隔离；
- 不设计跨数据中心高可用；
- 不设计通用浏览器自动化平台；
- 不提供反反爬、验证码破解或访问控制绕过；
- 不设计 PDF 全文解析和语义向量检索；
- 不承诺在未完成容量测试前支持百万级以上成果；
- 不在第一阶段拆分独立采集服务、图服务或分析服务。

## 3. 关键设计决策

| 编号 | 决策 | 状态 | 理由 |
| --- | --- | --- | --- |
| DD-001 | 采用模块化单体 | 已确认 | 保持部署简单，同时通过模块边界控制复杂度 |
| DD-002 | MySQL 为业务权威数据源 | 已确认 | 权限、任务、审计和标准成果需要可靠事务 |
| DD-003 | Neo4j 为可重建图投影 | 已确认 | 将关系查询优势与核心业务事务解耦 |
| DD-004 | 使用事务 Outbox 同步图数据 | 已确认 | 避免 MySQL 与 Neo4j 双写不一致 |
| DD-005 | 优先接入公开结构化 API | 已确认 | 稳定性、合规性和维护成本优于网页解析 |
| DD-006 | Spring MVC 处理业务 API，RestClient 负责受控出站请求 | 已确认 | 保持事务与批处理模型清晰，不引入全链路响应式复杂度 |
| DD-007 | Quartz 负责计划触发，Spring Batch 负责任务执行 | 已确认 | 分离“何时执行”和“如何可靠处理批次” |
| DD-008 | 服务端会话与 HttpOnly Cookie | 已确认 | 适合单一 Web 系统，减少前端持有长期令牌的风险 |
| DD-009 | MVP 不引入 Redis、消息队列和搜索引擎 | 已确认 | 当前规模可由 MySQL、数据库队列和索引满足 |
| DD-010 | 原始记录与标准成果分离 | 已确认 | 支持重新解析、追溯和解析器升级 |
| DD-011 | MySQL 数据交互使用 MyBatis | 已确认 | 便于精确控制批量写入、去重、动态检索和聚合 SQL |
| DD-012 | MySQL 数据库迁移使用 Flyway | 已确认 | 使表、索引、约束和基础数据变更可追踪、可复核 |
| DD-013 | Neo4j 数据交互使用 Spring Data Neo4j | 已确认 | 与 Spring Boot 集成并保持图数据访问边界 |

## 4. 总体架构

### 4.1 系统上下文

~~~mermaid
flowchart LR
    Operator[数据运营人员]
    Researcher[科研用户]
    Admin[系统管理员]
    Sources[OpenAlex / Crossref]

    System[学术成果爬虫与可视化系统]
    MySQL[(MySQL)]
    Neo4j[(Neo4j)]

    Admin -->|配置与审计| System
    Operator -->|任务与数据治理| System
    Researcher -->|检索与分析| System
    System -->|受限采集| Sources
    System -->|业务数据| MySQL
    System -->|图投影| Neo4j
~~~

### 4.2 逻辑架构

~~~mermaid
flowchart TB
    UI[Vue 3 前端]
    API[Spring Boot REST API]

    subgraph Application[模块化单体]
        Auth[认证与权限]
        Source[数据源管理]
        Task[任务编排]
        Adapter[数据源适配器]
        Ingest[采集与解析]
        Normalize[标准化与去重]
        Catalog[成果目录]
        Graph[图同步与查询]
        Analytics[统计分析]
        Export[导出]
        Ops[运行监控与审计]
    end

    MySQL[(本机 MySQL80<br/>当前8.0.41/基线8.0.42)]
    Neo4j[(Neo4j 5.26 LTS)]
    External[外部学术数据源]

    UI --> API
    API --> Auth
    API --> Source
    API --> Task
    API --> Catalog
    API --> Graph
    API --> Analytics
    API --> Export
    API --> Ops

    Task --> Adapter
    Adapter --> External
    Adapter --> Ingest
    Ingest --> Normalize
    Normalize --> Catalog

    Auth --> MySQL
    Source --> MySQL
    Task --> MySQL
    Catalog --> MySQL
    Normalize --> MySQL
    Analytics --> MySQL
    Ops --> MySQL

    MySQL -->|Outbox| Graph
    Graph --> Neo4j
~~~

### 4.3 运行时架构

第一阶段后端使用单个 Spring Boot 应用进程，同时承载：

- REST API；
- Quartz 计划触发器；
- Spring Batch 作业；
- 图同步后台任务；
- 健康检查和指标端点。

单实例可以避免未配置集群协调时的重复调度。后续需要多个后端实例时，必须先启用 Quartz 数据库集群模式、批处理作业互斥、Outbox 事件并发认领和共享会话，再进行水平扩容。

## 5. 技术栈

| 分类 | 建议技术 | 用途 |
| --- | --- | --- |
| Java | Java 21 | 后端统一运行时 |
| Web 框架 | Spring Boot 4.1.x、Spring MVC | REST API 和应用装配 |
| 安全 | Spring Security、Spring Session JDBC | 认证、授权、服务端会话 |
| 参数校验 | Jakarta Validation | API 和配置边界校验 |
| **关系数据** | MyBatis Spring Boot Starter、MySQL Connector/J | 兼容基线8.0.42；当前本机8.0.41，阶段8隔离容器8.0.42 |
| **数据库迁移** | Flyway | 结构版本管理 |
| 图数据 | Spring Data Neo4j | Neo4j 节点、关系和查询；连接、池获取和事务重试默认分别限制为5秒 |
| 出站 HTTP | Spring RestClient | 当前 OpenAlex/Crossref REST API；后续来源另行评审 |
| HTML 解析 | Jsoup | 授权静态页面解析 |
| 调度 | Quartz JDBC JobStore | 持久化计划和触发 |
| 批处理 | Spring Batch | 分块、检查点、重启和批次统计 |
| 接口文档 | springdoc-openapi | OpenAPI 文档 |
| 指标 | Spring Boot Actuator、Micrometer | 健康检查与运行指标 |
| **前端** | Vue 3、TypeScript、Vite | Web 用户界面 |
| 通用图表 | ECharts | 趋势、排行和分布图 |
| 图谱组件 | Cytoscape.js 或 AntV G6 | 关系网络可视化 |
| 后端测试 | JUnit 5、Testcontainers | 单元与数据库集成测试 |
| 前端测试 | Vitest、Playwright | 组件与端到端测试 |
| 构建 | Maven、npm | 后端与前端构建 |
| 本地运行 | Maven、Vite、Docker | 后端和前端直接启动，Neo4j 容器化运行 |

Spring 生态依赖优先使用 Spring Boot 的依赖管理版本。MyBatis Spring Boot Starter 需要选择与当前 Spring Boot 版本明确兼容的版本并在构建文件中锁定；除非存在经过验证的兼容性问题，不单独覆盖 Spring Data、Jackson、Spring Batch 或 Neo4j Driver 版本。

## 6. 代码与模块组织

### 6.1 仓库结构建议

~~~text
AACV_System/
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/aacv/system/
│     ├─ main/resources/
│     │  ├─ application.yml
│     │  └─ db/migration/
│     └─ test/
├─ frontend/
│  ├─ package.json
│  └─ src/
├─ deploy/
│  ├─ compose.yaml
│  └─ nginx/
├─ docs/
│  ├─ requirements-analysis.md
│  └─ system-design.md
└─ README.md
~~~

该结构是后续初始化建议，不表示本轮已经创建这些目录或文件。

### 6.2 后端业务模块

建议根包为 com.aacv.system，并按业务能力组织：

| 模块 | 责任 | 不应承担的责任 |
| --- | --- | --- |
| identity | 用户、角色、会话和权限 | 采集任务、成果业务 |
| source | 数据源配置、凭据引用、合规状态 | 来源页面解析 |
| crawl | 任务定义、计划、运行、检查点、暂停和重试 | 成果查询 |
| adapter | 来源适配器和来源 DTO | 直接写标准成果表 |
| ingestion | 下载结果接收、原始记录和解析流水线 | 用户界面逻辑 |
| catalog | 成果、作者、机构、载体、主题和引用 | 图数据库事务 |
| resolution | 去重、作者消歧、机构归一和人工合并 | HTTP 下载 |
| graph | Outbox 消费、Neo4j 投影和图查询 | 权威成果修改 |
| analytics | 聚合统计和读模型 | 数据采集 |
| export | 异步导出任务和下载 | 任意文件读取 |
| operations | 健康、运行指标、告警事件和审计查询 | 修改领域数据 |
| shared | 经过约束的通用类型和基础设施 | 未分类业务逻辑 |

shared 模块只允许保存真正跨模块的技术类型，例如分页模型、错误编码和时钟抽象，不得成为业务逻辑堆放区。

### 6.3 分层约定

每个业务模块内部可采用以下分层：

~~~text
module/
├─ api/              REST 控制器和请求响应模型
├─ application/      用例编排和事务边界
├─ domain/           领域实体、值对象和规则
└─ infrastructure/   MyBatis、Neo4j、HTTP 和外部系统实现
~~~

约束如下：

- API 层不得直接访问数据库；
- 数据源适配器不得直接写入标准成果实体；
- 领域层不得依赖 Spring MVC、MyBatis Mapper 实现或 Neo4j 驱动；
- application 层定义事务边界；
- MySQL Mapper 与 Neo4j Repository 必须位于不同基础设施包；
- 跨模块调用优先通过应用服务接口或领域事件完成；
- 禁止在一个事务中假设 MySQL 与 Neo4j 可以共同提交。

## 7. 数据源适配器设计

### 7.1 适配器职责

每个适配器负责：

1. 校验该数据源支持的任务参数；
2. 生成受控请求；
3. 处理分页或游标；
4. 将外部响应解析为来源记录；
5. 生成下一检查点；
6. 返回来源级错误分类；
7. 提供固定测试样例和解析器版本。

适配器不负责：

- 创建或合并标准成果；
- 直接操作 Neo4j；
- 绕过来源限制；
- 隐藏解析失败；
- 决定用户权限。

### 7.2 统一适配器契约

逻辑接口建议包含：

| 能力 | 输入 | 输出 |
| --- | --- | --- |
| validate | 数据源配置、任务参数 | 校验结果 |
| fetchPage | 数据源配置、查询条件、检查点 | 来源记录页、下一检查点 |
| parse | 原始响应或来源记录 | 标准化前的来源 DTO |
| probe | 数据源配置 | 连通性和权限检查 |
| capabilities | 无 | 支持的过滤条件、增量方式和字段集合 |

### 7.3 数据源类型

| 类型 | MVP 支持 | 说明 |
| --- | --- | --- |
| REST JSON API | 是 | 首选，支持游标或分页 |
| Atom/RSS | 是 | 适合预印本和更新订阅 |
| OAI-PMH | 否（后续） | 适合后续接入机构仓储元数据 |
| 静态 HTML | 按需 | 仅接入已确认授权的网站 |
| JavaScript 动态页面 | 否 | 如确需支持，后续引入独立、受限的浏览器适配器 |
| PDF 全文 | 否 | 不属于 MVP |

### 7.4 出站访问控制

- 仅允许 http 和 https；
- 数据源主机需要管理员显式登记；
- 请求前校验最终解析 IP，阻止未授权的本机、环回、链路本地和云元数据地址；
- 重定向次数有上限，跨主机重定向需要重新校验；
- 不允许用户通过查询参数覆盖基础主机；
- TLS 证书验证不得关闭；
- 连接超时、响应超时、最大响应大小和内容类型必须配置；
- robots.txt 规则按主机缓存，并按标准定期刷新；
- 每个主机使用独立的并发限制和请求间隔；
- 429 和临时 5xx 可按 Retry-After 或退避策略重试；
- 401、403、robots 禁止和参数错误不进行自动重复请求。

## 8. 采集任务设计

### 8.1 任务与运行实例

- 采集任务：描述业务意图和参数，可被多次执行。
- 任务计划：描述何时触发任务。
- 运行实例：任务的一次实际执行。
- 批次步骤：下载、解析、标准化、持久化等阶段。
- 检查点：最后成功提交的游标、页码、时间窗口或来源位置。
- 失败记录：单条记录或步骤失败的可重试证据。

Quartz 只负责创建运行触发，Spring Batch 负责具体的可恢复处理。业务任务表保存用户可理解的任务状态，Spring Batch 元数据表保存技术执行状态，两者通过运行 ID 和 JobExecution ID 关联。

### 8.2 状态机

~~~mermaid
stateDiagram-v2
    [*] --> PENDING: 创建运行
    PENDING --> RUNNING: 调度执行
    RUNNING --> PAUSING: 请求暂停
    PAUSING --> PAUSED: 到达安全检查点
    PAUSED --> RUNNING: 恢复
    RUNNING --> SUCCEEDED: 全部完成
    RUNNING --> PARTIAL_SUCCESS: 部分记录失败
    RUNNING --> FAILED: 作业不可继续
    RUNNING --> CANCELLING: 请求取消
    CANCELLING --> CANCELLED: 到达安全检查点
    FAILED --> PENDING: 人工重试
    PARTIAL_SUCCESS --> PENDING: 重试失败记录
~~~

### 8.3 处理流程

~~~mermaid
sequenceDiagram
    participant Q as Quartz
    participant B as Spring Batch
    participant A as 数据源适配器
    participant R as 原始记录服务
    participant N as 标准化与去重
    participant M as MySQL
    participant O as Outbox

    Q->>B: 创建任务运行
    loop 每个分页或游标批次
        B->>A: 按检查点获取一页
        A-->>B: 来源记录与下一检查点
        B->>R: 幂等保存原始记录
        R->>N: 解析后的来源 DTO
        N->>M: 写入或更新标准成果
        N->>O: 同事务写入图同步事件
        B->>M: 提交批次与检查点
    end
    B->>M: 汇总运行结果
~~~

### 8.4 并发和互斥

- 每个数据源有独立最大并发数；
- 同一数据源的全量任务默认互斥；
- 增量任务与全量回补时间范围重叠时，默认阻止或排队；
- 单个批次使用有限大小线程池，不使用无界队列；
- 数据库连接池大小必须大于任务并发需求并保留 API 查询容量；
- 暂停和取消只在批次边界生效；
- 同一任务创建可以使用 Idempotency-Key 防止重复提交。

### 8.5 重试分类

| 错误类型 | 自动重试 | 处理 |
| --- | --- | --- |
| 连接超时、临时 DNS、502/503/504 | 是 | 有上限的指数退避与抖动 |
| 429 | 是 | 优先遵守 Retry-After |
| 401/403 | 否 | 标记配置或权限错误 |
| 404 | 通常否 | 记录来源记录失效 |
| robots 禁止 | 否 | 标记合规拒绝 |
| JSON/XML/HTML 解析失败 | 单条隔离 | 保存失败样例并触发解析异常指标 |
| 数据唯一键冲突 | 不直接重试 | 进入幂等或冲突处理 |
| MySQL 短暂不可用 | 有限重试 | 事务回滚，不推进检查点 |
| Neo4j 不可用 | Outbox 重试 | 不回滚已成功的业务数据 |

## 9. 数据处理与实体解析

### 9.1 流水线

~~~text
外部响应
  → 原始记录幂等保存
  → 来源字段解析
  → 基础校验
  → 字段标准化
  → 成果候选匹配
  → 作者与机构候选匹配
  → 自动合并或生成审核候选
  → MySQL 事务保存
  → Outbox 事件
~~~

### 9.2 成果匹配策略

按以下优先级匹配：

1. 标准化 DOI 精确相同；
2. 同一来源的来源记录 ID 相同；
3. 其他稳定外部标识相同；
4. 标准化标题、年份和主要作者形成的指纹相同；
5. 标题相似度、作者重合度、年份和载体形成综合置信度。

高置信度可以自动关联到已有成果；中低置信度写入 duplicate_candidate，由数据运营人员审核。匹配阈值必须通过标注样本验证后确定，不在设计阶段虚构固定数值。

### 9.3 作者匹配策略

1. ORCID 精确匹配；
2. 同一来源稳定作者 ID 匹配；
3. 标准化姓名、机构、邮箱哈希和共同成果作为候选证据；
4. 只有在证据充分时自动合并；
5. 同名且机构或研究方向冲突时保持为不同作者；
6. 人工确认的合并或拆分结果进入规则优先级最高的人工决策记录。

### 9.4 标准化规则

- DOI：去除 https://doi.org/、doi: 等前缀，去除首尾空白并统一小写；
- ORCID：统一为四组标识格式并校验校验位；
- 标题：保存原始标题，同时生成 Unicode 规范化和空白折叠后的匹配标题；
- 日期：保存来源精度，只有年份时不得虚构月日；
- 作者：保存原始显示名、标准化名和顺序；
- 机构：保存来源名称、标准机构和别名映射；
- 主题：来源主题与系统标准主题分开保存；
- URL：仅保存通过协议和长度校验的地址；
- 文本：限制长度，清除非法控制字符，展示时进行上下文转义。

## 10. MySQL 数据设计

### 10.1 总体原则

- 使用 InnoDB；
- 使用 utf8mb4；
- 所有主键使用 BIGINT；
- 业务时间使用 UTC；
- 关键表包含 created_at、updated_at 和 version；
- 需要逻辑删除的业务表包含 deleted_at；
- 使用外键保护核心关系，但批处理写入顺序必须明确；
- 大字段与高频列表字段分离；
- 所有唯一性规则在数据库层建立约束；
- 数据库结构统一由 Flyway 管理。

### 10.2 核心表

#### 身份与权限

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| sys_user | 用户账号 | username 唯一 |
| sys_role | 角色 | role_code 唯一 |
| sys_user_role | 用户角色 | user_id、role_id 联合唯一 |
| spring_session 相关表 | 服务端会话 | 由 Spring Session JDBC 管理 |
| audit_log | 关键操作审计 | 按 actor_id、action、created_at 索引 |

#### 数据源与任务

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| data_source | 数据源配置 | source_code 唯一 |
| data_source_secret_ref | 凭据引用或加密值 | 不在接口中返回明文 |
| crawl_task | 任务定义 | 保存参数、范围和创建人 |
| crawl_schedule | Quartz 计划关联 | task_id、schedule_key 唯一 |
| crawl_run | 一次运行实例 | run_no 唯一，关联 Batch JobExecution |
| crawl_failure | 步骤或单条记录失败 | 按 run_id、stage、retryable 索引 |
| crawl_checkpoint | 业务检查点摘要 | run_id、partition_key 唯一 |

Quartz 和 Spring Batch 自有元数据表使用各自官方结构，不复制其内部执行细节。

#### 原始数据与标准成果

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| raw_record | 原始来源记录 | source_id、external_record_id 联合唯一 |
| achievement | 成果公共字段 | normalized_doi 条件唯一，保留类型和状态 |
| paper_detail | 论文扩展字段 | achievement_id 唯一 |
| achievement_source | 成果与来源记录映射 | achievement_id、raw_record_id 联合唯一 |
| author | 标准作者 | ORCID 非空时唯一 |
| author_external_id | 作者外部标识 | source_id、external_id 联合唯一 |
| organization | 标准机构 | 标准代码非空时唯一 |
| organization_alias | 机构别名 | organization_id、normalized_alias 联合唯一 |
| venue | 期刊、会议或仓储 | 类型与外部标识索引 |
| topic | 标准主题 | topic_code 唯一 |
| achievement_author | 成果作者关系 | achievement_id、author_id 联合唯一，保存顺序 |
| author_affiliation | 作者机构关系 | 保存时间范围和来源 |
| achievement_topic | 成果主题关系 | 保存来源和权重 |
| citation | 引用关系 | citing_id、cited_id 联合唯一 |

normalized_doi 为空时不能依赖普通唯一索引完成所有去重，应用层必须结合来源映射和内容指纹处理。

#### 数据治理与图同步

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| duplicate_candidate | 疑似重复候选 | entity_type、left_id、right_id 联合唯一 |
| merge_decision | 自动或人工合并决策 | 保存证据、置信度、操作人和时间 |
| data_revision | 人工修正前后值 | entity_type、entity_id、created_at 索引 |
| graph_outbox_event | 图同步事件 | event_id 唯一，按 status、next_attempt_at 索引 |
| graph_sync_dead_letter | 超过重试上限的事件 | 关联原 Outbox 事件 |

### 10.3 关键字段示例

achievement 建议包含：

- id；
- achievement_type；
- title；
- normalized_title；
- normalized_doi；
- publication_date；
- publication_date_precision；
- publication_year；
- abstract_text；
- language；
- venue_id；
- status；
- match_fingerprint；
- first_seen_at；
- last_seen_at；
- created_at；
- updated_at；
- deleted_at；
- version。

raw_record 建议包含：

- id；
- source_id；
- external_record_id；
- source_url；
- fetched_at；
- payload_hash；
- parser_version；
- raw_payload；
- parse_status；
- parse_error_code；
- created_at；
- updated_at。

graph_outbox_event 建议包含：

- id；
- event_id；
- aggregate_type；
- aggregate_id；
- event_type；
- aggregate_version；
- payload；
- status；
- attempts；
- next_attempt_at；
- locked_by；
- locked_at；
- last_error_code；
- created_at；
- processed_at。

### 10.4 主要索引

- achievement(normalized_doi) 唯一或条件等效约束；
- achievement(publication_year, achievement_type, id)；
- achievement(venue_id, publication_year, id)；
- achievement(match_fingerprint)；
- raw_record(source_id, external_record_id) 唯一；
- raw_record(source_id, fetched_at)；
- achievement_source(raw_record_id)；
- achievement_author(author_id, achievement_id)；
- author(normalized_name, id)；
- organization(normalized_name, id)；
- citation(cited_achievement_id, citing_achievement_id)；
- crawl_run(task_id, created_at)；
- crawl_failure(run_id, retryable, id)；
- graph_outbox_event(status, next_attempt_at, id)。

标题模糊检索在 10 万条规模下可以先使用受控条件、前缀索引和必要的数据库全文索引验证。若后续需要大规模中文全文搜索、复杂相关度或高亮，再通过容量和效果测试决定是否引入专用搜索引擎。

## 11. Neo4j 图模型

### 11.1 节点

| 标签 | 唯一业务键 | 主要属性 |
| --- | --- | --- |
| Achievement | businessId | title、achievementType、language、publicationDate、doi |
| Author | businessId | name、orcid |
| Institution | businessId | name、standardCode、countryCode |
| Venue | businessId | name、venueType、issn |
| Topic | businessId | name、code、path |

`businessId` 使用 MySQL 稳定主键的数值形式，API 再输出为字符串，不使用 Neo4j 内部 `elementId` 作为跨系统标识。所有系统受管节点统一设置 `aacvManaged=true`；成果节点额外保存 `projectionVersion`，投影版本和同步时间仍由 MySQL 的 `graph_projection_state` 作为权威记录。

### 11.2 关系

| 关系 | 起点 → 终点 | 属性 |
| --- | --- | --- |
| AUTHORED | Author → Achievement | `aacvManaged`、`achievementBusinessId` |
| AFFILIATED_WITH | Author → Institution | `aacvManaged`、`achievementBusinessId`、`institutionBusinessId` |
| PUBLISHED_IN | Achievement → Venue | `aacvManaged`、`achievementBusinessId` |
| HAS_TOPIC | Achievement → Topic | `aacvManaged`、`achievementBusinessId` |
| CITES | Achievement → Achievement | `aacvManaged`、`achievementBusinessId` |

`COOPERATES_WITH` 和 `INSTITUTION_COOPERATES_WITH` 在阶段 5 不物化；作者和机构合作由共同成果查询推导，是否物化留到阶段 7 按查询性能决定。

### 11.3 约束与索引

- 每种节点标签对 businessId 建唯一约束；
- Author.orcid 在非空值上建立索引；
- Achievement.doi、Achievement.year 和 Achievement.type 建查询索引；
- Institution.standardCode 和 Topic.code 建索引；
- Venue.issn 建索引；
- 图同步使用 businessId 执行 MERGE；
- 关系写入前必须先确保两端节点存在；
- 关系唯一性由端点业务 ID 和关系类型决定，必要时增加来源键。

### 11.4 查询边界

- 默认深度为 1；
- 普通图谱最大深度为 2；
- 最短路径必须指定最大跳数，建议不超过 6；
- 默认返回 100 个节点；
- 硬上限为 300 个节点；
- 查询必须包含中心节点或明确过滤条件；
- 禁止通过公共接口执行任意 Cypher；
- 达到上限时返回 truncated 标识和收窄条件建议；
- 图查询设置服务端超时。

## 12. MySQL 与 Neo4j 一致性设计

### 12.1 写入流程

1. 应用在 MySQL 事务内新增或更新标准实体，并单调推进 `graph_projection_state.desired_version`。
2. 同一事务写入只含成果 ID、期望版本和 `REFRESH` 类型的 `graph_outbox_event`，不复制业务快照。
3. 事务提交后，后台同步器认领待处理事件。
4. 同步器重新读取 MySQL 当前规范快照，使用固定参数化 Cypher 和期望版本执行单成果 Neo4j 写事务。
5. Neo4j 成功提交后，以独立 MySQL 短事务推进 `applied_version` 并将事件标记为完成。
6. 失败事件按退避策略更新 next_attempt_at。
7. 超过上限的事件进入死信表并产生告警。

### 12.2 并发认领

- 使用短事务认领事件；
- 通过状态、锁持有者和锁时间避免重复并发处理；
- 锁超时后允许其他工作线程重新认领；
- 即使发生重复认领，Neo4j 写入仍必须幂等；
- 同一 aggregate_id 的事件按 aggregate_version 有序处理；
- 旧版本事件不得覆盖新版本投影。

### 12.3 删除与合并

- 当前没有通用成果删除 API，阶段 5 不引入新的删除业务语义；
- 合并、撤销和人工字段修正只发布受影响成果的 `REFRESH`，消费时重新读取当前规范关系；
- 规范成员节点和失去关系的受管孤儿可由投影器清理，非 `aacvManaged=true` 数据始终不修改；
- 图同步完成前，查询接口应明确图数据可能存在短暂延迟；
- 提供按业务实体重建和全量重建图投影的运维能力。

### 12.4 对账

建议提供周期性对账任务：

- 比较 MySQL 有效实体数与 Neo4j 对应节点数；
- 抽样比较实体版本；
- 检查悬空关系；
- 检查长时间未完成 Outbox 事件；
- 对差异实体生成重建事件；
- 对账不得直接修改 MySQL 权威数据。

阶段 5 的初始回填、对账和全量重建复用 Spring Batch，以成果主键游标每页 100 条执行。`graph_maintenance_run` 的唯一活动锁阻止维护任务并发；全量重建期间 Quartz Outbox 消费暂停，图查询返回 `GRAPH_REBUILD_IN_PROGRESS`。全量重建只删除 `aacvManaged=true` 的业务投影，不删除未知节点、索引、约束或 Neo4j 卷。

## 13. API 设计

### 13.1 通用约定

- API 前缀为 /api/v1；
- 请求和响应使用 JSON；
- 时间使用 ISO 8601 UTC；
- 列表使用 page、size、sort 和明确过滤参数；
- size 默认 20，最大 100；
- 错误使用 application/problem+json；
- 错误响应包含稳定 errorCode、traceId 和安全描述；
- 不向客户端返回堆栈、SQL、Cypher 或敏感配置；
- 修改操作使用乐观锁版本或 If-Match 防止覆盖；
- 长时间导出使用异步任务；
- OpenAPI 作为接口契约。

### 13.2 主要接口

#### 认证

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | /api/v1/auth/login | 登录并创建会话 |
| POST | /api/v1/auth/logout | 注销当前会话 |
| GET | /api/v1/auth/me | 获取当前用户和权限 |

#### 数据源

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | /api/v1/sources | 分页查询OpenAlex/Crossref受控数据源 |
| POST | /api/v1/sources | 创建唯一的OpenAlex或Crossref数据源 |
| GET | /api/v1/sources/{sourceId} | 查看数据源详情 |
| PUT | /api/v1/sources/{sourceId} | 更新受控连接参数 |
| POST | /api/v1/sources/{sourceId}/enable | 启用 |
| POST | /api/v1/sources/{sourceId}/disable | 停用 |
| POST | /api/v1/sources/{sourceId}/probe | 受限连通性测试 |

#### 采集任务

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | /api/v1/crawl/tasks | 查询任务 |
| POST | /api/v1/crawl/tasks | 创建任务 |
| GET | /api/v1/crawl/tasks/{taskId} | 任务详情 |
| PUT | /api/v1/crawl/tasks/{taskId} | 更新未运行任务 |
| POST | /api/v1/crawl/tasks/{taskId}/trigger | 手动触发 |
| PUT | /api/v1/crawl/tasks/{taskId}/schedule | 配置每日计划 |
| POST | /api/v1/crawl/runs/{runId}/pause | 暂停运行 |
| POST | /api/v1/crawl/runs/{runId}/resume | 恢复运行 |
| POST | /api/v1/crawl/runs/{runId}/cancel | 取消运行 |
| POST | /api/v1/crawl/runs/{runId}/retry-failures | 有限重试失败内容 |
| GET | /api/v1/crawl/runs/{runId} | 查询进度和统计 |
| GET | /api/v1/crawl/runs/{runId}/failures | 查询失败记录 |

#### 成果目录

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | /api/v1/catalog/achievements | 八类组合筛选成果 |
| GET | /api/v1/catalog/achievements/{achievementId} | 成果详情和来源追溯 |
| GET | /api/v1/catalog/{collection} | 作者、机构、载体或主题查询 |
| GET | /api/v1/catalog/{collection}/{entityId}/achievements | 查询实体关联成果 |

#### 数据治理

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | /api/v1/duplicate-candidates | 查询待审核重复候选 |
| GET | /api/v1/duplicate-candidates/{id} | 查询候选与有限匹配证据 |
| GET | /api/v1/duplicate-candidates/{id}/comparison | 对照当前字段与来源明确版本关系，需GOVERNANCE_READ |
| POST | /api/v1/duplicate-candidates/{id}/accept | 接受合并 |
| POST | /api/v1/duplicate-candidates/{id}/reject | 拒绝合并 |
| POST | /api/v1/merge-decisions/{id}/revert | 受控撤销人工合并 |
| POST | /api/v1/catalog/achievements/{id}/field-overrides | 创建或更新成果字段人工修正 |
| POST | /api/v1/catalog/achievements/{id}/field-overrides/{revisionId}/revert | 撤销当前人工字段修正 |
| GET | /api/v1/quality-metrics | 按来源、运行和指标查询质量度量 |
| GET | /api/v1/quality-metrics/{id} | 查询质量度量和有限问题样本 |

#### 图谱与统计

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | /api/v1/graph/subgraph | 加载中心节点局部子图 |
| GET | /api/v1/graph/path | 查询受限最短路径 |
| GET | /api/v1/graph/sync-status | 查询图同步状态 |
| GET | /api/v1/analytics/overview | 总览指标 |
| GET | /api/v1/analytics/trends | 年度趋势 |
| GET | /api/v1/analytics/distributions | 类型、机构、来源和主题分布 |
| GET | /api/v1/analytics/collaboration | 合作统计 |

#### 导出与运维

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | /api/v1/exports | 创建异步导出 |
| GET | /api/v1/exports/{id} | 查询导出状态 |
| GET | /api/v1/exports/{id}/download | 下载已完成文件 |
| GET | /api/v1/operations/audits | 查询审计记录 |
| GET | /api/v1/operations/overview | 查询管理员运维总览 |
| GET | /api/v1/operations/alerts | 分页查询系统内告警 |
| POST | /api/v1/operations/alerts/{id}/acknowledge | 确认系统内告警 |
| GET | /api/v1/operations/graph-events | 查询同步积压和失败 |
| POST | /api/v1/operations/graph-events/{id}/replay | 按MySQL当前状态重放指定同步事件 |

### 13.3 图谱响应模型

局部图响应建议包含：

- nodes：节点 ID、类型、显示标签和有限摘要属性；
- edges：关系 ID、类型、起点、终点和有限属性；
- rootNodeId：中心节点；
- truncated：是否达到限制；
- appliedLimits：实际深度和节点上限；
- syncedAt：图投影更新时间；
- traceId：请求追踪 ID。

不得在通用图响应中返回摘要全文、凭据、原始响应或所有内部字段。

## 14. 前端设计

### 14.1 页面模块

| 页面 | 主要内容 |
| --- | --- |
| 登录页 | 登录、错误提示 |
| 工作台 | 核心数量、任务状态、数据质量和同步积压 |
| 数据源管理 | 数据源列表、编辑、启停、连通性测试 |
| 采集任务 | 任务创建、计划配置、运行进度、暂停、恢复、重试 |
| 成果检索 | 组合过滤、分页列表、导出 |
| 成果详情 | 标准信息、作者、机构、主题、引用和来源追溯 |
| 作者与机构 | 基础信息、关联成果和合作关系 |
| 知识图谱 | 中心节点、过滤器、布局、节点详情和路径查询 |
| 统计分析 | 趋势、分布、排行和合作统计 |
| 数据治理 | 重复候选、匹配证据和人工处理 |
| 运行监控 | 失败、图同步事件、审计和健康状态 |
| 用户管理 | 用户、角色和账号状态 |

### 14.2 图谱交互

- 首次只加载中心节点和第一层关系；
- 用户主动展开节点；
- 展开前显示预计增加数量或采用服务端限制；
- 支持按关系、年份、成果类型和节点类型过滤；
- 节点颜色只表达实体类型，不同时编码过多维度；
- 图例始终可见；
- 节点详情使用侧栏，避免离开分析上下文；
- 达到节点上限时停止自动展开并提示缩小范围；
- 大图不直接全部渲染；
- 提供表格替代视图，满足可访问性和精确读取需求；
- 图形不使用颜色作为唯一信息载体。

### 14.3 前端状态边界

- 服务端保存权威筛选、权限和任务状态；
- 前端只缓存当前页面必要数据；
- 登录会话通过 HttpOnly Cookie 管理；
- 修改请求携带 CSRF 令牌；
- 路由权限只用于用户体验，后端必须再次授权；
- 外部文本按纯文本展示，禁止直接注入 HTML。

### 14.4 阶段6业务前端实现边界

> 说明：本节保留阶段6的历史实现边界。前端已于后续整体改造中将 UI 组件库由 Element Plus 迁移到 shadcn-vue（reka-ui）+ Tailwind CSS，并引入三层设计令牌与深浅双主题；当前权威架构见 14.5。本节中“Element Plus”“`vLoading`”等描述仅作历史参考。

阶段6使用Vue 3、TypeScript、Vue Router、Element Plus、原生fetch、Vitest和Playwright实现业务前端。Element Plus按组件和`vLoading`指令显式引入，不使用自动导入插件；未引入Pinia或Axios。Vite分别将`/api`和`/actuator`原样代理到本机后端，避免业务API路径被改写。

会话只保存在轻量Vue响应式模块中，浏览器负责HttpOnly Cookie。统一API边界设置12秒默认超时、解析`application/problem+json`、对所有非安全HTTP方法获取并附加服务端返回的CSRF请求头；登录成功后清除匿名令牌缓存，使下一次写操作获取认证会话的新令牌。401会使内存会话失效并进入会话过期页，403进入无权限页，409要求刷新后重试；网络和超时错误、加载状态和空结果均在页面明确展示。前端路由权限只隐藏入口和阻止误入，服务端授权仍是最终边界。

阶段6已实现登录、权限菜单、工作台、成果及四类实体目录、成果详情和来源追溯、数据源、采集任务/计划/运行/失败、重复候选治理、字段人工覆盖、质量指标样本和用户管理。阶段7批次7.2至7.7已实现局部知识图谱、MySQL统计分析、异步导出、目录导出下载、运维总览、系统内告警和运行监控页面。

### 14.5 前端设计系统与组件架构（整体改造后）

前端在保持路由、权限、服务层与后端 `/api/v1/*` 契约不变的前提下完成了一次整体改造，技术栈与分层如下。

#### 14.5.1 技术栈

| 领域 | 选型 | 说明 |
| --- | --- | --- |
| 框架 | Vue 3.5 + TypeScript + Vite | 组合式 API，`<script setup>` |
| 组件库 | shadcn-vue 模式（reka-ui + Tailwind CSS v4） | 组件源码内置于 `src/components/ui/`，copy-paste 模式，已移除 Element Plus |
| 样式 | Tailwind CSS v4（`@tailwindcss/vite`）+ CSS 变量令牌 | 无 `tailwind.config.js`，主题经 `@theme inline` 映射 |
| 数据表 | `@tanstack/vue-table` v8 | 封装为 `DataTable` 业务组件，服务端分页/排序 |
| 表单校验 | `vee-validate` + `zod`（`@vee-validate/zod`） | 用于数据源等结构化表单 |
| 图标 | `lucide-vue-next` | 替代 `@element-plus/icons-vue` |
| 交互工具 | `@vueuse/core` | 主题持久化、v-model 代理等 |
| 图表/图谱 | ECharts 6、Cytoscape 3 | 经 `EChartCanvas`/`GraphCanvas` 主题化封装 |
| 通知 | `vue-sonner` | 替代 `ElMessage` |
| 测试 | Vitest、Playwright | E2E 依赖 ARIA 角色/文本/`label:has-text` 结构，与组件库解耦 |

#### 14.5.2 三层设计令牌

令牌定义于 `src/styles/tokens.css`，全部颜色使用 HSL 分量以便透明度组合：

- Primitive（原始值）：调色板（blue/cyan/emerald/amber/rose/violet/slate）、圆角、间距、字体、字号（`--font-size-*`，独立命名空间避免与 Tailwind `--text-*` 冲突）、行高、字距、阴影、动效曲线；
- Semantic（语义别名）：`[data-theme='light']` 与 `[data-theme='dark']` 各一套，含 `--background/--foreground/--primary/--muted/--accent/--destructive/--success/--warning/--info/--border/--ring/--sidebar*/--chart-1..6/--graph-*/--table-*/--status-*` 等；
- Component（组件级）：`--button-*/--card-*/--input-*/--table-*/--dialog-*/--badge-*/--nav-item-*`，均以 `var()` 引用语义层。

`src/styles/index.css` 通过 `@theme inline` 将语义令牌暴露为 Tailwind 工具类（`bg-background`、`text-foreground`、`border-border` 等），并在 `@layer base` 定义全局基础样式、`:focus-visible` 环、滚动条与 `prefers-reduced-motion` 降级。

#### 14.5.3 主题切换

- `src/composables/useTheme.ts` 基于 `@vueuse/core` 的 `useColorMode`，模式 `light|dark|auto`，持久化到 `localStorage['aacv-theme']`，同步 `<html data-theme>`、`.dark` class 与 `color-scheme`；
- `index.html` 内联防 FOUC 脚本在首帧前应用持久化主题或系统偏好；
- `src/composables/useChartTheme.ts` 实时解析 CSS 令牌为图表色板，`EChartCanvas`/`GraphCanvas` 监听 `isDark` 变化重渲染，使图表与图谱随主题切换。

#### 14.5.4 组件分层

- `src/components/ui/`：约 30 个 shadcn-vue 风格原语（button、card、input、label、select、dialog、alert-dialog、dropdown-menu、tabs、sheet、tooltip、popover、progress、switch、checkbox、table、form、badge、alert、separator、skeleton、scroll-area、avatar、breadcrumb、collapsible、toggle、toggle-group、radio-group、sonner 等），统一用 `cn()`（`src/lib/utils.ts`）合并类名、`class-variance-authority` 管理变体；
- `src/components/business/`：约 21 个业务组件（`PageHeader`、`PanelSection`、`DataTable`、`FilterBar`/`FilterField`、`StatCard`、`StatusPill`、`EmptyState`、`ErrorState`、`LoadingSkeleton`、`JsonEvidence`、`EntityLinks`、`LiveLogPanel`、`ConfirmDialog`、`ChartFrame`、`GraphCanvas`、`ThemeToggle`、`CommandPalette`、`Breadcrumb`、`UserMenu`、`AppSidebar`、`AppTopbar`）；
- `src/config/nav.ts`：集中式导航配置（标签、图标、权限、命令面板关键词），侧栏与命令面板复用同一权限过滤逻辑。

#### 14.5.5 布局外壳与可访问性

- `BusinessLayout` 由可折叠桌面侧栏（宽 240px/折叠 64px，状态持久化）+ 移动端 `sheet` 抽屉 + 粘性顶栏（面包屑、命令面板入口、主题切换、通知、用户菜单）组成；
- 快捷键：Ctrl+K 命令面板、Ctrl+B 折叠侧栏、Esc 关闭浮层；提供 skip-to-content 链接；
- `FilterField` 以 `<label>` 包裹控件，保留 E2E 依赖的 `label:has-text("…") input` 结构；状态用 `StatusPill` 的文本+颜色双通道表达，不以颜色为唯一载体；异步区域使用 `aria-live`；全局尊重 `prefers-reduced-motion`。

#### 14.5.6 兼容性边界

改造未修改 `src/router/index.ts`、`src/services/{api,business,session,health}.ts`、`src/types/api.ts` 与 `src/utils/{motion,format,graph,export-filter}.ts` 的公开契约；后端代码、`deploy/` 与 `docs/openapi.yaml` 不受影响。

### 14.6 WebUI 视觉与动效基线

- 业务界面提供深浅双主题（默认跟随系统），统一采用固定左侧权限导航、粘性顶栏、紧凑数据卡片和高对比状态色；颜色均取自语义令牌，两套主题下正文对比度不低于 4.5:1。工作台只组合当前账号有权读取的统计、采集和运维接口；任何接口失败均按区域降级，不使用虚构指标补位。
- ECharts 图表固定使用 Canvas 渲染，趋势线和条形图首次渲染控制在 600 毫秒内；折线图启用吸附轴指示器与数据点聚焦。Cytoscape.js 继续承担最多 300 个节点的图谱渲染，布局动画在 800 毫秒内完成，并支持一至二度关系聚焦、机构节点平滑缩放和展开节点渐次入场。
- 统计筛选期间保留旧图形并降至 40% 透明度、增加 1 像素模糊，响应返回后使用更新动画恢复；合作排行通过稳定业务标识和 Vue `TransitionGroup` 执行 FLIP 重排。
- 数据源初次读取使用轻量 shimmer 骨架；采集运行详情按 1.5 秒串行轮询，连续三次失败后停止，展示脉冲状态、600 毫秒计数过渡和最多 80 行的活动流。页面卸载或弹窗关闭时必须清理轮询、动画帧和图实例。
- 所有非必要动画遵守 `prefers-reduced-motion: reduce`，关闭图表、图谱、计数、滚动和装饰性过渡；文字、图标和结构信息不能只依赖颜色表达状态。
- 图标使用 `lucide-vue-next`；品牌使用现有 `frontend/public/favicon.svg`。UI 层采用 shadcn-vue 模式（reka-ui + Tailwind CSS v4），组件源码内置于仓库；未引入 Pinia 或 Axios，会话仍保存在轻量 Vue 响应式模块中。

## 15. 安全设计

### 15.1 认证与授权

- 使用 Spring Security；
- 密码使用强哈希算法和随机盐；
- 会话由服务端维护，Cookie 使用 HttpOnly、SameSite 和生产 Secure；
- 修改请求启用 CSRF 防护；
- 登录失败响应不泄露账号是否存在；
- 连续登录失败实施有界限速；
- 权限在控制器入口和应用服务用例层校验；
- 导出、人工合并、数据源修改和任务控制属于高审计操作；
- 停用用户后使其现有会话失效。

### 15.2 数据源与 SSRF 防护

- 数据源由管理员创建；
- 使用已登记主机和协议；
- 禁止 file、ftp、gopher 等非批准协议；
- 请求前后都验证重定向目标；
- 阻止未授权内网和云元数据地址；
- 不允许关闭 TLS 验证；
- 不把来源响应头直接转发给用户；
- 对压缩响应设置解压后大小上限；
- 对 XML 禁用外部实体；
- 对 HTML 和 JSON 设置解析深度、长度和超时限制。

如业务确实需要采集内部机构网站，应通过显式允许列表批准具体主机和网段，而不是关闭 SSRF 防护。

### 15.3 凭据与日志

- 生产凭据通过环境变量、Docker Secret 或外部密钥系统提供；
- 仓库仅保存配置项名称和无敏感示例；
- 数据源凭据在数据库中保存时必须加密，密钥不与密文存放在同一数据库；
- 日志脱敏 Authorization、Cookie、Token、Password 和连接字符串；
- 错误响应不包含堆栈；
- 审计日志防止普通用户修改；
- 导出文件使用不可预测标识并设置过期时间。

## 16. 性能与容量设计

### 16.1 建议容量

| 项目 | 建议基线 |
| --- | --- |
| 成果 | 10 万 |
| 作者 | 30 万以内 |
| 机构 | 5 万以内 |
| 图关系 | 100 万 |
| 单次图节点 | 默认 100，最大 300 |
| 批处理块大小 | 通过测试确定，初始建议 100 至 500 |
| API 单页 | 默认 20，最大 100 |
| 后端实例 | 1 |
| 图同步工作线程 | 小规模有界线程池，按压测确定 |

### 16.2 优化顺序

1. 建立正确索引和查询计划；
2. 避免按成果、作者或机构逐条执行重复 SQL，优先使用批量查询和集合映射；
3. 列表使用投影 DTO，不加载大字段；
4. 统计查询使用受控聚合和必要的汇总表；
5. 批处理分块并减少逐条往返；
6. Neo4j 查询限制深度、节点数和超时；
7. 使用短生命周期的本地缓存保存低风险字典；
8. 完成容量测试后再评估 Redis、搜索引擎、消息队列或服务拆分。

### 16.3 降级策略

- Neo4j 不可用时，成果列表、详情和 MySQL 统计仍可使用；
- 图谱入口显示暂不可用和最近同步时间；
- 外部数据源不可用时，不影响已有数据查询；
- 单个数据源连续失败时暂停该来源计划，不暂停其他来源；
- 大范围图查询被拒绝并返回可操作的收窄建议；
- 导出任务超过限制时拒绝创建，不在同步请求中生成大文件。

## 17. 异常处理

### 17.1 错误分类

| 分类 | 示例 | 对外处理 |
| --- | --- | --- |
| VALIDATION | 参数、范围或状态不合法 | 400 和字段级错误 |
| AUTHENTICATION | 未登录或会话失效 | 401 |
| AUTHORIZATION | 权限不足 | 403 |
| NOT_FOUND | 业务实体不存在 | 404 |
| CONFLICT | 重复任务、版本冲突、非法状态迁移 | 409 |
| RATE_LIMITED | 请求或任务频率超限 | 429 |
| SOURCE_UNAVAILABLE | 外部来源临时不可用 | 任务内重试，对管理端展示 |
| PARSE_FAILED | 来源结构或内容异常 | 单条隔离、运行统计 |
| STORAGE_UNAVAILABLE | MySQL 或 Neo4j 不可用 | 503，内部补偿 |
| INTERNAL | 未预期错误 | 500 和 traceId |

### 17.2 错误处理原则

- 不吞掉异常；
- 只在能够恢复的边界重试；
- 事务失败不推进检查点；
- 单条坏记录隔离后允许批次继续；
- 系统性解析失败触发熔断式暂停数据源；
- 所有失败都有稳定错误码和关联 ID；
- 日志记录技术细节，对外只返回安全描述；
- 资源使用 try-with-resources 或框架生命周期可靠释放。

## 18. 可观测性设计

### 18.1 日志

结构化日志建议字段：

- timestamp；
- level；
- service；
- traceId；
- userId；
- sourceId；
- taskId；
- runId；
- batchStep；
- recordId；
- errorCode；
- durationMs。

不得记录完整凭据、会话 Cookie、原始 Authorization 头、完整摘要正文或未脱敏外部响应。

### 18.2 指标

| 类别 | 指标示例 |
| --- | --- |
| API | 请求数、P95 延迟、4xx、5xx |
| 采集 | 请求数、限流数、超时数、来源状态码 |
| 解析 | 成功率、字段缺失率、解析器版本 |
| 数据 | 新增、更新、重复候选、人工审核积压 |
| 任务 | 运行数、成功率、耗时、恢复次数 |
| 图同步 | 待处理数、失败数、最大积压时间 |
| 数据库 | 连接池使用率、慢查询、事务失败 |
| JVM | 堆内存、GC、线程、CPU |

### 18.3 健康检查

- liveness：应用进程是否存活，不依赖外部数据源；
- readiness：MySQL 是否可用，必要的迁移是否完成；
- Neo4j 状态单独暴露，不因图数据库短暂不可用使全部 API 下线；
- 数据源连通性不属于全局 readiness；
- 详细健康信息仅管理员可见。

## 19. 本地运行与未来部署设计

### 19.1 第一阶段本地运行拓扑

~~~mermaid
flowchart LR
    Browser[本机或经确认的内网浏览器]
    Frontend[Vue 3 / Vite]
    App[Spring Boot 应用]
    MySQL[(本机 MySQL80<br/>当前8.0.41/基线8.0.42)]
    Neo4j[(Neo4j 5.26 Docker)]
    Sources[OpenAlex / Crossref]

    Browser -->|HTTP，仅本地或内网开发| Frontend
    Frontend -->|/api| App
    App -->|localhost:3306| MySQL
    App --> Neo4j
    App -->|HTTPS| Sources

    Neo4j --- Neo4jVolume[(Neo4j 本地持久卷)]
    App --- ExportDirectory[(本地临时导出目录)]
~~~

### 19.2 本地组件职责

| 组件 | 运行方式 | 责任 |
| --- | --- | --- |
| frontend | Vite 本地开发服务 | 页面、图表和接口代理 |
| backend | Maven 或 IDE 直接启动 | REST API、调度、批处理、图同步 |
| MySQL80 | Windows 本机服务 | 权威业务数据库，当前版本8.0.41，兼容基线8.0.42，端口3306 |
| neo4j | Docker 容器 | Neo4j 5.26 Community 图投影数据库 |

日常开发不创建新的MySQL容器，继续使用现有MySQL80和`aacv_system`；阶段8容量、性能与故障验收是唯一例外，使用固定的MySQL 8.0.42隔离容器和独立数据库。账号和密码不得写入仓库或文档。

### 19.3 本地配置

- application.yml 保存无敏感默认值；
- application-dev.yml 仅保存本地非敏感配置；
- 数据库密码、Neo4j 密码和其他凭据通过未提交的本地环境变量或安全配置注入；
- 不提交真实 .env；
- 启动时校验必需配置，缺失时明确失败；
- Flyway 在后端启动时维护 aacv_system 数据库结构；
- Neo4j 容器使用固定的 5.26 版本标签和持久卷，不使用 latest；
- 本地 HTTP 只用于 localhost 或经用户确认的同一内网开发访问；
- OpenAlex 和 Crossref 适配器使用固定官方基础地址，不允许普通用户配置任意目标主机。

### 19.4 未来服务器部署约束

服务器部署不属于第一阶段开发基线。决定部署时必须重新确认：

- Linux 发行版、CPU、内存、SSD 容量和网络条件；
- MySQL 使用现有实例还是容器；
- 域名、证书、反向代理和访问控制；
- 所有页面和 API 强制使用 HTTPS；
- Docker Compose 服务、健康检查、重启策略和持久卷；
- 备份目标、异地副本和恢复演练；
- 正式容量测试和回滚方案。

### 19.5 本地资源建议

- 建议开发机至少具备 4 核 CPU、8 GB 内存和 SSD；
- Neo4j 容器需要显式限制并配置堆和页面缓存；
- MySQL 数据、Neo4j 持久卷、导出文件和备份目录应分别设置容量边界；
- 10 万成果和 100 万关系的验收需要在实际开发机上完成容量测试，不能仅依据配置推断。

## 20. 备份、恢复与升级

### 20.1 备份

- MySQL：本地开发基线为每日逻辑备份，保留 7 个每日备份和 4 个每周备份；
- Neo4j：Community Edition 以从 MySQL 重建图投影为主要恢复方案，可以补充周期性离线 dump；
- 本地开发 RPO 为 24 小时，RTO 为 4 小时；
- 计划备份目录为 E:\AACV_System_Backups，该目录不得位于 Git 仓库内，创建和容量确认留到开发计划实施阶段；
- 正式部署时至少保留一个不同磁盘或不同主机的备份副本；
- 配置：备份无敏感配置模板和部署清单；
- 密钥：由外部密钥系统按其流程备份，不与数据库备份混放；
- 导出临时文件不作为业务备份；
- 备份产物需要完整性校验、访问控制和保留策略；跨主机或介质复制时还需要安全传输。

### 20.2 恢复顺序

1. 恢复 MySQL 权威数据；
2. 验证 Flyway 版本和应用兼容性；
3. 恢复 Neo4j，或从 MySQL 全量重建图投影；
4. 恢复未完成 Outbox 事件；
5. 启动作业前确认任务状态和检查点；
6. 执行数据量、抽样实体和图关系对账；
7. 恢复定时任务。

### 20.3 升级原则

- 不使用已结束支持的 Neo4j 4.4 作为新生产环境；
- 依赖升级与业务变更分开；
- 数据库迁移先在备份副本演练；
- 生产迁移准备回滚方案；
- Neo4j 跨主要版本迁移遵循官方检查点和备份要求；
- 不假设数据库可以直接降级；
- 升级后执行任务恢复、图同步和关键查询回归测试。

## 21. 测试设计

### 21.1 测试层级

| 层级 | 内容 |
| --- | --- |
| 单元测试 | 标准化、指纹、状态机、重试分类、权限规则 |
| 模块测试 | 控制器、应用服务、MyBatis Mapper、Neo4j Repository |
| 适配器契约测试 | 固定来源样例、分页、限流、字段缺失、结构变化 |
| 集成测试 | Testcontainers MySQL、Neo4j、Flyway、Outbox |
| 恢复测试 | 作业中断、检查点恢复、锁超时、重复事件 |
| 安全测试 | 越权、CSRF、SSRF、XSS、安全响应和日志脱敏 |
| 前端组件测试 | 筛选器、分页、错误状态、图例和详情侧栏 |
| 端到端测试 | 登录、任务创建、采集、检索、图谱和导出 |
| 性能测试 | 10 万成果、100 万关系基线下的列表和图查询 |
| 备份恢复测试 | MySQL 恢复、图重建和对账 |

### 21.2 关键边界用例

- 空搜索条件和最大分页；
- 空标题、无 DOI、只有年份；
- 同 DOI 不同标题；
- 同名作者不同机构；
- 来源记录重复返回；
- 请求超时、429、401、403、5xx；
- robots 禁止；
- 无效 JSON、XML 外部实体、超大响应；
- 批次中单条失败；
- 暂停、取消和应用进程异常退出；
- MySQL 提交失败；
- Neo4j 不可用和恢复；
- Outbox 重复消费和乱序事件；
- 合并后撤销；
- 图查询达到节点或深度上限；
- 普通用户访问管理接口；
- 导出过大或文件过期。

### 21.3 建议验证命令

以下是未来工程初始化后的建议命令，不表示本轮已经执行：

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml verify
npm --prefix .\frontend run test
npm --prefix .\frontend run build
docker compose -f .\deploy\compose.yaml config
~~~

### 21.4 开发页面样例数据

页面联合验收使用 `tools/development/Initialize-RenderingSampleData.ps1` 和 `rendering-sample-data.sql`，与 Flyway 迁移、阶段8容量数据和正式采集链路隔离。工具仅允许连接本机 `aacv_system`，要求 Flyway 恰好完成 V1 至 V14 且已有有效管理员，通过安全凭据提示连接，不保存数据库密码。样例业务写入使用单个事务，所有记录均带专用名称、外部标识、DOI、运行 UUID、事件 UUID 或审计 traceId，因此可以幂等重放且不会按模糊名称覆盖既有业务数据。

MySQL 仍是样例成果的唯一权威源。图样例不直接写 Neo4j，而是创建版本化 `graph_projection_state` 和 `graph_outbox_event`，由现有消费者投影；其中保留一个明确标记的模拟死信，用于验证运行监控、告警确认和受控重放。样例还覆盖目录与详情、双来源追溯、实体列表、年度/类型/来源/机构/主题统计、作者和机构合作、治理候选、质量问题样本、采集失败、维护记录及审计列表。工具不创建账号、不触发外部 API，也不提供自动清库流程。

## 22. 实施阶段

### 阶段一：工程基础

- 初始化后端、前端和部署目录；
- 接入 MySQL、MyBatis、Neo4j、Flyway、统一异常、认证和审计；
- 建立模块边界和自动化测试基础；
- 完成开发环境 Compose。

### 阶段二：采集闭环

- 数据源和任务管理；
- Quartz 与 Spring Batch；
- 第一个结构化数据源适配器；
- 原始记录、标准化和成果保存；
- 任务监控与失败重试。

### 阶段三：数据治理

- 第二个数据源适配器；
- DOI 和来源标识去重；
- 作者、机构消歧；
- 重复候选和人工审核；
- 数据质量指标。

### 阶段四：图谱与分析

- Outbox 图同步；
- Neo4j 约束和查询；
- 成果、作者、机构、主题和引用图；
- 统计分析和前端图表；
- 图同步对账。

### 阶段五：验收与交付

- 端到端、性能和安全验证；
- 备份恢复演练；
- 部署、运维和使用文档；
- 按验收标准完成试运行。

每个阶段都应遵循“需求确认、设计、实现、测试、复核”的顺序，不在前一阶段关键验收失败时扩大范围。

## 23. 需求追溯

| 需求范围 | 设计章节 |
| --- | --- |
| FR-AUTH | 第 6、13、15 节 |
| FR-SOURCE | 第 6、7、13、15 节 |
| FR-TASK | 第 8、13、17 节 |
| FR-CRAWL | 第 7、8、15 节 |
| FR-DATA | 第 9、10 节 |
| FR-CATALOG | 第 6、10、13 节 |
| FR-GRAPH | 第 11、12、13、14 节 |
| FR-ANALYTICS | 第 6、13、16 节 |
| FR-OPS | 第 17、18、19 节 |
| NFR-PERF | 第 11、16、21 节 |
| NFR-REL | 第 8、12、17、20、21 节 |

### 23.1 阶段3实现追溯

| 需求 | 阶段3实现证据 | 验证证据 |
| --- | --- | --- |
| `FR-SOURCE-001` 至 `FR-SOURCE-005` | 固定OpenAlex来源、连接设置、启停和受控探测；不接受任意目标URL | `DataSourceServiceTests`、`SourceCrawlPersistenceTests` |
| `FR-TASK-001` 至 `FR-TASK-008` | 有界任务、一次性触发、显式时区每日计划、状态机、暂停/恢复/取消和父子失败重试 | `CrawlTaskServiceTests`、`CrawlRunServiceTests`、`CrawlRunStateMachineTests`、`CrawlRecoveryServiceTests` |
| `FR-CRAWL-001` 至 `FR-CRAWL-007` | OpenAlex游标适配器、原始快照、字段解析、标准化、单页事务和检查点 | `OpenAlexResponseParserTests`、`OpenAlexDataSourceAdapterTests`、`IngestionPipelineIntegrationTests` |
| `FR-DATA` | OpenAlex稳定ID和DOI确定性关联，作者/机构/载体/主题及关系幂等写入 | `OpenAlexWorkNormalizerTests`、`IngestionPipelineIntegrationTests` |
| `FR-CATALOG-001` 至 `FR-CATALOG-003` | 八类组合筛选、稳定分页、实体关联入口和包含来源记录的成果详情 | `IngestionPipelineIntegrationTests`、`OpenApiDocumentTests` |
| `FR-OPS` | 运行统计、失败安全分页、审计和90天Payload小批清理 | `IngestionPipelineIntegrationTests`、`SecurityIntegrationTests` |
| `NFR-REL` | Spring Batch Chunk、业务检查点、Quartz JDBC JobStore、异常退出协调恢复 | `OpenAlexBatchOrchestrationIntegrationTests`、`OpenAlexPageItemReaderTests`、`CrawlRecoveryServiceTests` |

阶段3只关闭OpenAlex单源采集闭环对应的需求项。Crossref、跨来源治理、Outbox、Neo4j业务图和业务前端仍按后续阶段保持未实现，不能由本表推断为已满足。

### 23.2 阶段4实现追溯

| 需求 | 阶段4实现证据 | 验证证据 |
| --- | --- | --- |
| `FR-SOURCE-001` 至 `FR-SOURCE-005` | `SourceType`、固定身份和适配器注册表最小通用化；Crossref固定为`https://api.crossref.org`，不接受任意主机或重定向 | `DataSourceServiceTests`、`CrossrefDataSourceAdapterTests`、`CrossrefHttpTransportTests` |
| `FR-TASK-001` 至 `FR-TASK-008` | 参数版本1保持OpenAlex语义，版本2承载Crossref过滤；两个来源复用状态机、Quartz、Batch和检查点；V12更正每日模式为固定范围复查，持久化结束原因与额度恢复时间 | `CrawlTaskServiceTests`、`SourceCrawlPersistenceTests`、`OpenAlexBatchOrchestrationIntegrationTests`、`OpenAlexOnlineAcceptanceIT` |
| `FR-CRAWL-001` 至 `FR-CRAWL-008` | Crossref `/works`、不透明游标、固定参数链、结果总量对账、限流/重试/响应上限、字段缺失和安全错误分类；JATS/HTML摘要只保留于受限原始快照 | `CrossrefResponseParserTests`、`CrossrefDataSourceAdapterTests`、`CrossrefHttpTransportTests` |
| `FR-DATA-001` 至 `FR-DATA-010` | DOI、ORCID、ROR、ISSN确定性关联；无稳定标识按来源位置/文本证据生成候选；逻辑合并不删除实体或来源，决定和字段修正可受控撤销 | `CrossrefFusionIntegrationTests`、`GovernanceServiceTests`、`GovernancePersistenceIntegrationTests` |
| `FR-CATALOG-001` 至 `FR-CATALOG-003` | 固定字段优先级与导入顺序无关，人工修正优先；详情返回字段来源、人工覆盖状态和双源追溯，DOI引用支持后到回填 | `IngestionPipelineIntegrationTests`、`GovernancePersistenceIntegrationTests`、`CrossrefFusionIntegrationTests`、`OpenApiDocumentTests` |
| `FR-OPS`当前阶段质量定位 | 11项质量指标与单页采集同事务写入，记录任务标识并可按来源、运行定位有限问题样本 | `QualityMetricIntegrationTests`、`SecurityIntegrationTests` |
| `NFR-REL` | V8不可变迁移、单页事务回滚、幂等重跑、闭区间检查点和现有恢复路径复用 | `FlywayMigrationTests`、`IngestionPipelineIntegrationTests`、`OpenAlexBatchOrchestrationIntegrationTests` |

阶段4只实现Crossref与MySQL数据治理。Outbox、Neo4j业务图投影/查询/对账、业务前端、服务器部署及未列出的完整MVP能力仍未实现；验收命令和外部在线证据见`docs/stage4-acceptance.md`。

### 23.3 阶段5实现追溯

| 需求 | 阶段5实现证据 | 验证证据 |
| --- | --- | --- |
| `FR-GRAPH-001`、`FR-GRAPH-002` | V9投影状态/Outbox、事务内刷新端口、固定Cypher、五类节点和五类关系、版本守卫 | `GraphOutboxPersistenceTests`、`GraphProjectionIntegrationTests` |
| `FR-GRAPH-003` 至 `FR-GRAPH-005` | 固定中心类型和关系白名单，深度最大2，默认100/硬上限300，稳定截断响应 | `GraphQueryIntegrationTests`、`OpenApiDocumentTests` |
| `FR-GRAPH-008`、`FR-GRAPH-009` | 最大6跳确定性最短路径、3秒查询事务超时、同步时间/延迟/积压状态 | `GraphQueryIntegrationTests` |
| `FR-OPS-001`、`FR-OPS-003`、`FR-OPS-004` 图同步范围 | 事件安全分页、死信重放、维护运行、三个图权限、CSRF和审计 | `SecurityIntegrationTests`、`GraphOutboxPersistenceTests`、`GraphMaintenanceIntegrationTests` |
| `NFR-REL-003` 至 `NFR-REL-006` | 有界退避、租约恢复、故障积压补偿、游标维护和资源受管 | `GraphOutboxPersistenceTests`、`GraphProjectionIntegrationTests`、`GraphMaintenanceIntegrationTests` |

阶段5实现的是可重建图投影和受限后端接口。业务图前端、合作网络可视化、统计分析、导出、最终容量性能、备份恢复和服务器部署仍按后续阶段保留；验收边界见`docs/stage5-acceptance.md`。

### 23.4 阶段6实现追溯

| 需求范围 | 阶段6实现证据 | 验证证据 |
| --- | --- | --- |
| `FR-AUTH` | HttpOnly Cookie会话、登录/退出/当前用户、CSRF写请求、会话过期和权限路由 | `api.test.ts`、`session.test.ts`、`index.test.ts`、`stage6.spec.ts` |
| `FR-CATALOG-001` 至 `FR-CATALOG-003` | 八类成果筛选、分页、详情、作者/机构/载体/主题实体和关联成果 | 前端生产构建、Playwright成果目录空状态流程、后端OpenAPI与集成测试 |
| `FR-SOURCE`、`FR-TASK`、`FR-CRAWL` | 权限区分的数据源管理/探测、任务参数、调度、触发、运行控制和失败明细 | 前端生产构建、后端安全/OpenAPI/采集回归 |
| `FR-DATA`阶段4治理范围 | 重复候选证据、接受/拒绝/撤销、字段人工覆盖/撤销和乐观锁冲突提示 | 前端生产构建、后端治理与权限回归 |
| 阶段4质量定位 | 质量指标筛选、比率和有限问题样本 | 前端生产构建、后端质量指标回归 |
| `FR-AUTH-004`管理员范围 | 用户分页、创建、启停、角色替换和密码重置 | 前端生产构建、后端身份与安全回归 |

阶段6只关闭日常业务前端工作流。知识图谱和合作关系可视化、统计分析、导出、图同步与运行监控页面仍属于阶段7；最终容量、性能、备份恢复和本地联合验收仍属于阶段8。验收命令与证据边界见`docs/stage6-acceptance.md`。

### 23.5 阶段7契约冻结

阶段7使用`docs/openapi.yaml` 7.0.0作为统计、导出和系统运维能力的冻结契约。`ANALYTICS_READ`、`EXPORT_CREATE`和`EXPORT_READ`授予三个现有角色；导出状态和下载还必须执行创建者或管理员对象级校验。`OPERATIONS_READ`和`ALERT_MANAGE`仅授予管理员。导出创建和告警确认继续要求CSRF，所有新接口保持`/api/v1`和`application/problem+json`约定。

统计以MySQL规范数据为权威来源；导出只接受结构化目录过滤条件、`CSV/JSON`格式和服务端生成的不可预测令牌，单任务最多10,000条；运维与告警响应只返回计数、比例、时间和有限安全摘要。本节记录7.1冻结的边界；后续批次仅能在保持契约兼容的前提下逐项实现。V10导出、目录下载与审计、V11运维告警及运行监控页面均已在7.4至7.7实现，并通过7.8完整回归确认契约兼容。

### 23.6 阶段7局部图谱实现追溯

批次7.2前端复用`/api/v1/graph/subgraph`、`/path`和按权限读取的`/sync-status`，不修改MySQL权威数据与Neo4j投影边界。页面不自动加载大图，只接受明确的中心节点或路径输入；后端单次限制之外，客户端合并扩展时按稳定ID去重并执行累计300节点硬上限，移除指向未保留节点的悬空关系。Cytoscape.js负责受限布局和选择交互，节点/关系表格使用同一响应数据作为可访问替代视图，图例同时提供类型文字，颜色不是唯一信息载体。验证证据为`graph.test.ts`和`stage7-graph.spec.ts`。

### 23.7 阶段7统计分析实现追溯

批次7.3的`analytics`模块只依赖MyBatis与MySQL规范数据。查询先排除`canonical_entity_link`中的已合并成员成果，并使用有效的成果类型和发表日期人工覆盖，再按相同范围统计总览、年度趋势、类型/来源/机构/主题分布及合作关系；来源和其他实体关联通过规范成果ID归并，未知类型使用独立`UNKNOWN/未知`类别，不静默并入其他类别。作者和机构合作以同一规范成果中的实体对即时推导，排行榜有界且不物化合作边。

`/analytics`页面通过现有会话权限和原生`fetch`并发读取四个只读接口，显示`MYSQL`口径、实际过滤条件和数据更新时间。ECharts仅注册折线图、柱状图及必要组件，各图均有稳定中文语义标签和同源表格摘要；Neo4j驱动、图状态和图查询均不在统计模块调用链中。验证证据为`AnalyticsQueryTests`、`AnalyticsServiceTests`、`AnalyticsPersistenceIntegrationTests`、`SecurityIntegrationTests`、`business.test.ts`和`stage7-analytics.spec.ts`。

### 23.8 阶段7异步导出后端实现追溯

批次7.4通过V10持久化导出任务元数据，`export`模块只从MySQL当前规范成果读取数据。查询排除已合并成员成果，人工覆盖字段优先，并按成果标题、作者、机构、年份范围、成果类型、来源类型、载体和主题ID执行受控组合过滤；任务创建前固定计算数量，超过10,000条直接拒绝。任务状态通过`PENDING → RUNNING → SUCCEEDED/FAILED`原子条件更新，完成文件保留24小时并在读取或下载时转换为`EXPIRED`。

导出生成复用进程内`ThreadPoolTaskExecutor`，固定并发2、队列20和每用户2个活动任务，不引入消息队列。CSV采用UTF-8 BOM、全字段引号和公式注入中和，JSON复用既有Jackson；客户端不能提交文件名或路径，服务端以UUID生成文件名，在配置化固定根目录内规范化解析并使用同目录临时文件原子替换。下载要求`EXPORT_READ`、创建者或管理员对象权限和服务端生成的256位随机令牌；响应及错误不暴露内部路径。应用启动时把遗留`RUNNING`任务安全标记失败，只按执行器容量重新提交`PENDING`任务，不执行无限重试。验证证据为`ExportFilterTests`、`ExportServiceTests`、`ExportTaskProcessorTests`、`ExportRecoveryServiceTests`、`BoundedExportTaskDispatcherTests`、`LocalExportFileStoreTests`、`ExportPersistenceIntegrationTests`、`FlywayMigrationTests`、`SecurityIntegrationTests`和`OpenApiDocumentTests`。

### 23.9 阶段7目录导出与审计闭环追溯

批次7.5在既有成果目录中复用当前筛选表单。题名、单一年份、成果类型和`OPENALEX/CROSSREF`来源直接转换为冻结的`ExportFilter`字段；作者、机构、载体和主题的文本查询必须通过现有目录分页接口得到唯一规范实体后才转换为ID。零匹配或多匹配时前端拒绝创建任务，防止文本模糊查询被静默转换为更宽的导出范围。任务创建后只在内存中保存当前任务并按800毫秒串行轮询，终态停止；下载复用原生`fetch`的Blob响应和浏览器对象URL，不引入Axios、Pinia或其他依赖。

导出创建在任务事务内记录`EXPORT_CREATED`；异步处理通过独立事务终结器在数据库状态真实转换后，以原请求者身份记录`EXPORT_SUCCEEDED`或`EXPORT_FAILED`；有效令牌和对象权限校验通过后记录`EXPORT_DOWNLOADED`。审计摘要只含格式、数量或稳定错误码，不含筛选正文、下载令牌、文件路径或异常详情。启动恢复枚举全部遗留`RUNNING`任务并执行条件失败迁移，已终态任务不会写入伪失败审计；`PENDING`恢复仍受执行器容量限制。验证证据为导出后端单元/持久化/安全测试、`export-filter.test.ts`、`api.test.ts`、`business.test.ts`和`stage7-export.spec.ts`。

### 23.10 阶段7运维总览与系统内告警追溯

批次7.6通过V11增加`alert_event`，MySQL保存三类系统内事件、有限结构化证据和确认信息。`dedup_key`结合只在`OPEN`状态产生值的生成列唯一索引，保证同一告警类型与主体最多存在一个未确认事件；条件更新与`version`负责并发确认。新信号时间不晚于当前未确认事件时不更新，已确认事件只有在条件信号晚于确认时间时才产生新的未确认事件，避免定时轮询制造重复告警。

告警评估复用现有Quartz和MyBatis，默认每60秒串行执行。数据源最近连续失败达到3次触发告警，达到6次升级为严重；最近完成的任务运行读取至少20条且解析成功率低于80%时触发，低于40%升级为严重；图同步最老待处理超过300秒触发警告，存在死信直接触发严重。阈值通过`aacv.operations`配置并在启动时校验边界，不接入邮件、短信、即时通信或外部告警平台。

`/api/v1/operations/overview`汇总应用存活、MySQL、Neo4j、活动采集、近24小时失败、图同步和未确认告警；告警分页与确认遵循7.1冻结契约。读取要求`OPERATIONS_READ`，确认要求`ALERT_MANAGE`、CSRF、非空原因和当前版本，并记录`ALERT_ACKNOWLEDGED`安全审计。验证证据为`AlertServiceTests`、`AlertEvaluationServiceTests`、`OperationsServiceTests`、`AlertEvaluationQuartzScheduleTests`、`OperationsPersistenceIntegrationTests`、`FlywayMigrationTests`、`SecurityIntegrationTests`和`OpenApiDocumentTests`。

### 23.11 阶段7运行监控页面追溯

批次7.7新增管理员`/operations`权限路由。页面并行但独立处理Actuator的liveness、readiness和graph健康组，以及运维总览、告警、图事件、维护运行和审计分页；任何一个请求失败只标记对应区域。Actuator以HTTP 503返回合法`DOWN`状态时保留该状态，不把依赖降级误报为无响应。Neo4j不可用时页面仍保留MySQL侧运行计数、告警和审计，成果目录和统计路由也不受图状态控制。

监控页只使用7.1冻结接口。近24小时采集失败使用总览聚合计数，并链接到已有采集运行页查看失败阶段和有限摘要，不新增跨运行失败明细接口。告警确认、死信重放、回填、对账和全量重建均复用既有原生`fetch`与CSRF边界；按钮按权限隐藏，后端继续最终授权。全量重建要求用户显式输入`REBUILD_AACV_MANAGED_GRAPH`。验证证据为`health.test.ts`、`business.test.ts`、`index.test.ts`、生产构建和`stage7-operations.spec.ts`。

### 23.12 阶段7最终验收与启动顺序约束

批次7.8以MySQL 8.0.42和Neo4j 5.26 Testcontainers复跑全部后端测试，并复核V1至V11空库迁移和既有升级路径。全量回归发现原始Payload清理计划在`ApplicationReadyEvent`阶段注册时，可能与已即时启动的图Outbox任务竞争Quartz JDBC表；清理计划现作为最高优先级`ApplicationRunner`先完成固定Job/Trigger注册，之后才允许普通即时Runner注册，任务周期、Job身份和失败语义不变。启动测试同步断言V11，相关失败用例、迁移和调度定向测试及159项全量测试均通过。

浏览器验收不用于并发性能测量。各流程共享一个Vite开发服务器，Playwright固定单worker顺序执行，避免阶段7大型懒加载模块首次转换时的跨文件资源竞争；前端组件并发行为仍由Vitest覆盖。最终验收还包括24项Vitest、生产构建、7项Edge流程、Compose静态解析、依赖与禁止技术检查。容量性能、备份恢复、真实凭据联合运行和部署仍属于阶段8。

## 24. 方案比较

### 24.1 模块化单体与微服务

推荐模块化单体。当前业务规模、团队规模和部署目标尚未证明微服务的必要性。微服务会增加服务发现、消息可靠性、分布式追踪、配置、部署和跨服务一致性成本。通过明确包边界、应用服务和事件模型，可以保留后续拆分可能。

### 24.2 Outbox 与同步双写

推荐 Outbox。同步双写在 MySQL 成功、Neo4j 失败时无法自动保持一致；跨数据库分布式事务复杂且不适合当前栈。Outbox 允许短暂最终一致，并提供重试、死信和对账能力。

### 24.3 MySQL 检索与专用搜索引擎

MVP 推荐先使用 MySQL。当前建议规模和元数据检索需求可以通过结构化筛选、索引和受控标题搜索验证。只有当中文全文相关度、复杂高亮、百万级数据或并发指标无法满足时，再基于测试引入 Elasticsearch 或 OpenSearch。

### 24.4 公开 API 与 HTML 爬取

推荐优先公开 API。API 的字段、分页和使用规则通常更明确，解析维护成本更低。HTML 适配器只作为经授权来源的补充，并且需要固定样例、结构变化监控和更严格的访问控制。

### 24.5 Neo4j 4.4 与 5.26 LTS

推荐 5.26 LTS。Neo4j 4.4 已结束支持，不适合作为新系统生产基线。目录中现有 neo4j-4.4.tar 可以保留用于历史兼容性参考，但不应让新代码依赖 Neo4j 4 方言或旧配置格式。

### 24.6 MyBatis 与 Spring Data JPA

本项目已经确认 MySQL 使用 MyBatis。采集系统包含批量写入、基于唯一键的幂等更新、动态组合检索、去重候选和统计聚合，MyBatis 能够让 SQL、索引使用和批次行为更加明确。项目不再引入 Spring Data JPA，避免同一批 MySQL 表同时存在两套持久化模型。Flyway 独立负责数据库结构版本，Spring Data Neo4j 继续负责图数据访问。

## 25. 系统设计确认问卷

### 25.1 填写说明

- 在选中的选项前将 [ ] 改为 [x]。
- 单选题只选择一项；标注“可多选”的题目可以选择多项。
- 需要环境信息的题目请直接填写横线内容。
- 已确认且不再提问的技术基线：MySQL 使用 MyBatis，MySQL 数据库迁移使用 Flyway，Neo4j 使用 Spring Data Neo4j。

### Q-DES-01 仓库目录结构（单选）

- [x] A. backend、frontend、deploy、docs 分目录（推荐）
- [ ] B. 后端位于仓库根目录，frontend 和 deploy 单独分目录
- [ ] C. 其他结构：________________

### Q-DES-02 Java 根包名（单选）

- [x] A. com.aacv.system（推荐）
- [ ] B. 根据学校或组织域名命名：________________
- [ ] C. 其他：________________

### Q-DES-03 后端 Maven 模块组织（单选）

- [x] A. 单个 Spring Boot Maven 模块，通过业务包保持模块边界（推荐）
- [ ] B. 一个父工程加多个 Maven 子模块
- [ ] C. 其他：________________

如选择 B，请填写计划拆分的模块：________________

### Q-DES-04 MyBatis SQL 管理方式（单选）

- [x] A. Mapper 接口加 XML SQL，复杂和简单 SQL 均集中在 XML（推荐）
- [ ] B. 简单 SQL 使用注解，复杂 SQL 使用 XML
- [ ] C. 引入 MyBatis-Plus
- [ ] D. 其他：________________

说明：选择 MyBatis-Plus 会增加新的框架依赖，需要在实施前单独评估版本、许可和长期维护成本。

### Q-DES-05 调度与批处理职责（单选）

- [x] A. Quartz 负责计划触发，Spring Batch 负责任务执行、检查点和恢复（推荐）
- [ ] B. 只使用 Spring Batch，由固定计划触发
- [ ] C. 只使用 Quartz，自行实现批次和检查点
- [ ] D. 其他：________________

### Q-DES-06 Web 认证方式（单选）

- [x] A. Spring Security 服务端会话、HttpOnly Cookie 和 CSRF 防护（推荐）
- [ ] B. JWT 访问令牌加刷新令牌
- [ ] C. 直接接入组织统一登录，具体协议见 Q-DES-14
- [ ] D. 其他：________________

### Q-DES-07 图谱前端组件（单选）

- [x] A. Cytoscape.js（推荐，图算法与关系交互能力较完整）
- [ ] B. AntV G6（中文生态和定制展示较方便）
- [ ] C. 先制作技术验证页面后再选择
- [ ] D. 其他：________________

### Q-DES-08 Neo4j 版本与授权（单选）

- [x] A. Neo4j 5.26 LTS Community Edition（推荐用于 MVP）
- [ ] B. Neo4j 5.26 LTS Enterprise Edition，已有合法授权
- [ ] C. 实施时选择更新的 Neo4j LTS，并重新验证兼容性
- [ ] D. 其他版本或发行方式：________________

是否已有 Neo4j Enterprise 授权：不适用，第一阶段使用 Community Edition

### Q-DES-09 后端运行方式（单选）

- [x] A. 单个 Spring Boot 进程同时承载 API、调度、批处理和图同步（推荐）
- [ ] B. API 与后台任务拆成两个进程，但仍共享同一代码库
- [ ] C. 从第一阶段开始拆分独立服务
- [ ] D. 其他：________________

### Q-DES-10 生产或验收服务器信息（填写）

当前状态：第一阶段暂不考虑服务器，只在 Windows 本机开发运行。服务器操作系统、CPU、内存、磁盘、MySQL、Neo4j 和 Docker 条件均延后到部署决策时重新填写。

当前验收主机数据库：MySQL80，MySQL Community Server 8.0.41，Win64/x86_64，端口3306，服务正在运行。8.0.42仍是兼容基线，并通过Testcontainers执行V1至V11迁移；不得把本机8.0.41与基线表述为同一版本。

### Q-DES-11 网络与交付方式（单选）

- [ ] A. 服务器可以联网，在线构建和部署
- [ ] B. 开发机联网、服务器离线，需要完整离线部署包
- [ ] C. 开发机和服务器均处于受限网络
- [ ] D. 其他：________________
- [x] E. 第一阶段仅本机联网开发，服务器交付方式延后确认

允许访问的外部学术数据源域名：本地开发不单独配置部署层允许列表；应用适配器只使用 api.openalex.org 和 api.crossref.org 固定地址

### Q-DES-12 域名、HTTPS 与访问范围（填写）

- 系统访问域名或 IP：localhost；本地端口在开发计划中确定
- 是否要求 HTTPS：本地开发暂不要求；未来部署到服务器时必须启用
- 是否仅内网访问：是，第一阶段仅本机或经用户确认的同一内网
- 是否已有反向代理或证书：当前不适用
- 允许访问系统的网络范围：默认仅 127.0.0.1；开放内网访问前必须明确绑定地址和允许网段

### Q-DES-13 备份与恢复目标（填写）

- MySQL 备份频率：每日一次逻辑备份
- Neo4j 备份或重建策略：以 MySQL 权威数据全量重建图投影，必要时补充周期性离线 dump
- 备份保留期限：7 个每日备份和 4 个每周备份
- 最多允许丢失的数据时间，即 RPO：24 小时
- 故障后期望恢复时间，即 RTO：4 小时
- 备份存放位置：固定使用 E:\AACV_System_Backups，不得位于 Git 仓库内；创建工具和容量检查已实现，目录仍须由用户首次执行时显式创建

### Q-DES-14 统一身份认证（单选）

- [x] A. MVP 使用系统本地账号，不接入统一认证（推荐）
- [ ] B. LDAP
- [ ] C. CAS
- [ ] D. OAuth 2.0 或 OpenID Connect
- [ ] E. 其他：________________

如需统一认证，请填写服务地址、测试环境和对接负责人，不要在文档中填写密码或密钥：________________

### Q-DES-15 首期监控方式（单选）

- [x] A. Actuator、结构化日志和系统内运行监控页面（推荐）
- [ ] B. 在 A 的基础上接入现有 Prometheus 和 Grafana
- [ ] C. 接入组织已有监控平台：________________
- [ ] D. 其他：________________

告警接收方式和负责人：第一阶段保留系统内告警事件、失败记录和监控页面，不实现邮件、短信或即时通信外部通知

本问卷及需求确认问卷已完成第一阶段本地开发基线确认。服务器部署相关信息延后到实际部署前重新填写和评审。

## 26. 阶段8隔离验收设计

阶段8容量与性能环境不复用`aacv_system`开发库。`deploy/compose.stage8.yaml`固定创建MySQL 8.0.42数据库`aacv_stage8_capacity_20260903`和Neo4j 5.26投影，端口分别为13306、17474和17687；后端使用18080。三个命名卷均以`aacv_stage8_`开头，默认保留，不提供自动删除流程。

`tools/stage8/stage8-capacity.sql`确定性生成100,000条成果、300,000名作者、10,000个机构、1,000个载体和2,000个主题。Neo4j生成413,000个节点及精确1,000,000条关系，其中`AUTHORED`、`AFFILIATED_WITH`各300,000条，`PUBLISHED_IN`、`CITES`各100,000条，`HAS_TOPIC` 200,000条。生成器只接受空的固定隔离目标；发现已有成果或受管图节点时停止，不做清空和覆盖。

性能验收从HTTP边界测量并完整读取响应体。普通成果默认20条、最大100条、详情分别预热100次并测量1,000次；300节点上限局部图预热50次并测量500次；固定并发4。P95使用升序样本的nearest-rank，即索引`ceil(0.95*N)`，任何非2xx或超时均导致该场景失败，不从样本中剔除。目标保持目录与详情P95不超过2秒、局部图P95不超过3秒。

故障演练仅允许对明确命名的阶段8隔离服务执行。Neo4j停止时要求liveness和MySQL成果目录继续可用、图接口明确503；恢复后图健康和查询恢复。MySQL停止时写请求不得返回2xx；恢复后验证关键计数未推进。脚本在`finally`中启动依赖并等待健康，但主机断电等外部中断仍需人工检查。应用重启、检查点、取消、重复Outbox、退避、死信和资源释放继续由自动化测试与真实联合运行共同取证。

备份静态加密不属于本地阶段8验收范围。业务备份固定写入仓库外`E:\AACV_System_Backups`，以临时文件生成后原子改名，同时写入SHA-256和计数元数据；目录ACL只允许当前用户、SYSTEM和Administrators。每日与每周副本分别保留7个和4个，实际删除旧副本必须显式确认。`deploy/compose.stage8-recovery.yaml`使用独立MySQL 8.0.42、Neo4j 5.26、数据库`aacv_stage8_recovery`及23306/27474/27687端口；恢复脚本拒绝非空目标和28080端口冲突，恢复后禁用恢复副本的外部采集，通过28080临时后端抽样核对成果目录/详情，再执行受控图全量重建与零差异对账。清理时只停止属于脚本进程树的监听进程；所有恢复资产默认保留，不自动删除数据库或命名卷。凭据不得进入脚本、命令行、日志或文档，跨主机复制备份时仍须遵守安全传输要求。

## 27. 参考资料

- [需求分析](./requirements-analysis.md)
- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Batch Reference](https://docs.spring.io/spring-batch/reference/)
- [MyBatis Reference Documentation](https://mybatis.org/mybatis-3/)
- [Flyway Documentation](https://documentation.red-gate.com/flyway)
- [Spring Data Neo4j Reference](https://docs.spring.io/spring-data/neo4j/reference/)
- [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)
- [Neo4j Supported Versions](https://neo4j.com/developer/kb/neo4j-supported-versions/)
- [Neo4j Upgrade and Migration Guide](https://neo4j.com/docs/upgrade-migration-guide/current/)
- [RFC 9309: Robots Exclusion Protocol](https://www.rfc-editor.org/rfc/rfc9309.html)
