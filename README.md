# AACV System

学术成果爬虫与可视化系统已完成开发计划阶段0至阶段7。2026-09-05优化增加四组导航、图谱名称检索、额度延后恢复、治理证据比较、机构名称证据和学术字段覆盖统计，并优化组合查询。当前实施范围、验证命令和结果见[优化验收记录](./docs/optimization-acceptance.md)。历史阶段8容量及HTTP性能证据继续保留；实际业务备份目录的ACL、保留策略和RPO/RTO尚未完成运营验收，服务器部署仍不在本阶段范围内。

## 账号资料与日志（V14）

账号管理增加全库用户类型环形图、最近10条登录活动及统一编辑抽屉。新增表单要求姓名，邮箱、电话、单位、部门和备注选填；旧账号资料不自动回填。用户名保持不变，密码通过独立入口重置。管理员不能停用自己或移除自己的管理员角色，也不能移除最后一个可用管理员。

“系统状态 → 日志管理”提供操作日志和登录日志，支持账号、时间、结果与类型筛选；原审计入口保留。日志只对管理员开放。IP是服务器连接地址，浏览器是客户端声明，历史缺失值显示`--`。

V14使用独立安全版本：升级后的旧会话需重新登录一次，之后资料修改保持登录，角色、状态和密码变化使旧会话失效。迁移脚本只在隔离测试数据库验证，本轮未启动业务库迁移、备份恢复或部署。实现与验证详情见[账号管理与日志验收记录](./docs/account-management-acceptance.md)。

## 本地要求

- Windows 与 PowerShell；
- JDK 21；
- Node.js 20.19+、22.12+ 或兼容的更新版本；
- Docker Desktop Linux Engine；
- 文档兼容基线为 MySQL 8.0.42；当前验收主机的 MySQL80 实测为 8.0.41，数据库 `aacv_system` 使用 `utf8mb4`。阶段8隔离容量环境固定使用 MySQL 8.0.42，不把两个补丁版本表述为一致。

本项目不在仓库中保存任何真实密码。仓库只提交 `.env.example`；本地真实配置写入根目录 `.env`，该文件已由 `.gitignore` 排除。

## 配置本地 `.env`

可先执行`.\tools\development\Test-DevelopmentEnvironment.ps1`，只读检查工具版本、Docker Linux Engine、依赖、配置文件是否存在和默认端口占用；不会读取`.env`内容，也不会验证或修改数据库凭据。`-AsJson`可返回结构化检查结果。

在项目根目录执行：

~~~powershell
if (-not (Test-Path .\.env)) { Copy-Item .\.env.example .\.env }
notepad .\.env
~~~

至少替换 `AACV_DB_PASSWORD`、`NEO4J_PASSWORD` 和 `AACV_NEO4J_PASSWORD` 的 `change-me-before-use`。Compose 与后端使用的 Neo4j 用户名、密码必须一致。`.env` 同时供 Spring Boot 和 Docker Compose 读取，必须保持 `KEY=value` 格式，不要添加 `export` 前缀，也不要将其复制到聊天、日志、截图或共享目录。

后端通过 `spring.config.import` 检查当前工作目录和上一层目录中的 `.env`，兼容从项目根目录启动 Maven 后由插件使用 `backend` 作为工作目录的情况。日常操作仍必须从项目根目录运行下文命令；系统环境变量可在临时诊断时覆盖同名配置。JDK 路径不是应用配置，如本机尚未配置 `JAVA_HOME`，请只在当前 PowerShell 会话设置：

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
~~~

## 一次性初始管理员

初始管理员引导默认关闭，只允许在 `sys_user` 为空且显式启用时创建一个 `ADMIN`。用户表非空时不会重复创建或覆盖账号。

首次启动前，在 `.env` 中临时设置 `AACV_BOOTSTRAP_ADMIN_ENABLED=true`，填写管理员用户名和密码。密码长度必须为 12 至 128 位，不能只包含空白或包含控制字符。成功启动并确认管理员已创建后，立即停止后端，将 `AACV_BOOTSTRAP_ADMIN_ENABLED` 恢复为 `false`、清空 `AACV_BOOTSTRAP_ADMIN_PASSWORD`，再重新启动。不要把管理员密码写入 `.env.example`、IDE 共享配置、脚本、文档或日志。

Neo4j Driver 的连接、连接池获取和事务重试默认都限制为5秒，避免Neo4j不可用时图查询长期占用请求线程。只有经过故障演练后，才应调整`.env`中的`AACV_NEO4J_CONNECTION_TIMEOUT`、`AACV_NEO4J_CONNECTION_ACQUISITION_TIMEOUT`和`AACV_NEO4J_MAX_TRANSACTION_RETRY_TIME`，不要为了掩盖依赖故障而禁用超时。

## 加载页面测试数据

开发库完成当前全部 Flyway 迁移且已存在有效管理员后，可加载一套非敏感页面测试数据：

~~~powershell
.\tools\development\Initialize-RenderingSampleData.ps1
~~~

脚本只接受本机 `127.0.0.1`/`localhost` 上名为 `aacv_system` 的数据库，通过 `Get-Credential` 获取数据库密码，不读取或回显 `.env` 中的凭据。它会在写入前校验数据库名、迁移版本和有效管理员，并在单个事务中写入带 `[页面测试]`、`AACV-DEMO` 或 `10.9999/aacv-demo.*` 标识的数据。重复执行会复用同一批样例并刷新运行时间，不会重复创建成果，也不会修改已有 OpenAlex/Crossref 来源配置。

样例覆盖两个来源、两个采集任务与运行、失败明细、12 条跨年份成果、作者/机构/载体/主题关系、双来源追溯、统计分布与合作、治理候选、质量指标及问题样本、图投影 Outbox、一个模拟死信、维护记录、开放与已确认告警和审计记录。脚本不会创建登录账号，也不会发起外部来源请求。其输出中的 `graph_center_achievement_id` 可直接用于知识图谱页面；Neo4j 与后端启动后，等待现有 Outbox 调度完成首轮投影即可查询样例子图。模拟死信和开放告警是运行监控页面的预期测试状态。

## 一键启动（Windows）

完成上述本地配置、安装前端依赖（`npm --prefix .\frontend ci`），并启动 MySQL 和 Docker Desktop Linux Engine 后，双击根目录 `start.bat`，或在终端执行：

~~~powershell
.\start.bat
~~~

入口调用 `tools/development/Start-All.ps1` 自动定位项目根目录，复用现有开发脚本检查环境和默认应用端口，依次提交 Neo4j、后端、前端启动命令。前后端各自保留一个 PowerShell 日志窗口，沿用后端短 Socket 目录设置；启动后仍由 Flyway 按现有规则应用待执行迁移。等待窗口中的启动完成信息后，访问 `http://127.0.0.1:5173/login`。主窗口提示“已提交”只代表命令已发出，实际启动错误请查看组件窗口。

`.\start.bat --check` 仅执行检查，不启动服务。8080 或 5173 已占用时会停止，避免重复启动；自定义端口沿用下方手动命令。脚本不安装软件或依赖，不创建、读取或回显 `.env` 内容，配置仍由 Spring Boot 和 Compose 自行加载。关闭主窗口不会停止服务；停止方法见文末“停止”。

## 启动 Neo4j

~~~powershell
docker compose --env-file .\.env -f .\deploy\compose.yaml config --quiet
docker compose --env-file .\.env -f .\deploy\compose.yaml up -d neo4j
docker compose --env-file .\.env -f .\deploy\compose.yaml ps
~~~

Neo4j Browser 仅绑定本机 `http://127.0.0.1:7474`，Bolt 仅绑定本机 `127.0.0.1:7687`。

## 启动后端

也可使用`.\tools\development\Start-Development.ps1 -Component Backend`在当前终端启动。脚本为应用JVM指定项目内`.local/java-sockets`目录，解决部分Windows桌面应用派生进程的Unix Socket临时目录问题，不修改系统环境变量或SelectorProvider。`-Component Neo4j`幂等启动图数据库，`-Component Frontend`启动前端；前后端分别使用一个终端，端口占用时拒绝重复启动。脚本按默认本地端口8080/5173检查，自定义端口请使用原始启动命令。

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml spring-boot:run
~~~

后端仅绑定 `127.0.0.1:8080`。可用以下地址检查状态：

- `http://127.0.0.1:8080/actuator/health/liveness`
- `http://127.0.0.1:8080/actuator/health/readiness`
- `http://127.0.0.1:8080/actuator/health/graph`

Flyway 会依次执行迁移目录中的版本：工程基线、身份/会话/审计、数据源/任务/批处理/成果目录、Crossref/治理/字段来源/质量指标、图投影/事务 Outbox/死信/维护运行、导出和系统内告警，以及 V12 的运行结束原因、额度恢复元数据、V13 的机构来源名称证据及 V14 的用户资料、安全版本和日志来源信息。V12同时更正每日计划模式名称，不改变既定采集范围和触发时间。迁移不会为既有成果隐式生成图事件；首次投影必须由受控回填运行触发。Spring Session、Spring Batch 和 Quartz 均不会自行隐式建表。

## 会话、CSRF 与接口

浏览器客户端按以下顺序使用认证接口：

1. `GET /api/v1/auth/csrf`，保留服务端返回的 HttpOnly `SESSION` Cookie，并读取响应中的 `headerName` 和 `token`；
2. 所有非安全方法请求（`POST`、`PUT`等）使用同一 Cookie，并按`headerName`提交Token；
3. `POST /api/v1/auth/login` 登录后继续使用轮换后的会话 Cookie；
4. 使用 `GET /api/v1/auth/me` 获取当前账号、角色和权限；
5. 使用 `POST /api/v1/auth/logout` 注销，注销请求同样必须携带 CSRF Token。

管理员用户管理接口位于 `/api/v1/users`，审计查询位于 `/api/v1/operations/audits`。停用、密码重置或角色变更会使目标用户的已有会话失效。所有错误使用 `application/problem+json`，包含稳定的 `errorCode` 和 `traceId`，不返回堆栈或数据库细节。

详细接口契约见 [OpenAPI](./docs/openapi.yaml)，角色与操作映射见 [权限矩阵](./docs/authorization-matrix.md)，阶段 5 的后端图投影证据见 [阶段 5 验收记录](./docs/stage5-acceptance.md)，业务前端证据见 [阶段 6 验收记录](./docs/stage6-acceptance.md)，可视化、分析、导出与运维证据见 [阶段 7 验收记录](./docs/stage7-acceptance.md)。

统计接口位于`/api/v1/analytics`，只从MySQL规范数据聚合。异步导出接口位于`/api/v1/exports`，成果目录可按当前筛选创建CSV或JSON导出，单任务最多10,000条；默认固定并发2、队列20、每用户最多2个活动任务，成功文件保留24小时。作者、机构、期刊和主题文本必须唯一解析为规范实体后才创建任务。导出文件名和下载令牌只由服务端生成，下载还会校验任务创建者或管理员身份；客户端不得提交或读取服务器文件路径。创建、成功、失败和下载会写入不含令牌、路径或筛选正文的安全审计摘要。

管理员运维接口位于`/api/v1/operations/overview`和`/api/v1/operations/alerts`。总览区分应用、MySQL与Neo4j状态，并聚合采集、图同步和未确认告警计数；Quartz默认每60秒检查数据源连续3次失败、至少20条记录的最近完成运行解析成功率低于80%、图同步积压超过300秒或出现死信。告警按类型与主体幂等，确认必须携带CSRF、原因和当前版本并写入安全审计；系统不发送邮件、短信或即时通信通知。阈值可通过`aacv.operations`配置并在启动时校验。

具有`OPERATIONS_READ`权限的管理员可从`/operations`进入运行监控页，分别查看liveness、readiness、Neo4j独立状态、活动采集、近24小时未解决失败、图同步事件、维护运行、告警和审计。Neo4j不可用时页面明确降级，MySQL目录、统计和其他区域不被隐藏；采集失败明细仍从具体运行进入。告警确认和图运维写操作沿用服务端权限与CSRF，全量重建需要显式确认值。

## OpenAlex 与 Crossref 双源采集和治理闭环

系统只允许固定的 `OPENALEX`/`CROSSREF` 来源及 `https://api.openalex.org`、`https://api.crossref.org` 官方基础地址；请求方不能提交完整 URL，也不会跟随重定向。OpenAlex支持可选的`OPENALEX_API_KEY`，仅由后端从本机配置读取并通过官方请求的Authorization头发送，不进入URL、业务数据库、探测响应或日志；留空时仍使用匿名额度。不要在前端或聊天中提供密钥。Crossref通过`CROSSREF_CONTACT_EMAIL`选择polite pool，邮箱不会持久化或返回给客户端。

管理员可通过 `/api/v1/sources` 分别创建、更新、启停和探测唯一 OpenAlex 与 Crossref 来源；数据运营人员可通过 `/api/v1/crawl` 创建一次性任务、配置每日计划、查看运行和失败记录，并请求暂停、恢复、取消或有限重试。任务范围硬限制为最多 5 页、500 条；两个适配器共用来源契约、Batch、Quartz、状态机、检查点、超时、响应体上限和有限重试。Crossref 使用不透明游标并在同一游标链保持过滤、字段和行数参数一致。

每日计划模式为 `FIXED_SCOPE_REFRESH`，重复既定查询范围，可用于复查较早出版成果；没有自动移动日期窗口或推进全量同步水位。页面的“历史复查”提供去年同月作为起点，应结合日期、作者、机构或关键词拆分任务。来源游标耗尽仅代表当前查询范围读完；达到页数或记录上限会显示 `PARTIAL_SUCCESS` 和明确的结束原因。暂停和取消在当前 Chunk 提交后生效，已经暂停的运行可立即取消；恢复复用同一业务运行及最近已提交游标。OpenAlex每日额度耗尽后保留检查点并释放线程，Quartz每60秒扫描到期运行，最多自动恢复三次；用户可以停止自动恢复或取消。失败记录重试每次最多100条、单条最多5次，并创建带父运行标识的新运行。应用重启时只恢复业务状态与 Batch 元数据一致的运行，异常不一致会停止自动推进并标记失败。

成果详情按来源展示被引量与采集时间、可用的撤稿/开放状态、明确声明的版本关系。两个来源的被引量不相加，未知不等于零或false，版本关系不会自动合并成果。旧数据需要重新采集后才会出现版本2解析器新增的信息。

相同 DOI、ORCID、ROR、ISSN 等确定性标识可以自动关联；无 DOI、无 ORCID、同名作者和纯文本机构只生成稳定候选，不按名称或未经验证的模糊阈值自动合并。治理接口支持候选接受、拒绝、受控撤销和成果字段人工修正；人工修正优先于后续采集。质量接口位于 `/api/v1/quality-metrics`，指标记录任务标识，并可按来源、运行和指标定位有限问题样本。

成果目录接口位于 `/api/v1/catalog`，支持按标题、作者、机构、年份、类型、来源、载体和主题组合分页筛选。详情返回规范字段来源、人工覆盖状态、作者顺序、机构、主题、引用和双源追溯，但不返回完整原始 Payload。Crossref JATS/HTML 摘要不会写入标准摘要字段，仅随受限原始 Payload 按既有 90 天规则保存；Payload 清理由持久化 Quartz 计划每天 UTC 03:15 最多处理 500 条。

## Neo4j 图投影与运维

MySQL 是唯一权威源。采集与治理事务只通过 `GraphProjectionRequestPort` 在同一 MySQL 事务内推进成果投影版本并写入 `REFRESH` Outbox；Neo4j 写入发生在事务提交后。Quartz 默认每 10 秒单线程认领最多 50 条，使用短事务租约、有界退避、最多 5 次尝试和死信；恢复或人工重放时始终从 MySQL 重新读取当前规范快照。

Neo4j 只保存五类受管节点与五类关系，节点和关系均标记 `aacvManaged=true`。局部子图接口位于 `/api/v1/graph/subgraph`，深度最大 2、节点默认 100/硬上限 300；最短路径 `/api/v1/graph/path` 最大 6 跳。同步状态和事件位于 `/api/v1/graph/sync-status`、`/api/v1/operations/graph-events`，初始回填、对账和全量重建位于 `/api/v1/operations/graph-maintenance`。写接口要求会话、权限和 CSRF；全量重建还要求正文确认值 `REBUILD_AACV_MANAGED_GRAPH`。

全量重建只删除 `aacvManaged=true` 的业务投影并保留非 AACV 数据、索引、约束和卷。重建期间普通消费暂停，图查询返回 `GRAPH_REBUILD_IN_PROGRESS`；Neo4j 不可用不会回滚已提交的 MySQL 业务事务。

## 启动前端

左侧导航统一分为可视化（工作台、成果目录、知识图谱、统计分析）、爬虫管理（数据源、采集任务、数据治理）、系统状态（质量指标、运行监控）、用户管理（账号管理）。无权限页面和空组不显示，命令面板沿用相同分组。

图谱支持按名称检索中心、路径起点和终点，候选需明确选择，也保留直接输入业务ID。成果详情可进入关系图谱；常用查询按账号保存在本机浏览器，最多十个。主题目录、关联成果与图投影统一使用`subject.id`，旧`topic.id`不应作为图谱ID使用。

~~~powershell
npm --prefix .\frontend ci
npm --prefix .\frontend run dev
~~~

前端仅绑定 `127.0.0.1:5173`，并将 `/api` 和 `/actuator` 原样代理到后端。访问 `http://127.0.0.1:5173/login` 进入登录页；业务菜单和路由入口按当前用户权限显示，服务端仍执行最终授权。

## 验证

阶段8容量与故障工具只操作固定端口和明确命名的隔离资源：MySQL `127.0.0.1:13306`、Neo4j `127.0.0.1:17474/17687`、后端 `127.0.0.1:18080`，以及数据库 `aacv_stage8_capacity_20260903`。恢复工具另用MySQL `127.0.0.1:23306`、Neo4j `127.0.0.1:27474/27687`、后端 `127.0.0.1:28080`和数据库`aacv_stage8_recovery`。脚本发现既有容量或恢复数据时会停止，不会清空数据库或删除Docker卷。所有密码均由脚本调用`Get-Credential`获取。

~~~powershell
.\tools\stage8\Start-Stage8Environment.ps1
.\tools\stage8\Initialize-Stage8Capacity.ps1
.\tools\stage8\Measure-Stage8Performance.ps1
.\tools\stage8\Restart-Stage8Backend.ps1
.\tools\stage8\Test-Stage8FailureRecovery.ps1 -ConfirmFaultInjection
.\tools\stage8\Invoke-Stage8RealSourceAcceptance.ps1 -ConfirmExternalRequests
.\tools\stage8\New-Stage8DatabaseBackup.ps1 -InitializeBackupRoot -ApplyRetention
.\tools\stage8\Test-Stage8BackupRecovery.ps1 -ConfirmIsolatedRestore
~~~

首次备份才需要`-InitializeBackupRoot`；它只创建固定目录`E:\AACV_System_Backups`并将ACL限制为当前用户、SYSTEM和Administrators。`-ApplyRetention`才会删除超出7个每日、4个每周配额的旧备份及其旁车文件。恢复前会复核SHA-256，并拒绝任何已有表或节点的恢复目标。完整条件、样本量和当前受阻项见[阶段8验收记录](./docs/stage8-acceptance.md)和[备份与恢复说明](./docs/backup-and-recovery.md)。禁止把临时导出文件当作业务备份，业务备份凭据不得落盘。

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml verify
npm --prefix .\frontend ci
npm --prefix .\frontend run test
npm --prefix .\frontend run build
npm --prefix .\frontend run test:e2e
docker compose --env-file .\.env -f .\deploy\compose.yaml config --quiet
~~~

后端测试使用独立的 MySQL 8.0.42 和 Neo4j 5.26 Testcontainers，不会清理、重建或覆盖本机 `aacv_system` 数据库。

阶段7最终回归包含159项后端测试、24项前端Vitest、生产构建、7项Microsoft Edge流程和Compose静态解析；覆盖V1至V11迁移、权限/OpenAPI、MyBatis/Testcontainers、安全与恢复，以及图谱、统计、导出和运维关键流程。Playwright固定单worker顺序执行共享Vite开发服务器上的业务验收流程，不作为并发性能证据。在线 OpenAlex/Crossref 仅用于此前受控小样本验收，不是确定性构建的必要条件。

## 开发环境迁移与交接

需要通过压缩包将项目迁移到另一台 Windows 电脑继续开发时，请先阅读[开发环境迁移交接手册](./docs/development-handoff.md)。手册包含安全打包、凭据排除、新电脑环境准备、数据库与 Neo4j 数据边界、启动验证和故障排查步骤；解压后可将[新电脑 Codex 接管提示词](./docs/new-computer-handoff-prompt.md)直接复制到新任务中执行。

## 停止

在后端和前端终端分别按 `Ctrl+C`。Neo4j 使用以下命令停止，命名卷会被保留：

~~~powershell
docker compose --env-file .\.env -f .\deploy\compose.yaml stop neo4j
~~~

不要执行 `docker compose down -v`，除非已经明确确认需要删除本地图数据库卷。
