# 本地开发与接入维护

## 构建和入口

先运行 `scripts/Initialize-Integration.ps1` 准备本项目独立基础服务；首次执行 `scripts/Build-Integration.ps1 -Restore` 从各系统原有锁文件恢复依赖并构建，后续使用不带 `-Restore` 的命令。各系统继续独立管理 POM、Maven Wrapper、package.json、锁文件和 node_modules。

`scripts/Start-Integration.ps1 -System all -Mode Demo` 启动门户和四个 Java jar，Vite preview 从各系统 dist 提供页面；`Development` 额外启动四个 Vite 开发进程，仍通过 18000 统一访问。两种模式互斥。运行中配置发生变化时网关返回配置错误，必须重启门户以应用新配置。

开发模式可用 `node scripts/Test-SystemsBrowser.mjs --development` 验证真实登录、深链接刷新和通过统一入口建立的 HMR WebSocket 连接。统一登录改造前的四系统接入已通过此检查；本次统一登录的浏览器验收使用 Demo 模式，尚未重跑 Development HMR。演示模式无需运行独立 Vite 开发进程。

构建脚本明确检查正在运行的后端，避免 Windows jar 文件锁。构建使用 `-DskipTests package`，测试在验收阶段单独执行，不能把构建成功当作单元测试成功。

启停记录位于 `.local/integration/processes.json`，校验工作目录、PID、创建时间、可执行文件和命令行。总入口重复启动会拒绝；在门户已运行且模式一致时，可以恢复已停止的单个系统。停止不按端口或进程名批量结束，也不操作原项目。

## 本地功能试用数据

默认初始化仍保持空业务库。需要试用数据时，先按 README 完成本机基础服务、构建与管理员准备，启动四个后端使表结构就绪，再在仓库根目录执行：

```powershell
node scripts/Import-IntegrationDemoData.mjs
node scripts/Import-IntegrationDemoData.mjs --check
node scripts/Test-IntegratedSystems.mjs
node scripts/Test-SystemsBrowser.mjs
```

`Import-IntegrationDemoData.mjs` 只连接归属于当前工作区 `course-integration` 的本机 MySQL 容器，不创建账号、不更改表结构、不调用外部采集或模型服务。数据库连接沿用容器内部环境，运行参数和输出不含凭据。每库独立事务；执行前核对四库容器归属和样例标识，已有完整样例则跳过，存在部分样例或 ID 冲突则明确失败，保留用户数据。重复执行不会重置用户对样例的修改；`--check` 仅核对数据库中的样例数量和标识。

| 系统 | 试用数据 |
| --- | --- |
| relation | 30 篇论文、16 位作者、6 个机构、6 个载体、10 个关键词、74 条署名、64 条主题关联、51 条引用。 |
| extraction | 同一组 30 篇论文、16 位作者、6 个载体、10 个主题、74 条署名、64 条主题关联、51 条引用；另有 10 个实体、5 条关系，前 5 篇论文标记为已抽取。 |
| crawler | 12 项成果、8 位作者、4 个机构、5 个主题、2 个采集任务，以及来源、治理、质量、告警和模拟失败记录。 |
| scholar | 同一组 30 篇论文、16 位作者、6 个机构、6 个载体、10 个主题及署名、主题和引用关联，可试用学者画像和科研分析。 |

relation、extraction、scholar 的数据由 relation 原有 `sample-data.sql` 的简单 `INSERT ... VALUES` 数据转换，导入器不会执行原脚本的删除语句。relation、extraction 使用 `910100–910999` 数值标识范围，scholar 使用 `course-demo-` 标识；论文 DOI 为 `10.9999/course-demo.*`，标题、作者等带 `[试用]` 标记。relation 必填时间、版本、抽取状态与 `publication_year` 按实际 JPA 建表结果补齐，不依赖原独立运行 SQL 的默认值或生成列。

crawler 复用已有 `systems/crawler/tools/development/rendering-sample-data.sql`，样例 DOI 为 `10.9999/aacv-demo.*`，任务和异常说明带 `[页面测试]` 标记。此样例有意包含一次图同步死信、失败运行和质量问题，用于试用治理与运维页面；这些标记记录不代表本机基础服务当前不可用。未创建采集定时计划。

所有记录都是功能试用样例；抽取结果由脚本人工构造，不是实际模型输出，样例外部标识和 DOI 不用于验证真实外部检索。relation、extraction、crawler 通过现有 outbox 机制生成图投影，scholar 从 MySQL 组图，导入后需等待异步同步完成。各系统仍保持独立数据库，无自动跨系统数据同步。

2026-09-10 的首次带数据检查发现 extraction 论文图谱错误返回 `404 / AUTHOR_NOT_FOUND`，年份分布和高被引列表为空。随后按用户要求修复，当前实现和复验结果见下节；导入器本身没有改变业务实现。

## 抽取论文图谱与统计

`GraphController.getPaperGraph` 按 Neo4j 数值属性使用 `asLong()` 读取论文、作者和载体 ID，返回以请求论文为根的图谱，对连接查询产生的重复节点和边去重，并返回已有查询中的发表载体。先查询 MySQL 确认论文存在；不存在返回 `404 / PAPER_NOT_FOUND`，存在但图投影尚未生成或 Neo4j 不可用时返回 `503 / GRAPH_UNAVAILABLE`，不再转入同编号作者查询。统计接口同样在图服务不可用时返回 503，正常空库仍返回真实零值；`relationshipCount` 统计全部图关系，供前端“关系总数”卡片使用。

`PaperService.savePaperFromRemote` 对新论文发送 `PAPER_CREATED`，对已有论文发送 `PAPER_UPDATED`。轮询器处理这两类事件时读取 MySQL 当前论文、作者、载体和引用；兼容旧的仅含标题的事件，并在一次 Neo4j 事务内更新 `title/year/citationCount`，重建该论文的 `WROTE`、`PUBLISHED_AT`、`CITES` 边。节点、其他论文的入向引用和抽取实体关系保持；缺失年份或被引次数移除旧属性。已不存在的论文不会被事件重新创建，图事务失败仍保留未处理事件以便重试。

已有已处理事件不会自动回放。旧环境应用修复后，可通过现有 `outbox_events` 追加论文更新事件，等待原轮询器完成同步；不需要重导业务数据、重置历史事件或修改表结构。本机使用以下带固定修复标记的语句，首次追加 30 条，重复执行追加 0 条：

```sql
START TRANSACTION;
INSERT INTO outbox_events(event_type, aggregate_id, aggregate_type, payload)
SELECT 'PAPER_UPDATED', p.id, 'PAPER',
       JSON_OBJECT('id', p.id, 'repair', 'paper-graph-v1-20260910')
FROM papers p
WHERE NOT EXISTS (
    SELECT 1 FROM outbox_events e
    WHERE e.aggregate_type = 'PAPER' AND e.aggregate_id = p.id
      AND e.event_type = 'PAPER_UPDATED'
      AND JSON_UNQUOTE(JSON_EXTRACT(e.payload, '$.repair')) = 'paper-graph-v1-20260910'
);
SELECT ROW_COUNT() AS enqueued;
COMMIT;
```

2026-09-10 复验：30 篇论文图谱逐一返回 200，根节点、作者、引用、载体与论文详情一致，节点和边没有重复；原失败的 `910120` 返回 8 个节点和 7 条边，未知论文返回 `404 / PAPER_NOT_FOUND`。统计为 30 篇论文、16 位作者、10 个实体、51 条引用、170 条总关系，7 个年份分组与 10 篇被引排名均与 MySQL 论文详情一致。追加的 30 个任务全部处理完成，重复运行修复 SQL 不再入队。Edge 实际统计页面确认卡片和两张图表加载完成。

针对性测试在 `systems/extraction` 执行，包含图谱 ID、缺失资源、图服务故障、空统计、历史事件、空字段、重试、更新事件与原有统一身份过滤器，共 21 项通过：

```powershell
.\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/extraction/tmp' '-Dtest=GraphControllerTest,OutboxPollingSchedulerTest,PaperServiceTest,PortalAuthenticationFilterTest' test
.\mvnw.cmd -o -B '-DskipTests' package
```

上面的临时目录参数是本机路径，其他工作区需替换为自己的 extraction 临时目录。重新打包后只重启 extraction；根目录的 `node scripts/Test-IntegratedSystems.mjs`、`node scripts/Test-SystemsBrowser.mjs` 和 `npm.cmd --prefix portal run check:source` 均通过，其他三个后端持续运行。未进行真实外部检索或模型调用。

官方依据：[Neo4j Value 数值转换](https://neo4j.com/docs/api/java-driver/current/org.neo4j.driver/org/neo4j/driver/Value.html)、[Neo4j Java 事务](https://neo4j.com/docs/java-manual/current/transactions/)。

## 门户入口视觉与交互


门户采用深蓝科技主题：顶部导航与入口搜索、四张系统卡片、学术合作/研究分布/趋势面板和数据总览。布局及简单交互由 `portal/src/App.vue` 管理，主题位于 `portal/src/tokens.css`，响应式样式位于 `portal/src/style.css`。1200px 以下将辅助面板排到主入口后方，800px 以下入口卡片改为单列。

`portal/src/data/overview.js` 中的统计、趋势、研究方向与卡片视觉说明是演示内容，不调用业务数据接口；界面标注“示例”或“演示数据”。搜索仅筛选系统名称、功能与研究方向，不提供跨系统论文检索。趋势范围切换仅作用于演示数据。`PortalRoot.vue` 确认统一会话后展示门户；`PortalLogin.vue` 复用原主题、背景、按钮和图标，账号菜单显示当前用户名并提供统一退出。

系统名称、实际接入状态、维护说明和固定路径继续来自 `services/systems.js` 读取的 `/integration.json`。维护状态仍跳转到原 HTTP 503 维护页，加载失败仍提供重新读取；本轮运行接入对网关、配置和子系统的必要适配见下文。

图片与图标位于 `portal/public/assets/portal/`，通过现有 `/assets/` 白名单提供访问；不要改为根级 `/images/` 或 `/icons/`。图片使用无损 WebP，源文件与图标授权说明见该目录的 `README.md`。页面不使用远程字体/CDN，也没有增加 npm 依赖；图表在独立 `PortalChart.vue` 中绘制，卸载时释放 `ResizeObserver`。

`scripts/Test-PortalBrowser.mjs` 保留原有维护页和错误重试检查，并适配帮助弹窗、搜索空状态、趋势范围及移动导航检查。此次视觉与浏览器核对的证据、环境限制见仓库根目录 `design-qa.md`。

## 实际端口与数据隔离

| 系统 | 开发前端 | 后端 | Session Cookie / Path | MySQL 端口 / 库名 | Neo4j HTTP / Bolt |
| --- | --- | --- | --- | --- | --- |
| relation | 5174 | 18081 | `RELATION_SESSION` / `/relation` | 23361 / `course_relation` | 27471 / 27681 |
| extraction | 5175 | 18082 | `EXTRACTION_SESSION` / `/extraction` | 23362 / `course_extraction` | 27472 / 27682 |
| crawler | 5176 | 18083 | `CRAWLER_SESSION` / `/crawler` | 23363 / `course_crawler` | 27473 / 27683 |
| scholar | 5177 | 18084 | `SCHOLAR_SESSION` / `/scholar` | 23364 / `course_scholar` | 不需要；图谱从 MySQL 组装 |

数据库容器属于 `course-integration` Compose 项目，每个库有独立应用账号、随机密码与卷。初始化脚本创建 `.local/integration-runtime` 并限制为当前 Windows 用户访问。不会读取原工作区 .env 或连接本机原业务库。临时文件、Socket 与 crawler 导出目录也在各系统私有运行目录中。

Neo4j 使用既有 5.26 Community 镜像；MySQL 使用既有 8.0.42 镜像。初始化会核对容器所属工作区；重复执行不更换密码、不删除数据，也不覆盖已有 application.properties。`Stop-Integration.ps1` 只停止应用；停止基础服务使用 `docker compose -f .local/integration-runtime/compose.json stop`。

## 接入配置契约

唯一来源为 `deploy/systems.json`，固定 relation、extraction、crawler、scholar 四个系统。支持 maintenance/enabled；非法状态、缺失来源、端口冲突、目录越界、缺少验收文档明确失败。

- `sourceBranch/sourceSha`：已核定来源，Li 的真实分支名是 `Li`。
- `acceptance/acceptanceSha`：对应系统验收文档及匹配的来源 SHA。
- `dist`：前三套为 `systems/<id>/frontend/dist`；Li 保留原布局 `systems/scholar/web/dist`。
- `contextPath`：各 Java 后端使用 `/<id>` 上下文，网关保留该前缀；未设置此字段的旧适配模式仍剥离前缀。
- `readinessPath`：真实健康路径，前三套检查 MySQL/Neo4j；scholar 检查实际使用的 MySQL。
- `backend/frontend`：前台直接运行的 java/node 进程，参数不含凭据。`{workspace}` 仅由启停脚本展开为当前仓库绝对目录。

后端从外部 `application.properties` 加载独立连接信息、Cookie 名称与路径、端口及 integration profile。新环境默认不创建用户、不导入业务数据，首次启动只建立表结构及角色、字典等基础记录。relation、scholar 停用上游种子账号，其整合管理员引导仅在显式设置 `integration.bootstrap-admin.enabled=true` 时装配；缺省或 false 均不要求 `integration.admin-password`。原业务 profile 保留原行为。extraction 的原配置账号不参与统一入口认证。

初始化为新配置写入 `integration.bootstrap-admin.enabled=false`，crawler 另写入 `aacv.bootstrap-admin.enabled=false`、`aacv.bootstrap-admin.username=admin`，不再注入自动建号密码。开发者在首次后端建表完成后，手动向 `course_crawler.sys_user` 添加 `admin`，保存带 `{bcrypt}` 前缀的密码哈希和 `ACTIVE` 状态，并在 `sys_user_role` 关联 `ADMIN`。统一密码沿用 crawler；本机初始密码仍在 `.local/integration-runtime/crawler/credentials.json` 的 `admin` 字段，仅生成该文件不会创建账号。其他系统无需复制账号，具体本地哈希与 SQL 操作见 [README](../README.md)。

重复初始化仍保留已有配置、凭据和账号。旧 crawler 配置若含 `aacv.bootstrap-admin.enabled=true`，需开发者改为 false 并重启后端后才遵循空表不建号的规则；旧 relation、scholar 的密码字段不会独自启用引导。在账号管理中修改密码不会同步凭据文件，真实登录验收仍要求数据库密码与该文件一致。

前端构建使用 `--base=/<id>/`，Router 和集中请求层读取 `import.meta.env.BASE_URL`。relation、extraction 在安装路由前恢复会话，scholar 在工作区恢复当前用户。所有系统提供返回门户链接。API/Actuator 先于静态资源和 SPA 回退；后端停止返回 JSON 503，不回退为 HTML。

统一登录沿用 crawler 账号、服务端 Session、账号状态与角色校验。网关 `/__integration/auth/*` 将其内部 `CRAWLER_SESSION` 转换为浏览器侧 `PORTAL_SESSION`（Path=/、HttpOnly、SameSite=Lax）；上表的各系统 Cookie 配置仍用于内部或独立运行。其余三个后端在 integration profile 下逐请求查询门户身份，仅设置当前请求的 SecurityContext，不创建本地账号，也不保存独立登录状态。原 CSRF 与后端权限校验保留；RESEARCHER 在接入系统内只读。账号集中在 `/crawler/users` 管理，relation 的旧账号管理接口返回 410，其他业务管理接口保持原权限。

四套前端在各自集成 BASE_URL 下移除独立登录路由，旧登录页面由网关跳转 `/login`，旧认证写接口返回 410。未登录访问业务深链接会保留回跳地址；API 返回 401 会返回统一入口，网络故障返回门户供重试。任一系统的退出都撤销同一门户会话，旧 Cookie 重放也不能继续访问。统一认证服务依赖 crawler 和门户可用；没有离线授权缓存。同源子路径不能用作不互信应用之间的安全边界。完整契约、权限映射、验证和限制见 [统一登录说明](unified-login.md)。

`startupPlan` 为三个接入后端附加 `--integration.portal-url=http://127.0.0.1:<portalPort>`，统一认证地址随配置中的门户端口生成，不读取或更改本机凭据配置。单独停止 crawler 会使其他系统的身份确认返回 503；恢复 crawler 后可重新使用。旧 Nginx 静态生成模板未接入统一认证，不适用于此次统一登录运行；当前使用上述 Node/Vite 网关，不应将旧模板作为可部署的统一登录入口。

## 本轮修复与维护约束

- extraction 的 Spring Boot 4 需要 `spring-boot-starter-flyway` 才能装配迁移。已替换直接 flyway-core 声明，版本仍由原 Boot 4.1.1 管理，V001 脚本未改。
- extraction 的缺失参数与未知资源恢复为 400/404，沿用现有 problem+json 错误结构。
- scholar 的退出接口显式销毁会话；图谱注册已有的 fcose 扩展，并处理空图；没有新增前端依赖。
- Li 根目录另有旧 Servlet 练习工程，运行适配器只构建 `web/backend/pom.xml` 和 `web/package.json`，没有修改旧工程的 Java 版本。

## 来源同步

原三套 subtree 导入树及来源祖先保留在 `docs/import-records.json`。Li 使用 `git archive origin/Li` 导入 136 个原始跟踪文件，其源码及已记录适配已纳入提交 `4f47a3e`；archive 导入不建立 Li 来源提交的祖先关系。

`scripts/check-source.mjs` 核对四个原始来源树、前三套原始导入与 HEAD 的祖先关系、每个原始文件、所有新增文件，以及 `docs/source-adaptations.json` 中经过审阅的适配对象哈希。未记录的新改动、缺失来源文件或过期记录会失败。适配记录的更新必须与实际 diff 一起审阅，不能删除校验来让检查通过。

Ye 的远端回退已由接入阶段的 fetch 再次确认，当前保持本地完整版。后续不得直接将骨架覆盖到系统目录。其他分支同步时也应先核定 SHA 与历史，保留集成路径、会话和运行适配。当前集成版本在 `dev` 分支维护，跟踪 `origin/dev`；本次按用户授权分主题提交和快进推送，原集成分支保留。main 合入、PR 与部署仍需对应任务的授权。

2026-09-10 的试用数据准备与抽取修复已分别通过 `8bb4968`（导入脚本）和 `b86be72`（图谱同步、统计、测试及来源适配记录）提交并快进推送到 `origin/dev`，配套使用说明与项目记忆作为独立文档批次整理。推送前核对导入脚本语法、暂存差异及四系统来源记录；先前完成的 21 项抽取测试和真实接口、浏览器验证对应同一份业务代码。各库中的试用记录由导入脚本生成并保存在独立容器卷，凭据、运行配置和截图仍位于忽略目录。

## 验证与环境限制

`scripts/Test-IntegrationInitialization.ps1` 在临时工作区执行初始化脚本，使用替代 Docker 函数验证新配置默认关闭建号、保留本机密码及重复初始化不覆盖配置和凭据，不操作实际数据库。relation、scholar 的 `IntegrationAdminBootstrapTest` 验证未配置密码、旧版密码字段和显式关闭三种情形均不会装配账号引导；原统一身份过滤器用例继续适用。

四系统接入的历史命令和结果见 [本地运行验收](local-runtime-acceptance.md)，本次认证改造的实际命令和结果见 [统一登录说明](unified-login.md)。原有第一阶段记录是历史证据，不能再理解为当前四系统均维护中。

本机受限工具环境的 Node 子进程可能报 `spawn EPERM`，Docker named pipe 和 CIM 也需要工具允许的正常本机权限；这些权限问题不通过修改业务安全设置规避。Java 的 Windows Socket 路径使用本项目独立临时目录。Li 原有 H2 测试使用命令级 `NON_KEYWORDS=YEAR`，不修改业务字段。Li 两个间接工具包在 Node 24.14.0 上有引擎警告，但前端构建和 Edge 实测通过。

官方依据：[Vite 子路径构建](https://vite.dev/guide/build.html)、[Spring Boot 外部配置](https://docs.spring.io/spring-boot/reference/features/external-config.html)、[Spring Boot 4 Flyway 模块要求](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)。Vite preview 仅用作本地入口，Nginx 本体与公网部署仍未验收。

## 2026-09-10 采集可靠性与数据源名称

采集任务、首页任务卡片、质量和治理筛选使用 OpenAlex、Crossref 名称；接口与数据库继续使用来源关联键，避免破坏既有任务和外键。创建、修改和执行均校验来源能力，Crossref 拒绝 OpenAlex 作者/机构标识，多值过滤重复过滤名。

每日计划增加读取、启停和移除，页面自动读取版本；任务提供分页运行历史。旧计划继续使用 `FIXED_SCOPE_REFRESH`。显式增量模式通过 V16 的 `crawl_run_window` 保存实际查询窗口，每次处理最多 7 天：完整成功且没有记录失败才推进，超限时下次缩小，失败或取消重做原窗口。Crossref 按索引时间、OpenAlex 按出版日期处理，不保证补齐迟到记录或全库完整。

重启恢复包含尚未启动的 `PENDING` 与已恢复但尚未附加新 Batch 执行的运行。运行级错误记录安全的阶段、类别和建议，执行失败可从最后已提交检查点重试，并与新触发共用来源锁以避免同范围重复启动。内容及解析器版本不变时仅刷新观测信息，不重建关系或重复发出图投影事件；解析器变化仍触发重建。

实施前核对本基线、development.md 与源码；README 中固定复查和 V15 的描述随本次功能更新为实际行为。完整接口、窗口边界、验证命令和本轮文件清单见 [采集可靠性说明](crawl-reliability.md)。不修改现有业务数据库或凭据；启动新版后端时由 Flyway 执行新增迁移。本轮未提交、推送或重启正在运行的系统。

## 2026-09-10 作者、机构名称选择与关键词说明

OpenAlex 采集表单通过来源名称候选选择作者、机构（各最多50项），提示所属机构或所在地以区分同名对象。已有任务批量回显名称；任务参数仍保存 authorIds/institutionIds，完整URL形式的旧值保留，无新增数据库迁移。Crossref 继续使用 DOI、ORCID、ROR 筛选。

新增只读接口 `GET /api/v1/sources/{sourceId}/entities/{kind}?query=...` 与 `GET /api/v1/sources/{sourceId}/entities/{kind}/resolve?ids=...`，kind 仅允许 authors、institutions，复用 SOURCE_READ 权限并要求启用的 OpenAlex 来源。名称搜索用官方 autocomplete，回显用 ids.openalex 批量过滤；复用认证、请求门控和配额检查，交互等待最多2秒、连接最多3秒、响应最多8秒、响应体最多256KB（不超过原来源限制）。搜索最多10项候选，不自动重试；失败返回503与 SOURCE_LOOKUP_UNAVAILABLE，可手动重试。

前端防抖300毫秒，切换搜索或退出时取消请求并丢弃过期响应。未选中的名称搜索文字、未完成或缺失的名称回显会阻止保存，保留原筛选；用户可明确移除后重选。关键词可选，OpenAlex 传 search 检索标题、摘要及可检索全文，Crossref 传 query 检索成果元数据；整段文本直接交给来源。同类作者/机构多选为或关系，不同筛选维度共同限定作品范围；不保证作者与机构属于同一条署名。

实施前读取本基线、development.md、README 和 crawl-reliability.md，核对源码、既有未提交改动和测试。原记忆与实施前代码一致；本次追加上述交互和接口约定，同步 README 与来源适配哈希。真实外部名称查询与运行中的旧后端替换未执行，测试使用官方格式的模拟响应；验证命令和本轮文件清单见 [采集可靠性说明](crawl-reliability.md#作者机构名称选择补充)。
