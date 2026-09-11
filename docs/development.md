# 本地开发与接入维护

## 当前系统范围

2026-09-10 按用户要求删除 extraction、scholar，当前只保留 relation 与 crawler。crawler 显示名称为“学术成果信息采集及可视化系统”，目录、URL、内部标识、数据库及端口继续沿用。构建、启停、初始化、可选样例导入与日志筛选仅覆盖两个有效系统。旧系统的页面、API 和静态路径返回 404；门户拒绝它们作为登录回跳或工作区地址。

删除前的系统目录及未提交改动保存在本机 Git 忽略的 `.local/subsystem-removal-*/`。源码删除不清理已有数据库、数据卷、本机凭据或历史日志。已有工作区的废弃 Compose 服务不在新初始化范围内，数据仍保留；无需运行删除数据卷的命令。

## 作者信息表导入（2026-09-11）

crawler 使用知网 XLSX/XLS/CSV 文件作为新增学者资料的入口，页面 `/crawler/author-import`，API `/api/v1/author-import`（集成前缀 `/crawler`）。四个旧模块的页面和控制器已删除；历史 SQL 数据与必要的内部模型保留。旧恢复监听不再装配，兼容 Quartz Job 只注销旧采集计划。不要使用历史采集操作说明作为当前入口。

后端新增 POI 与 Commons CSV 依赖，以及 Flyway V17 导入表和 `SUPERVISED`、`PRODUCED_AT` 关系。仍按 MySQL 事务提交业务数据与 Outbox，再同步 Neo4j。文件导入不触发知网网络访问，也不虚构来源采集运行。知网表头、重复判定、身份边界、权限与验证命令见 [作者导入说明](author-import.md)。

作者导入页面现支持同批多文件自动识别：本人署名表的完整作者交集唯一时确定学者，多人时点选候选；硕博按用户确认的导师姓名筛选约定建立指导关系，来源库逐行区分硕士、博士。机构自动提取为成果机构，不据此推断当前所属单位。新增 `/api/v1/author-import/files/preview`、`files/confirm`，一次最多 10 份、合计 10 MB 和 2000 条，在同一 MySQL 事务提交。仅有硕博文件无法识别导师，需附本人署名表；缺少共同成果时不按姓名合并既有身份。单文件 API、V17 和依赖保持原状；构建及测试成功不表示本机正在运行的 JAR 已更新。

首次运行新版本需要应用 V17。它保留历史业务记录，新增结构并允许文件关键词的 source_id 为空；2026-09-11 本机试用启动已备份 `course_crawler`，运行当前 JAR 并确认业务库从 V16 升级至 V17。部署回退前保留新结构，旧应用不负责处理新增指导关系；不能通过删表来回退业务数据。项目记忆已与新路由、控制器及迁移核对，以下历史验收记录保留其日期和适用版本。

## 构建和入口

先运行 `scripts/Initialize-Integration.ps1` 准备本项目独立基础服务；首次执行 `scripts/Build-Integration.ps1 -Restore` 从各系统原有锁文件恢复依赖并构建，后续使用不带 `-Restore` 的命令。各系统继续独立管理 POM、Maven Wrapper、package.json、锁文件和 node_modules。

`scripts/Start-Integration.ps1 -System all -Mode Demo` 启动门户和两个 Java jar，Vite preview 从各系统 dist 提供页面；`Development` 额外启动两个 Vite 开发进程，仍通过 18000 统一访问。两种模式互斥。运行中配置发生变化时网关返回配置错误，必须重启门户以应用新配置。

开发模式可用 `node scripts/Test-SystemsBrowser.mjs --development` 验证真实登录、深链接刷新和通过统一入口建立的 HMR WebSocket 连接。历史四系统接入阶段曾通过此检查；本次统一登录的浏览器验收使用 Demo 模式，尚未重跑 Development HMR。演示模式无需运行独立 Vite 开发进程。

构建脚本明确检查正在运行的后端，避免 Windows jar 文件锁。构建使用 `-DskipTests package`，测试在验收阶段单独执行，不能把构建成功当作单元测试成功。

启停记录位于 `.local/integration/processes.json`，校验工作目录、PID、创建时间、可执行文件和命令行。总入口重复启动会拒绝；在门户已运行且模式一致时，可以恢复已停止的单个系统。停止不按端口或进程名批量结束，也不操作原项目。

根目录 `start.bat`、`stop.bat` 是 Windows 一键入口，均支持 `--help` 和 `--no-pause`，返回底层脚本的退出码，默认暂停以保留双击窗口。启动优先调用已有 `.local/Start-LocalProject.ps1 -System all`，否则调用 `scripts/Start-Integration.ps1 -System all -Mode Demo`；保留本机入口的密码交互，不传管理员引导参数。停止固定调用 `scripts/Stop-Integration.ps1 -System all`。两者使用 PowerShell 7，依次从 PATH、常规安装目录和当前用户的 Codex 已有运行时查找，不下载依赖、不初始化或重建、不停止数据库及容器。调用前切换到 BAT 所在根目录，结束后恢复调用方目录。

## 本地功能试用数据

默认初始化仍保持空业务库。需要试用数据时，先按 README 完成本机基础服务、构建与管理员准备，启动两个后端使表结构就绪，再在仓库根目录执行：

```powershell
node scripts/Import-IntegrationDemoData.mjs
node scripts/Import-IntegrationDemoData.mjs --check
node scripts/Test-IntegratedSystems.mjs
node scripts/Test-SystemsBrowser.mjs
```

`Import-IntegrationDemoData.mjs` 只连接归属于当前工作区 `course-integration` 的本机 MySQL 容器，不创建账号、不更改表结构、不调用外部采集或模型服务。数据库连接沿用容器内部环境，运行参数和输出不含凭据。每库独立事务；执行前核对两库容器归属和样例标识，已有完整样例则跳过，存在部分样例或 ID 冲突则明确失败，保留用户数据。重复执行不会重置用户对样例的修改；`--check` 仅核对数据库中的样例数量和标识。

| 系统 | 试用数据 |
| --- | --- |
| relation | 30 篇论文、16 位作者、6 个机构、6 个载体、10 个关键词、74 条署名、64 条主题关联、51 条引用。 |
| crawler | 12 项成果、8 位作者、4 个机构、5 个主题、2 个采集任务，以及来源、治理、质量、告警和模拟失败记录。 |

relation 的数据由 relation 原有 `sample-data.sql` 的简单 `INSERT ... VALUES` 数据转换，导入器不会执行原脚本的删除语句。relation 使用 `910100–910999` 数值标识范围；论文 DOI 为 `10.9999/course-demo.*`，标题、作者等带 `[试用]` 标记。relation 必填时间、版本、抽取状态与 `publication_year` 按实际 JPA 建表结果补齐，不依赖原独立运行 SQL 的默认值或生成列。

crawler 复用已有 `systems/crawler/tools/development/rendering-sample-data.sql`，样例 DOI 为 `10.9999/aacv-demo.*`，任务和异常说明带 `[页面测试]` 标记。此样例有意包含一次图同步死信、失败运行和质量问题，用于试用治理与运维页面；这些标记记录不代表本机基础服务当前不可用。未创建采集定时计划。

所有记录都是功能试用样例，样例外部标识和 DOI 不用于验证真实外部检索。relation、crawler 通过现有 outbox 机制生成图投影，导入后需等待异步同步完成。各系统仍保持独立数据库，无自动跨系统数据同步。


## 门户入口视觉与交互


门户采用深蓝科技主题：顶部导航与入口搜索、两张系统卡片、学术合作/研究分布/趋势面板和数据总览。布局及简单交互由 `portal/src/App.vue` 管理，主题位于 `portal/src/tokens.css`，响应式样式位于 `portal/src/style.css`。1200px 以下将辅助面板排到主入口后方，800px 以下入口卡片改为单列。

`portal/src/data/overview.js` 中的统计、趋势、研究方向与卡片视觉说明是演示内容，不调用业务数据接口；界面标注“示例”或“演示数据”。搜索仅筛选系统名称、功能与研究方向，不提供跨系统论文检索。趋势范围切换仅作用于演示数据。`PortalRoot.vue` 确认统一会话后展示门户；`PortalLogin.vue` 复用原主题、背景、按钮和图标，账号菜单显示当前用户名并提供统一退出。

系统名称、实际接入状态、维护说明和固定路径继续来自 `services/systems.js` 读取的 `/integration.json`。维护状态仍跳转到原 HTTP 503 维护页，加载失败仍提供重新读取；本轮运行接入对网关、配置和子系统的必要适配见下文。

图片与图标位于 `portal/public/assets/portal/`，通过现有 `/assets/` 白名单提供访问；不要改为根级 `/images/` 或 `/icons/`。图片使用无损 WebP，源文件与图标授权说明见该目录的 `README.md`。页面不使用远程字体/CDN，也没有增加 npm 依赖；图表在独立 `PortalChart.vue` 中绘制，卸载时释放 `ResizeObserver`。

`scripts/Test-PortalBrowser.mjs` 保留原有维护页和错误重试检查，并适配帮助弹窗、搜索空状态、趋势范围及移动导航检查。此次视觉与浏览器核对的证据、环境限制见仓库根目录 `design-qa.md`。

## 实际端口与数据隔离

| 系统 | 开发前端 | 后端 | Session Cookie / Path | MySQL 端口 / 库名 | Neo4j HTTP / Bolt |
| --- | --- | --- | --- | --- | --- |
| relation | 5174 | 18081 | `RELATION_SESSION` / `/relation` | 23361 / `course_relation` | 27471 / 27681 |
| crawler | 5176 | 18083 | `CRAWLER_SESSION` / `/crawler` | 23363 / `course_crawler` | 27473 / 27683 |

数据库容器属于 `course-integration` Compose 项目，每个库有独立应用账号、随机密码与卷。初始化脚本创建 `.local/integration-runtime` 并限制为当前 Windows 用户访问。不会读取原工作区 .env 或连接本机原业务库。临时文件、Socket 与 crawler 导出目录也在各系统私有运行目录中。

Neo4j 使用既有 5.26 Community 镜像；MySQL 使用既有 8.0.42 镜像。初始化会核对容器所属工作区；重复执行不更换密码、不删除数据，也不覆盖已有 application.properties。`Stop-Integration.ps1` 只停止应用；停止基础服务使用 `docker compose -f .local/integration-runtime/compose.json stop`。

## 接入配置契约

唯一来源为 `deploy/systems.json`，固定 relation、crawler 两个系统。支持 maintenance/enabled；非法状态、缺失来源、端口冲突、目录越界、缺少验收文档明确失败。

- `sourceBranch/sourceSha`：两个保留系统的已核定来源，删除系统的历史来源只在导入记录中保存。
- `acceptance/acceptanceSha`：对应系统验收文档及匹配的来源 SHA。
- `dist`：均为 `systems/<id>/frontend/dist`。
- `contextPath`：各 Java 后端使用 `/<id>` 上下文，网关保留该前缀；未设置此字段的旧适配模式仍剥离前缀。
- `readinessPath`：真实健康路径，两个系统均检查实际使用的 MySQL/Neo4j。
- `backend/frontend`：前台直接运行的 java/node 进程，参数不含凭据。`{workspace}` 仅由启停脚本展开为当前仓库绝对目录。

后端从外部 `application.properties` 加载独立连接信息、Cookie 名称与路径、端口及 integration profile。新环境默认不创建用户、不导入业务数据，首次启动只建立表结构及角色、字典等基础记录。relation 停用上游种子账号，其整合管理员引导仅在显式设置 `integration.bootstrap-admin.enabled=true` 时装配；缺省或 false 均不要求 `integration.admin-password`。原业务 profile 保留原行为。

初始化为新配置写入 `integration.bootstrap-admin.enabled=false`，crawler 另写入 `aacv.bootstrap-admin.enabled=false`、`aacv.bootstrap-admin.username=admin`，不再注入自动建号密码。开发者在首次后端建表完成后，手动向 `course_crawler.sys_user` 添加 `admin`，保存带 `{bcrypt}` 前缀的密码哈希和 `ACTIVE` 状态，并在 `sys_user_role` 关联 `ADMIN`。统一密码沿用 crawler；本机初始密码仍在 `.local/integration-runtime/crawler/credentials.json` 的 `admin` 字段，仅生成该文件不会创建账号。其他系统无需复制账号，具体本地哈希与 SQL 操作见 [README](../README.md)。

重复初始化仍保留已有配置、凭据和账号。旧 crawler 配置若含 `aacv.bootstrap-admin.enabled=true`，需开发者改为 false 并重启后端后才遵循空表不建号的规则；旧 relation 的密码字段不会独自启用引导。在账号管理中修改密码不会同步凭据文件，真实登录验收仍要求数据库密码与该文件一致。

前端构建使用 `--base=/<id>/`，Router 和集中请求层读取 `import.meta.env.BASE_URL`。relation 在安装路由前恢复会话，crawler 使用现有会话状态。所有系统提供返回门户链接。API/Actuator 先于静态资源和 SPA 回退；后端停止返回 JSON 503，不回退为 HTML。

统一登录沿用 crawler 账号、服务端 Session、账号状态与角色校验。网关 `/__integration/auth/*` 将其内部 `CRAWLER_SESSION` 转换为浏览器侧 `PORTAL_SESSION`（Path=/、HttpOnly、SameSite=Lax）；上表的各系统 Cookie 配置仍用于内部或独立运行。relation 后端在 integration profile 下逐请求查询门户身份，仅设置当前请求的 SecurityContext，不创建本地账号，也不保存独立登录状态。原 CSRF 与后端权限校验保留；RESEARCHER 在接入系统内只读。账号集中在门户 `/management/users` 管理，relation 的旧账号管理接口返回 410，其他业务管理接口保持原权限。

两套前端在各自集成 BASE_URL 下移除独立登录路由，旧登录页面由网关跳转 `/login`，旧认证写接口返回 410。未登录访问业务深链接会保留回跳地址；API 返回 401 会返回统一入口，网络故障返回门户供重试。任一系统的退出都撤销同一门户会话，旧 Cookie 重放也不能继续访问。统一认证服务依赖 crawler 和门户可用；没有离线授权缓存。同源子路径不能用作不互信应用之间的安全边界。完整契约、权限映射、验证和限制见 [统一登录说明](unified-login.md)。

`startupPlan` 为 relation 后端附加 `--integration.portal-url=http://127.0.0.1:<portalPort>`，统一认证地址随配置中的门户端口生成，不读取或更改本机凭据配置。单独停止 crawler 会使其他系统的身份确认返回 503；恢复 crawler 后可重新使用。旧 Nginx 静态生成模板未接入统一认证，不适用于此次统一登录运行；当前使用上述 Node/Vite 网关，不应将旧模板作为可部署的统一登录入口。


## 来源同步

原始导入树及来源证据保留在 `docs/import-records.json`。extraction、scholar 的记录标记为 retired，仅验证其历史来源，不再读取已删除源码；有效来源与运行系统列表必须一致。

`scripts/check-source.mjs` 核对历史原始来源树与 subtree 祖先关系，并逐文件验证 relation、crawler 的原始文件、新增文件和 `docs/source-adaptations.json` 中的适配哈希。适配记录只包含两个有效系统；删除原始文件必须显式记录 `adaptedBlob: null`，未记录改动、未经记录的缺失文件或过期记录均失败。文件核对由 `scripts/lib/source-adaptations.mjs` 执行。适配记录必须与实际 diff 一起审阅。

Ye 的远端回退已由接入阶段的 fetch 再次确认，当前保持本地完整版。后续不得直接将骨架覆盖到系统目录。其他分支同步时也应先核定 SHA 与历史，保留集成路径、会话和运行适配。当前集成版本在 `dev` 分支维护，跟踪 `origin/dev`；当前交付按采集能力、门户及系统整合、图谱修复分批提交，各批同步项目记忆与来源适配记录，再正常推送到 `origin/dev`。验收文档中的未提交说明记录对应实施阶段，实际交付状态以 Git 记录为准。main 合入、PR 与部署仍需对应任务的授权。

## 验证与环境限制

本次两系统删除与信息采集更名的实际命令、结果和文件清单见 [两系统验收](system-scope-acceptance.md)。

`scripts/Test-IntegrationInitialization.ps1` 在临时工作区执行初始化脚本，使用替代 Docker 函数验证新配置默认关闭建号、保留本机密码及重复初始化不覆盖配置和凭据，不操作实际数据库。relation 的 `IntegrationAdminBootstrapTest` 验证未配置密码、旧版密码字段和显式关闭三种情形均不会装配账号引导；原统一身份过滤器用例继续适用。

此前系统接入的历史命令和结果见 [本地运行验收](local-runtime-acceptance.md)，本次认证改造的实际命令和结果见 [统一登录说明](unified-login.md)。旧验收文档中的四系统和三系统描述仅为历史证据；当前有效系统以接入配置为准。

本机受限工具环境的 Node 子进程可能报 `spawn EPERM`，Docker named pipe 和 CIM 也需要工具允许的正常本机权限；这些权限问题不通过修改业务安全设置规避。Java 的 Windows Socket 路径使用本项目独立临时目录。

官方依据：[Vite 子路径构建](https://vite.dev/guide/build.html)、[Spring Boot 外部配置](https://docs.spring.io/spring-boot/reference/features/external-config.html)。Vite preview 仅用作本地入口，Nginx 本体与公网部署仍未验收。

## 图谱计数正常但画布空白的排查（2026-09-10）

下列执行记录对应历史版本。原 `graph-overview/GraphCanvas.test.ts` 已随旧画布移除，当前等效交互和清理验证位于 `components/business/GraphCanvas.test.ts` 与 `e2e/graph-overview.spec.ts`。

源码中的 CSS 动效时长可在构建压缩时由 `280ms` 改为等价的 `.28s`。`systems/crawler/frontend/src/composables/useMotion.ts` 必须识别 `ms`、`s` 并统一返回毫秒；禁止只用 `parseFloat` 后直接传给 Cytoscape 或 ECharts。vis-network 已退出当前依赖，下段保留其历史故障证据。零时长和减少动画偏好仍返回零；缺失、非法、负数或溢出的配置使用各类动效原有默认值。

本次故障中 `/crawler/api/v1/graph/overview` 返回 20 个节点、38 条关系，`/crawler/actuator/health/graph` 为 `UP`，画布尺寸正常，但秒值被误作毫秒导致 vis-network 动画后的缩放比例为负、实际绘制像素为零。排查此类问题需区分接口空态与有数据未绘制，无需重建数据库或重放图投影。

回归覆盖 `useMotion.test.ts` 的单位和边界条件、`e2e/graph-overview-vis.spec.ts` 的秒单位多节点绘制。`node scripts/Test-SystemsBrowser.mjs` 额外检查 crawler 独立入口和门户 iframe 的持续绘制像素，以及工作区刷新和适应画布；数据库为空时验证正常空态。该脚本使用现有本机验收账号，不输出凭据。修复前单位测试与画布回归均重现故障；旧构建也未通过新增的像素断言。

前端修改后运行 `npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/` 更新本机 Demo 产物，再运行上述浏览器脚本；只修复前端无需重启 Java 后端。Development 模式的测试结果不能替代压缩构建产物验收。

本轮已执行的验证：

| 命令 | 结果 |
| --- | --- |
| `npm.cmd --prefix systems/crawler/frontend test -- src/composables/useMotion.test.ts src/components/business/graph-overview/GraphCanvas.test.ts src/utils/graph-rendering.test.ts src/components/EChartCanvas.test.ts` | 4 个文件、35 项测试通过，包含共享动效的图谱及 ECharts 调用方。 |
| `npm.cmd --prefix systems/crawler/frontend run test:e2e -- e2e/graph-overview-vis.spec.ts` | 9 项通过，覆盖压缩单位、绘制、缩放、拖动、空态和窄屏。 |
| `npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/` | 类型检查和生产构建通过；保留已有超过 500 kB 的分块警告。 |
| `node scripts/Test-SystemsBrowser.mjs` | 新构建的真实接口、独立入口及门户工作区绘制、刷新、适应画布和返回通过；相同像素检查在旧构建上失败。 |
| `node scripts/check-source.mjs` | relation、crawler 的来源树、文件及适配哈希检查通过。 |

本轮保留已有未提交改动，只重建 crawler 前端，未修改或重启后端。测试使用现有本机服务及 Edge；未执行其他浏览器或公网环境验收。初次受限运行的 `spawn EPERM` / `spawnSync git EPERM` 属于工具进程权限限制，相关验证已在正常本机权限下完成，未修改业务权限配置。

## 统一管理与全屏工作区（2026-09-10）

门户顶部提供用户管理、日志管理和全屏控制。平台账号页面为 /management/users（含 /management/users/overview），平台请求日志为 /management/logs，原登录与采集后台审计为 /management/audits。管理页使用 crawler 前端新增的 management.html 多入口产物，复用已有用户与审计组件、统一账号后端及 CSRF/版本号/权限校验；没有新建用户库、迁移账号或增加依赖。网关先校验 USER_LIST / AUDIT_READ 再返回管理页面，平台日志 API 每次确认实时身份。集成模式移除 crawler 与 relation 的独立管理入口；旧地址导向门户管理，独立运行保留来源系统兼容行为。

门户通过同源 iframe 承载工作区，顶层 document.documentElement 管理全屏。各系统顶部左侧返回链接在门户中由父容器接管；直接访问子系统时仍可返回根入口。仅接受来源与当前工作区窗口均匹配的消息；地址继续使用固定业务路径白名单。子系统路由同步为 /?workspace=编码后的业务地址，刷新恢复当前页面；浏览器整页刷新或关闭页面会按原生规则退出全屏，Esc 可退出。子系统不再提供全屏按钮。

scripts/lib/platform-audit.mjs 记录经门户网关的两系统 /api/v1 请求与门户登录、退出，统一用户管理归属 portal。仅保存时间、用户名、系统、请求方法、脱敏路由、响应状态和耗时，不保存请求体、查询值、Cookie、密码或原始异常。客户端取消记录为 499。日志保存于 Git 忽略的 .local/integration/platform-audit.jsonl，最多保留约一万条，超限整理为最近九千条；重启可恢复，磁盘故障明确报错。原 crawler 后台任务审计仍在数据库，其他 JVM 的原始进程日志继续留在现有 .local/integration 目录，不将请求日志表述为所有进程日志的集中收集。可按系统、用户、时间和结果筛选，分页上限为100。

采集启动失败会在同一事务保存 SYSTEM 失败明细及 OPERATION_FAILED 审计，保留运行编号、固定错误类别和处理建议；同一终态不重复写入。无原始记录的启动失败不标记为可重试的数据记录，应排查后对任务再次执行。现有业务状态和数据库 schema 不变。界面区分“批次未启动”和采集中失败，数据处理计数不把系统级错误当成失败学术记录。

本次截图中的运行编号在当前本机库不存在。修改前的直接启动和提交后启动测试均通过，运行 jar 的启动器类与当前编译结果一致；因此截图中该次失败的触发根因尚未核实，未把补齐诊断记录表述为已复现并消除其原始故障。验证记录与剩余条件见 platform-management.md。

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

## 2026-09-10 采集子系统体验与复杂度收敛

本轮默认首页改为工作台，原大屏位于独立 `/dashboard`，集成地址为 `/crawler/dashboard`。目录筛选及分页写入 URL，详情保留返回条件；作者、机构、期刊和主题支持选中 ID 精确匹配或自由文本模糊匹配。导出沿用已显示结果的筛选，后端复用 `CatalogMapper.achievementFilters`，保留旧导出 ID、年份区间与 sourceType 字段。

采集任务列表按创建顺序倒序展示，通过 `/api/v1/crawl/tasks/latest-runs?taskIds=...` 与 `/api/v1/crawl/tasks/schedules?taskIds=...` 批量读取本页状态，每次最多 100 个正整数 ID，分别沿用 CRAWL_RUN_READ 与 CRAWL_SCHEDULE_MANAGE 权限。运行编号统一显示数字 ID，UUID 仅在技术标识中保留。运行详情用处理计数与结束原因表达状态，不再将采集上限当成总量。任务、计划、运行弹窗分别位于 `components/business/crawl/`；高级采集参数默认折叠，数据源校验统一使用 Element Plus。

图谱概览、高级查询、路径分析统一使用 `components/business/GraphCanvas.vue`（Cytoscape）；`utils/graph-presentation.ts` 保留响应验证与类型默认值。关键词和类型筛选不再重复请求，双击进入中心与显式刷新仍受取消、序列校验保护。样式入口改为 `/graph/settings/nodes`、`/graph/settings/edges`，旧地址为兼容别名。删除闲置 OperationsView、health 服务、preferences store、motion 工具和旧 vis 画布，移除 vis-network、vis-data、vee-validate、@vee-validate/zod；后端运维、采集检查点、调度及权限功能保持。

实施前读取本文件与另一份指定项目记忆，核对源码及工作区；根目录 `11` 的既有删除未改动。本轮对首页、画布和筛选语义的变更已同步 README，旧图谱空白修复段落属于历史验收，其动效单位约束继续适用。未改数据库结构、凭据或部署配置，未提交、推送或部署。验证证据与剩余边界见 [采集子系统改进记录](crawler-ux-improvements.md)。

运行控制现在会作废先前轮询，避免迟到状态覆盖暂停或取消结果。本轮在用子系统的来源与适配哈希一致；整仓来源命令仍受本机历史 scholar 原始 Git 对象缺失影响。未修改归档 SHA 或跳过历史验证，具体命令及数据库验收限制见上述改进记录。

## 2026-09-11 本机 MySQL 运行实例

在 `E:\Program\Java\course_design\XSGXZSTP-KS` 按用户选择使用 Windows MySQL80，两个项目库为 `127.0.0.1:3306/course_relation`、`course_crawler`。Neo4j 继续使用 `neo4j:5.26-community`，容器为 `course-integration-relation-neo4j-1`、`course-integration-crawler-neo4j-1`，HTTP/Bolt 端口保持 `27471/27681`、`27473/27683`；数据卷为 `course-integration_relation-neo4j`、`course-integration_crawler-neo4j`，重启策略为 `unless-stopped`。其他工作区仍按上文默认的隔离 MySQL 容器流程初始化。

此实例的辅助脚本、Compose 配置及外部应用配置位于 Git 忽略的 `.local/`。启动使用 `.\.local\Start-LocalProject.ps1`，通过交互提示取得本机数据库密码；停止使用 `.\scripts\Stop-Integration.ps1 -System all`。已创建统一管理员 `admin`，后续启动无需 `-BootstrapAdmin`。应用配置只引用进程环境变量，Neo4j 认证从经归属校验的已有容器读取，未生成明文凭据文件。详细命令见本机 `.local/README-local-runtime.md`；这些本机脚本不会随 Git 克隆迁移。

本机可选样例导入使用 `node .local/Import-LocalDemoData.mjs`，通过当前进程的 `COURSE_MYSQL_PASSWORD` 连接固定项目库。该入口加载原 `scripts/Import-IntegrationDemoData.mjs`，仅替换连接与归属校验边界，保留样例标识、事务、冲突检测、数量核对和重复导入保护。原导入器仍适用于默认容器方案，不能直接用于本实例。两套图数据仍由原 Outbox 逻辑生成。

当前 `GraphCanvas.vue` 使用 Cytoscape 多层画布，仓库 `Test-SystemsBrowser.mjs` 的单个 `canvas` 定位会产生严格模式错误。本机 `.local/Use-LocalTestCredentials.mjs` 在运行验收时将凭据来源替换为进程环境，并将像素检查定位到 `canvas[data-id="layer2-node"]`；登录、权限、连续像素、刷新与适应画布断言保持不变。原验收脚本的通用定位尚未修改；不把未适配脚本的首次失败记录为成功。

## 2026-09-11 本机 Docker 数据盘迁移

本机 Docker Desktop 4.72.0 的 WSL 存储根目录已迁移至 `E:\docker\data`，`%APPDATA%\Docker\settings-store.json` 中的 `CustomWslDistroDir` 指向该目录。数据盘为 `E:\docker\data\disk\docker_data.vhdx`，`docker-desktop` 的系统盘为 `E:\docker\data\main\ext4.vhdx`；WSL 注册路径已通过 `wsl --manage docker-desktop --move E:\docker\data\main` 同步。

两份磁盘在首次启动前均通过 SHA256 校验。迁移前的原始数据盘完整保存在 `E:\docker\backup-20260911\docker_data.vhdx`，其哈希仍与迁移前一致；C 盘原数据盘已移除以释放空间。校验及恢复位置记录在 `E:\docker\migration-20260911.json`，这些本机文件不随 Git 克隆迁移。`E:\docker\DockerDesktopWSL` 中已有的旧盘保持原状。恢复时应先退出 Docker，确保新旧副本不会同时挂载到 WSL；相同磁盘标识会阻止 Docker 选择数据盘。

迁移后曾因 `Page expected to be: 13, but self identifies as 0` 无法启动；迁移前日志已有元数据库的 I/O 错误。现已在经校验的整盘副本上恢复快照元数据库、ext4 结构和受损镜像内容，再切换至原 E 盘运行路径。切换前全部数据卷的文件聚合 SHA256 与修复前一致，原始备份及本次切换前运行盘均保留。Docker Engine 29.4.2 已恢复，两个项目 Neo4j 容器使用原 ID 和卷且健康检查通过；认证只读查询分别返回 relation 68 个节点/262 条关系、crawler 32 个节点/90 条关系。恢复材料、验证命令及后端验收边界见 [Docker 修复与后端验收](backend-acceptance-20260911.md)。

## 2026-09-11 作者导入与验收约定

当前 crawler 以 XLSX/XLS/CSV 作者导入、成果检索、统计和知识图谱为主，数据源、采集任务、数据治理、质量指标的页面和业务 API 已退场。历史基础表和数据保留，新版启动后停止旧采集计划。V17 增加作者导入记录及导师/成果机构关系，图类型共 13 种；知网字段映射、预览确认、原子导入、重复导入与权限约定见 [作者导入说明](author-import.md)。上文 2026-09-10 采集界面和调度说明保留为历史记录，不作为当前入口依据。

后端验收采用隔离数据库。crawler 沿用 Testcontainers；relation 的 `local` 测试依赖样例数据，必须先准备独立 MySQL/Neo4j、执行现有 schema/sample SQL 并完成 Outbox 图同步，再运行 `mvnw.cmd verify`。本机复现入口为 Git 忽略的 `.local/docker-repair-20260911/run_relation_acceptance.py`；凭据只在进程环境流转，测试容器限定回环地址并在结束时按归属清理。该验收不会替换业务后端或迁移 Windows MySQL 项目库。

## 2026-09-11 本机试用启动

用户要求启动当前版本试用后，复用既有 Demo 模式、三个前端构建和两个后端 JAR 启动。启动前通过本机安全输入窗口取得 MySQL 密码，仅用于当前进程；Git 忽略的 `.local/Start-ReviewProject.ps1` 先备份 `course_crawler`，再调用原 `Start-LocalProject.ps1`。备份和脱敏状态位于 `.local/review-start-20260911-171852/`，目录限制当前用户、管理员和 SYSTEM 访问，备份 SHA256 已复核；未重置账号或导入新的测试资料。日常启动、停止仍使用根目录 `start.bat`、`stop.bat`。

本次启动前 Docker 因失效的零字节 AF_UNIX 套接字退出。确认 Docker/WSL 已停止且目录仅含运行时套接字后，将 `%LOCALAPPDATA%\Docker\run` 和 `%LOCALAPPDATA%\docker-secrets-engine` 改名为各自的 `.before-start-20260911-171755` 备份，再启动 Docker；未修改数据盘配置。两个原图库恢复 `healthy`。今后若出现同类错误，应重新核对日志、进程和目录内容，不能直接复用目录改名命令。

实际日志与数据库查询确认 `course_crawler` 从 V16 升级至 V17；三个登记进程的 PID、创建时间、路径及命令行归属验证通过。门户、relation 健康接口、crawler readiness 与 graph 均返回 200/UP。未登录访问 `/crawler/author-import` 返回登录跳转，导入 API 返回 401，原认证边界保持。试用入口为 `http://127.0.0.1:18000/crawler/author-import`；使用原账号登录。本次启动检查未替用户上传真实知网文件，也未替代此前的隔离后端验收。
