# 本地开发与运行维护

本文件与 integration-baseline.md 为指定项目记忆。当前唯一系统为学术成果信息采集及可视化系统；2026-09-12 已移除 relation 和门户源码。后文带日期的运行证据只适用于记录时点。

## 当前系统与入口

访问根地址自动进入 /crawler/；登录为 /crawler/login。使用成果系统自己的 Vue 路由、导航和页面，不再有系统卡片或 iframe。/crawler/、MySQL course_crawler、Neo4j、账号与角色保持兼容，未新增本轮数据库迁移或依赖。成果系统内的三类学术图谱继续保留。

前端旧 `/overview/activity` 返回 `/` 工作台，集成地址为 `/crawler/overview/activity` → `/crawler/`；旧 `/operations` 及其兼容子路径转到 `/logs`，继续检查 `AUDIT_READ`。工作台入口不要求审计权限，未登录访问时保留工作台作为登录后的目标。浏览器回归分别验证这两类兼容入口。

Node/Vite 网关移到 scripts/server.mjs，复用 systems/crawler/frontend/node_modules。deploy/systems.json 只接受 crawler。内部字段 portalPort、网关进程标识 portal、PORTAL_SESSION Cookie 及 /__integration/* 接口保留兼容；它们不是另一个业务系统。

## 浅色研究页面（2026-09-12）

前端固定使用浅色，不随操作系统的明暗偏好切换。`systems/crawler/frontend/index.html` 设置静态 `data-theme="light"`、`color-scheme: light` 与首帧背景；页面初始化将旧 `aacv-theme` 迁移为 `light`。`src/composables/useTheme.ts` 向页面、图表和图谱提供相同的只读浅色状态，并处理本地存储不可用的情况。

颜色与组件样式沿用 `src/styles/tokens.css`、`index.css`、`element-plus.css` 和 `research.css` 的共享变量与覆盖规则。页面使用浅蓝背景、白色卡片/表格/弹层、深色正文与蓝色主操作；输入、状态、焦点和选中状态保留辨识度。顶栏、工作台与大屏使用 `public/images/campus-banner.png` 作为校园装饰，资源地址通过 `import.meta.env.BASE_URL` 适配独立入口和 `/crawler/` 集成入口。

登录页单独按校园山水参考图还原：`src/views/LoginView.vue` 使用局部样式和登录专用色值，桌面为顶部居中品牌、左下能力说明与右侧白色表单卡片；小于 1100px 时改为单列表单，较矮屏幕允许自然纵向滚动。专用背景为 `public/images/login-campus-background.png`（1672 × 941），由用户参考图移除文字与界面后生成，不覆盖共享 `campus-banner.png`，仍通过 `BASE_URL` 加载。输入框保持不透明白底；密码显隐按钮常显、可用键盘操作并提供状态与名称。登录 API、会话 store、校验、防重、回跳和权限契约不变。专项视觉检查及运行证据见根目录 `design-qa.md` 的登录参考图章节。

`BusinessLayout.vue` 统一提供顶部 `ModuleNavigation`，可视化大屏也使用该主导航；十个主模块继续按 `nav.ts` 和账号权限过滤。小于 1024 像素时隐藏横向模块导航，使用顶部导航按钮打开已有抽屉；原快捷键、命令面板、返回工作台和全屏入口保留。

`WorkbenchView.vue` 的平台数据概览读取已有 `analyticsApi.overview({})`，展示成果总数、作者总数、机构总数与包含摘要数量；请求前检查 `ANALYTICS_READ`，无权限不请求统计接口。统计和最近导入各自维护加载、错误、重试和请求序号，卸载后失效旧响应。最近导入继续检查 `AUTHOR_IMPORT`，通过 `authorImportApi.recent()` 读取最多六条记录，以表格显示作者、文件、导入类型、时间及新增/已存在成果数，不从参考图填入虚构统计或学者资料。

`academic-graph.ts` 为独立图谱中的 `AUTHORED`、`SUPERVISED` 普通连线标记交互文字模式，`GraphCanvas.vue` 在悬停或选中时展示标签，合作边保留“共同创作”及原证据操作。作者仍使用通用图标，不补造真人头像或教育、任职经历；`AcademicGraphView.vue` 的背景模块继续展示真实成果时间线。大屏 `OverviewView.vue` 的作者与机构网络来自现有合作统计，机构网络使用 `circular` 布局，节点和合作对均来自返回数据。此次调整不修改业务 API、后端、数据库、业务数据、认证或权限契约；原导入、ORCID 和日志约定继续适用。

大屏合作网络启用现有 ECharts 的 `LabelLayout` 特性和 `hideOverlap`：节点名称拥挤时自动避让重叠标签，保留全部节点、关系和悬停详情；不通过删除数据来简化画面。

## 构建和入口

仓库根目录使用 PowerShell 7；环境与首次建号步骤见 [README](../README.md)。

```powershell
.\scripts\Initialize-Integration.ps1
.\scripts\Build-Integration.ps1 -Restore
.\scripts\Start-Integration.ps1 -System all -Mode Demo
```

初始化只创建 crawler 的 MySQL 与 Neo4j 两项服务、两个卷；重复运行保留既有配置与凭据，不删除历史容器或数据。构建一个前端与一个后端，Maven 使用 -DskipTests package，测试独立执行。Windows 运行中的 JAR 有文件锁，重建前先停止 crawler。

start.bat 优先使用已有 .local/Start-LocalProject.ps1，否则调用标准启动脚本；stop.bat 停止有归属记录的应用，保留数据库。当前本机启动脚本也只检查 crawler 的 Neo4j，不再要求 relation。

Demo 启动网关和一个 Java 后端；Development 另启一个业务 Vite 服务。两种模式互斥。-System crawler 只选择成果后端，-System portal 仅选访问网关，relation 不再是可启动对象。停止脚本仍认识历史 relation 进程记录，以便旧工作区清理应用进程。

| 服务 | 端口或路径 |
| --- | --- |
| 访问网关 | 18000 |
| 业务 Vite | 5176 |
| 业务后端 | 18083，/crawler |
| 标准初始化 MySQL | 23363，course_crawler |
| Neo4j | HTTP 27473，Bolt 27683 |

本机 MySQL80 的环境沿用本机运行配置，不自动改写连接信息。进程状态位于 .local/integration/processes.json；启动、停止均校验目录、PID、创建时间和命令行。网关工作目录为 scripts/，所有后台进程使用隐藏窗口。

### IDEA 本机固定配置（2026-09-12）

本机 IDEA 调试按用户要求使用固定配置，不再要求手动填写后端环境变量。数据库连接值保存在 Git 忽略的 `.local/integration-runtime/crawler/application.properties`，管理员初始化固定关闭；文件含本机凭据，不提交或分享。新克隆不会携带此文件，仍需按 README 准备自己的运行配置。此前通过进程环境变量传递密码的记录仅描述历史启动方式。

在 IDEA 中打开仓库根目录 `XSGXZSTP-KS`，将 `systems/crawler/backend/pom.xml` 添加为 Maven 项目，再选择仓库提供的 `CrawlerBackend` 运行配置。该配置指定正式后端工作目录、外部本机配置和 JDK `temurin-21`；若本机 SDK 名称不同，在运行配置中选择已安装的 JDK 21。它不保存密码或环境变量。

本机取消关联备份后，正式模块仍名为 `system (1)`，`CrawlerBackend` 已与该名称对齐；新的工作区可能生成 `system`，应在 `Use classpath of module` 中选择正式 POM 对应的模块。当前本机仍选中的 `AacvSystemApplication` 配置也已补齐 JDK 21、工作目录和外部配置参数。配置名称不同不影响业务，实际命令行必须包含外部配置参数；仅选择正确 Maven 项目不会自动更新旧运行配置。若 IDEA 已打开运行配置窗口，应关闭再打开以核对磁盘更新。

`.local/` 内的目录是备份和运行材料，不能作为 Maven 模块导入。若之前打开的是父目录 `course_design`，应重新打开本仓库并重新导入正式 POM；启动日志中的 classes 路径必须来自 `systems/crawler/backend/target/classes`，不能含 `.local/author-import-before-20260911`。仅修改工作目录不会改变已选模块的 classpath。

正确运行时应使用 `integration`、后端 `18083 /crawler`、MySQL `3306/course_crawler` 和 Neo4j Bolt `27683`。IDEA 启动前停止原有同端口后端，保留 MySQL 和 Neo4j。此配置只启动 Java 后端；前端与访问网关仍按既有 Development 模式运行，入口保持 `http://127.0.0.1:18000/`。

日常双击根目录 `start.bat` 使用 Demo 模式启动访问网关和已打包的后端；本机启动脚本检查外部配置是否引用密码环境变量，已有固定连接配置时不再重复询问密码，旧环境变量配置仍保留安全输入流程。BAT 不自动构建；缺少 `systems/crawler/backend/target/system-0.0.1-SNAPSHOT.jar` 时，先执行 `scripts/Build-Integration.ps1`。`stop.bat` 只停止 `.local/integration/processes.json` 登记且归属验证通过的应用，不接管 IDEA 或其他方式启动的 JVM；切换启动方式前先停止原实例，避免同时占用 18083。命令行验收可使用 `start.bat --no-pause`、`stop.bat --no-pause` 获取真实退出码。

本次通过正式后端离线编译，并在移除系统环境变量与系统属性来源后验证配置加载、MySQL 只读查询及 Neo4j 只读连接；未由工具操作 IDEA 启动应用，用户仍需重新导入正式 POM 后选择上述运行配置。本机凭据文件已限制普通用户组的继承访问，具体检查命令与边界保存在 `.local/idea-startup-review/verification.md`。

## 登录、管理与日志

登录表单、会话失效、退出均由成果前端处理。网关将既有 PORTAL_SESSION 转换为后端 CRAWLER_SESSION；/crawler/api/v1/auth/login 与 logout 复用原认证代理，Origin、CSRF、会话轮换和后端实时权限校验继续生效。旧 /login 和管理书签只作安全跳转。

账号管理为 /crawler/users，日志管理为 /crawler/logs，仅保留操作日志和登录日志。旧 /management/users 转到账号管理，/management/logs、/management/audits 和 /crawler/request-logs 转到日志管理并保留分类查询参数；UI 守卫检查 USER_LIST/AUDIT_READ，真实数据 API 仍在服务端校验权限。

2026-09-12 移除请求日志页面、专用查询 API 和网关自动记录逻辑；旧 /__integration/platform/logs 返回 404，不再为记录请求额外读取身份或写入日志文件。历史 .local/integration/platform-audit.jsonl 保留原状，不执行数据清理。操作与登录审计继续使用 MySQL 和 /api/v1/operations/audits，原权限、分类和详情契约保持。

日志页复用成果目录的紧凑字段搜索组件，账号输入最多 64 字符；右上角提供搜索、重置、刷新，时间范围、事件类型、结果从对应表头筛选。时间以本地时间编辑，提交时转换为 UTC，起点包含、终点不包含，非法区间不发请求。切换分类清除事件类型、保留账号/时间/结果；重置保留当前分类，分页和刷新使用已提交条件，旧请求取消机制保持。成果顶部提供原生全屏，业务路由切换保持全屏。

本次日志改动已构建前端并通过模拟浏览器与网关测试，未重启现有业务进程。Demo 模式刷新页面即可加载前端产物；网关代码需按原运行模式重启访问网关后生效。使用 `scripts/Stop-Integration.ps1 -System portal` 和 `scripts/Start-Integration.ps1 -System portal -Mode Demo` 只重启访问网关，保留运行中的 Java 后端与数据库；Development 模式应保持原模式参数。

## ORCID 后台获取已移除（2026-09-12）

2026-09-12 按用户要求删除 ORCID 获取、重试、回填和绑定接口、HTTP 查询客户端、处理器及专用 MyBatis 映射。作者导入不再为署名作者、导师创建 ORCID 查询任务；前端残留补全面板、服务与对应测试一并移除，继续显示原有 `authorId`。已有 `author_external_id` 编号及目录、作者和导师的图谱字段保留，原导入事务与 Outbox 不变。

`OrcidQuartzCleanup` 启动时在原 JDBC 事务中删除 `aacv-author-orcid.author-orcid`，不再注册或重建触发器。`OrcidQuartzJob` 仅兼容数据库中保存的旧类名；若遗留任务先于启动清理执行，只删除自身调度，不访问外部服务。保留 V18 迁移和 `author_orcid_task` 历史记录，不清库或新增依赖。`/api/v1/author-orcids*` 已从代码和 OpenAPI 移除，原 ORCID 开关与凭据配置不再被这条流程读取。IDEA 必须重新编译并重启后端，旧 JVM 不会自动加载此变更。具体边界见 [ORCID 获取停用说明](author-orcid.md)。

早期 ORCID 启用的备份和状态位于 `.local/orcid-review-20260912-104302/`，只描述当时版本。下文作者内部标识、V17 和真实数据验收均为对应阶段的历史记录。

### 作者内部标识与导师显示验收（2026-09-12）

作者导入页不再挂载 ORCID 组件，因此不发起其页面请求；导入结果和最近导入表使用已有 `authorId`，图谱入口仍使用同一标识。相关单测 6 项、桌面/窄屏及编目浏览器回归 4 项通过，浏览器接口为模拟数据。

真实 MySQL 的博士论文 486、487 已关联导师作者 9“席酉民”，缺失的是旧运行 JAR 的导师响应字段。更新前后真实 API 从无导师值变为 `advisors: ["席酉民"]`，未改写论文或导师数据。`CatalogWorkEntitiesIntegrationTests` 的 5 项及 `OpenApiDocumentTests` 的 1 项通过；独立构建包 `system-0.0.1-SNAPSHOT-author-id-advisor-review.jar` 已替换到默认启动路径。标准日常启停仍使用原项目脚本，不重复运行本轮一次性脚本或早期 ORCID 包。

本轮数据库备份在 `.local/author-id-advisor-review-20260912/runtime-retry/`，只允许当前用户、SYSTEM、管理员访问。一次性校验脚本曾发生查询结果和导入历史数组解析错误；后续通过原 `Start-LocalProject.ps1 -System crawler` 入口恢复新版，最终状态见 `recovery-status.json`，就绪与图谱健康均为 `UP`。运行中的 JAR 与已核对真实导师 API 的包相同；前端 108 个文件逐一校验，网关已提供新页面资源。全过程未增加迁移，作者/成果/指导关系数量保持 211/476/2。详细命令、失败与恢复证据在同目录 `verification.md`，不要将中间失败状态当作当前状态。

## 三类学术图谱（2026-09-11）

crawler 的三个独立主模块为 `/academic-relations` 学术关系、`/academic-achievements` 学术成果与 `/academic-background` 学术背景，均以规范作者 ID 为中心。本人论文、专利使用 `AUTHORED`，指导硕博使用 `SUPERVISED`；机构、期刊、主题及其他成果类型不作为图谱节点，原记录继续保留。背景图按成果日期正序分页，缺日期置于末尾，不推断教育或任职经历。

2026-09-12 按逐篇查看要求，背景页在每项成果卡片展示“发布时间”和“机构”，替换作者层面的机构汇总。`GET /api/v1/graph/authors/{authorId}` 在成果节点属性中返回 `institutions: [{ id, name }]` 和 `institutionsTruncated`；先按成果分页，再合并本篇 `PRODUCED_AT` 与本篇作者/导师对应的 `AFFILIATED_WITH`，后者必须满足 `achievementBusinessId` 等于本篇成果 ID，且节点和关系均为托管。按机构 ID 去重、升序返回前 100 家，截断标记独立于图谱节点截断，不改变节点数量与分页。机构信息与成果一起读取，沿用 `GRAPH_READ`；打开完整目录资料仍要求 `CATALOG_READ`。空机构或缺日期显示“未收录”，旧后端缺少机构字段时显示“暂未返回”，无机构名称时显示编号，不推断发布地点、出版机构或当前任职。

账号列表、创建/编辑/调整角色和顶部账户菜单统一使用 `frontend/src/utils/roles.ts` 的中文名称：管理员、数据运营人员、科研用户；接口仍提交 `ADMIN`、`DATA_OPERATOR`、`RESEARCHER`，权限判断保持原状。运行方式决定前端变更如何生效：Development 使用 Vite 源码；Demo/BAT 使用 `systems/crawler/frontend/dist`，修改源码或仅构建到 `.local/` 验收目录不会更新正式页面。仅修改前端时，在 `systems/crawler/frontend` 执行 `npm.cmd run build -- --base=/crawler/` 后刷新页面；后端代码变更才需要重新打包并重启相应后端。

2026-09-12 处理背景页仍不显示机构：实际网关已切换 Demo，正式前端仍为 18:51 的旧构建，既无机构字段也无“发布时间”文案；运行 JAR 已包含逐篇机构实现。已将验证通过的新前端资源同步到正式 `dist`，保留旧资源及入口备份，最后切换入口，108 个文件哈希与新构建一致；未重启网关、后端或改写数据库。当前网关资源的桌面、手机端显示均已通过模拟响应验收，不能视为用户已登录真实数据的接口验收。复现、构建、发布核对与截图见本机 `.local/background-runtime-fix-20260912/verification.md`。

新增只读 `/api/v1/graph/authors/{authorId}`，先按成果分页再补共同作者，维持 300 节点与 3 秒查询限制。合作边只关联中心作者，证据仅覆盖当前页且不以指导关系推断共同署名。原全局概览移到 `/graph/overview`，原高级工具保留；旧 `/graph/relations`、`/graph/achievements`、`/graph/background` 跳转到三个独立路径并保留查询参数，样式入口为 `/graph/settings/edges`。作者导入、工作台的作者图谱链接进入成果模块；三个模块切换保留作者。

这些图谱保持现有查询、权限及数据模型，真实图谱交互的历史验收见 [三类学术图谱](academic-graphs.md)。

## 研究页面紧凑布局与编目（2026-09-11）

三个图谱不再放在知识图谱父菜单下；沿用共享图谱组件、请求生命周期与 GRAPH_READ 权限，主导航、侧栏及命令面板切换保留作者。内容清单默认收起，标题、作者和分类选择集中在顶部工具栏，画布填满剩余宽高。

成果目录右上角通过字段选择搜索题名、作者、机构、期刊或主题，发表年份从日期列表头的日历选择；已删除的类型和来源条件不会从旧 URL 隐式恢复。导出按钮内保留 CSV/JSON 任务与下载，使用已提交结果条件。统计页沿用已有机构、主题的规范 ID 筛选，年份范围在年度图或趋势表头选择；其他统计分类提供同一日历入口。分类导航合并为一行，指标为紧凑摘要。统计导出使用已加载的聚合结果，合作排行最多二十项，导出入口沿用 EXPORT_CREATE 权限。

编目 GET `/api/v1/catalog/{collection}` 增加 `patents`、`master-theses`、`doctoral-theses`，规范成果类型分别为 `patent`、`master-thesis`、`doctoral-thesis`；后两者必须有 `achievement_advisor`，合并成员与多人指导去重。编目列表展示内部 ID 与中文类别，指导硕博编目另返回 `advisors` 姓名数组，按规范作者身份去重，页面新增“导师”列；作品通过现有成果详情接口查看；无表结构、依赖或数据迁移。两份指定记忆已对照当前路由、SQL、接口与前端修正旧父菜单描述；具体测试及试用版本状态见 [研究页面调整](compact-research-ui.md)。

## 作者信息表导入（2026-09-11）

crawler 使用知网 XLSX/XLS/CSV 文件作为新增学者资料的入口，页面 `/crawler/author-import`，API `/api/v1/author-import`（集成前缀 `/crawler`）。四个旧模块的页面和控制器已删除；历史 SQL 数据与必要的内部模型保留。旧恢复监听不再装配，兼容 Quartz Job 只注销旧采集计划。不要使用历史采集操作说明作为当前入口。

后端新增 POI 与 Commons CSV 依赖，以及 Flyway V17 导入表和 `SUPERVISED`、`PRODUCED_AT` 关系。仍按 MySQL 事务提交业务数据与 Outbox，再同步 Neo4j。文件导入不触发知网网络访问，也不虚构来源采集运行。知网表头、重复判定、身份边界、权限与验证命令见 [作者导入说明](author-import.md)。

作者导入页面现支持同批多文件自动识别：本人署名表的完整作者交集唯一时确定学者，多人时点选候选；硕博按用户确认的导师姓名筛选约定建立指导关系，来源库逐行区分硕士、博士。机构自动提取为成果机构，不据此推断当前所属单位。新增 `/api/v1/author-import/files/preview`、`files/confirm`，一次最多 10 份、合计 10 MB 和 2000 条，在同一 MySQL 事务提交。仅有硕博文件无法识别导师，需附本人署名表；缺少共同成果时不按姓名合并既有身份。单文件 API、V17 和依赖保持原状；构建及测试成功不表示本机正在运行的 JAR 已更新。

首次运行新版本需要应用 V17。它保留历史业务记录，新增结构并允许文件关键词的 source_id 为空；2026-09-11 本机试用启动已备份 `course_crawler`，运行当前 JAR 并确认业务库从 V16 升级至 V17。部署回退前保留新结构，旧应用不负责处理新增指导关系；不能通过删表来回退业务数据。项目记忆已与新路由、控制器及迁移核对，以下历史验收记录保留其日期和适用版本。

## 来源同步

原始导入树及来源证据保留在 `docs/import-records.json`。relation、extraction、scholar 的记录标记为 retired，仅验证其历史来源，不再读取已删除源码；有效来源与运行系统列表必须一致。

`scripts/check-source.mjs` 核对历史原始来源树与 subtree 祖先关系，并逐文件验证 crawler 的原始文件、新增文件和 `docs/source-adaptations.json` 中的适配哈希。适配记录只包含 crawler；删除原始文件必须显式记录 `adaptedBlob: null`，未记录改动、未经记录的缺失文件或过期记录均失败。文件核对由 `scripts/lib/source-adaptations.mjs` 执行。适配记录必须与实际 diff 一起审阅。

relation 的历史完整来源与 Ye 远端回退记录仍保留在原接入文档，本次不恢复该系统。后续同步先核定 SHA 与历史，保留 crawler 的路径、会话和运行适配。当前在 `dev` 分支维护；提交、推送、合并和部署需要对应用户授权，本轮没有执行。

2026-09-12 已按记录中的精确 SHA 补回本机缺失的 scholar archive 来源提交 `f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d`，其树为 `8c060254a4ddd2fd485c3bbe2824bdd2c9af1353`，与导入证据一致。旧远端 `Li` 分支已不存在，现有 `feature/Li` 不是该来源，不能替代。仅恢复 Git 对象，不恢复退役源码，不修改导入记录、校验逻辑、分支引用或提交历史。

新克隆或清理不可达对象后，如果再次缺失该提交，可在仓库根目录执行以下命令。`http.sslBackend=openssl` 仅对本次命令生效，用于本机 schannel 的 `SEC_E_NO_CREDENTIALS` 问题；不会修改 Git 配置。

```powershell
git -c http.sslBackend=openssl fetch --no-tags --no-recurse-submodules --no-write-fetch-head --no-auto-maintenance origin f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d
git cat-file -t f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d
git rev-parse 'f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d^{tree}'
node scripts/check-source.mjs
```

预期对象类型为 `commit`，树哈希为上述固定值，完整校验退出码为 0。该提交没有持久分支引用，普通分支拉取不保证包含它；恢复仍依赖远端保留原始对象，不能通过修改 SHA 或跳过退役记录解除失败。

## 验证与限制

本轮执行命令、结果及运行边界见 [单系统调整](single-system.md)。前端单测使用模拟请求；浏览器回归使用压缩构建和模拟后端；后端编目集成测试使用隔离 MySQL/Neo4j。它们不替代真实账号及数据验收。本次导师显示验收已更新默认运行 JAR；后续更新仍需按 README 停止、构建并启动。

2026-09-12 补回 scholar 来源后，`node scripts/check-source.mjs` 完整执行通过，包括退役来源树、subtree 来源祖先及当前 crawler 全部文件的适配哈希。修正两份旧工作台跳转用例后，路由单测 35 项、相关浏览器回归 5 项通过；路由实现和权限契约未改动。下方及其他历史验收文档中的对象缺失记录描述当时状态，当前恢复方法以上方“来源同步”为准。

## 历史本机运行与数据证据

以下章节原样保留关键历史数据与恢复材料位置；其中多系统入口、门户和 relation 的运行状态均已被本次单系统范围取代，不能作为当前启动说明。历史计数没有在本轮重新查询。

### 历史记录：2026-09-11 本机 MySQL 运行实例

在 `E:\Program\Java\course_design\XSGXZSTP-KS` 按用户选择使用 Windows MySQL80，两个项目库为 `127.0.0.1:3306/course_relation`、`course_crawler`。Neo4j 继续使用 `neo4j:5.26-community`，容器为 `course-integration-relation-neo4j-1`、`course-integration-crawler-neo4j-1`，HTTP/Bolt 端口保持 `27471/27681`、`27473/27683`；数据卷为 `course-integration_relation-neo4j`、`course-integration_crawler-neo4j`，重启策略为 `unless-stopped`。其他工作区仍按上文默认的隔离 MySQL 容器流程初始化。

此实例的辅助脚本、Compose 配置及外部应用配置位于 Git 忽略的 `.local/`。启动使用 `.\.local\Start-LocalProject.ps1`，通过交互提示取得本机数据库密码；停止使用 `.\scripts\Stop-Integration.ps1 -System all`。已创建统一管理员 `admin`，后续启动无需 `-BootstrapAdmin`。应用配置只引用进程环境变量，Neo4j 认证从经归属校验的已有容器读取，未生成明文凭据文件。详细命令见本机 `.local/README-local-runtime.md`；这些本机脚本不会随 Git 克隆迁移。

本机可选样例导入使用 `node .local/Import-LocalDemoData.mjs`，通过当前进程的 `COURSE_MYSQL_PASSWORD` 连接固定项目库。该入口加载原 `scripts/Import-IntegrationDemoData.mjs`，仅替换连接与归属校验边界，保留样例标识、事务、冲突检测、数量核对和重复导入保护。原导入器仍适用于默认容器方案，不能直接用于本实例。两套图数据仍由原 Outbox 逻辑生成。

当前 `GraphCanvas.vue` 使用 Cytoscape 多层画布，仓库 `Test-SystemsBrowser.mjs` 的单个 `canvas` 定位会产生严格模式错误。本机 `.local/Use-LocalTestCredentials.mjs` 在运行验收时将凭据来源替换为进程环境，并将像素检查定位到 `canvas[data-id="layer2-node"]`；登录、权限、连续像素、刷新与适应画布断言保持不变。原验收脚本的通用定位尚未修改；不把未适配脚本的首次失败记录为成功。

### 历史记录：2026-09-11 本机 Docker 数据盘迁移

本机 Docker Desktop 4.72.0 的 WSL 存储根目录已迁移至 `E:\docker\data`，`%APPDATA%\Docker\settings-store.json` 中的 `CustomWslDistroDir` 指向该目录。数据盘为 `E:\docker\data\disk\docker_data.vhdx`，`docker-desktop` 的系统盘为 `E:\docker\data\main\ext4.vhdx`；WSL 注册路径已通过 `wsl --manage docker-desktop --move E:\docker\data\main` 同步。

两份磁盘在首次启动前均通过 SHA256 校验。迁移前的原始数据盘完整保存在 `E:\docker\backup-20260911\docker_data.vhdx`，其哈希仍与迁移前一致；C 盘原数据盘已移除以释放空间。校验及恢复位置记录在 `E:\docker\migration-20260911.json`，这些本机文件不随 Git 克隆迁移。`E:\docker\DockerDesktopWSL` 中已有的旧盘保持原状。恢复时应先退出 Docker，确保新旧副本不会同时挂载到 WSL；相同磁盘标识会阻止 Docker 选择数据盘。

迁移后曾因 `Page expected to be: 13, but self identifies as 0` 无法启动；迁移前日志已有元数据库的 I/O 错误。现已在经校验的整盘副本上恢复快照元数据库、ext4 结构和受损镜像内容，再切换至原 E 盘运行路径。切换前全部数据卷的文件聚合 SHA256 与修复前一致，原始备份及本次切换前运行盘均保留。Docker Engine 29.4.2 已恢复，两个项目 Neo4j 容器使用原 ID 和卷且健康检查通过；认证只读查询分别返回 relation 68 个节点/262 条关系、crawler 32 个节点/90 条关系。恢复材料、验证命令及后端验收边界见 [Docker 修复与后端验收](backend-acceptance-20260911.md)。

### 历史记录：2026-09-11 作者导入与验收约定

当前 crawler 以 XLSX/XLS/CSV 作者导入、成果检索、统计和知识图谱为主，数据源、采集任务、数据治理、质量指标的页面和业务 API 已退场。历史基础表和数据保留，新版启动后停止旧采集计划。V17 增加作者导入记录及导师/成果机构关系，图类型共 13 种；知网字段映射、预览确认、原子导入、重复导入与权限约定见 [作者导入说明](author-import.md)。上文 2026-09-10 采集界面和调度说明保留为历史记录，不作为当前入口依据。

真实混合知网 XLS 的段内表头可更换顺序或省略列，解析器逐段重建映射；手工映射按列名跟随，保留原行号及各段原始字段。中文姓名逗号分隔与 DOI/URL 边界 BOM 有定向兼容，英文姓名和机构内逗号不拆。`scientific-result` 为科技成果类型；独立单人署名项目按原作者保存，不改变主学者身份或博士指导口径。导入服务仍沿用现有 MyBatis、事务和 Outbox，不增加数据库迁移、依赖或清库 API。合作边仍从共同作品派生，作者子图按作品先后保留相邻共同作者，避免大量论文挤掉合作证据；节点和关系上限保持。

后端验收采用隔离数据库。crawler 沿用 Testcontainers；relation 的 `local` 测试依赖样例数据，必须先准备独立 MySQL/Neo4j、执行现有 schema/sample SQL 并完成 Outbox 图同步，再运行 `mvnw.cmd verify`。本机复现入口为 Git 忽略的 `.local/docker-repair-20260911/run_relation_acceptance.py`；凭据只在进程环境流转，测试容器限定回环地址并在结束时按归属清理。该验收不会替换业务后端或迁移 Windows MySQL 项目库。

### 历史记录：2026-09-11 本机试用启动

用户要求启动当前版本试用后，复用既有 Demo 模式、三个前端构建和两个后端 JAR 启动。启动前通过本机安全输入窗口取得 MySQL 密码，仅用于当前进程；Git 忽略的 `.local/Start-ReviewProject.ps1` 先备份 `course_crawler`，再调用原 `Start-LocalProject.ps1`。备份和脱敏状态位于 `.local/review-start-20260911-171852/`，目录限制当前用户、管理员和 SYSTEM 访问，备份 SHA256 已复核；未重置账号或导入新的测试资料。日常启动、停止仍使用根目录 `start.bat`、`stop.bat`。

本次启动前 Docker 因失效的零字节 AF_UNIX 套接字退出。确认 Docker/WSL 已停止且目录仅含运行时套接字后，将 `%LOCALAPPDATA%\Docker\run` 和 `%LOCALAPPDATA%\docker-secrets-engine` 改名为各自的 `.before-start-20260911-171755` 备份，再启动 Docker；未修改数据盘配置。两个原图库恢复 `healthy`。今后若出现同类错误，应重新核对日志、进程和目录内容，不能直接复用目录改名命令。

实际日志与数据库查询确认 `course_crawler` 从 V16 升级至 V17；三个登记进程的 PID、创建时间、路径及命令行归属验证通过。门户、relation 健康接口、crawler readiness 与 graph 均返回 200/UP。未登录访问 `/crawler/author-import` 返回登录跳转，导入 API 返回 401，原认证边界保持。试用入口为 `http://127.0.0.1:18000/crawler/author-import`；使用原账号登录。本次启动检查未替用户上传真实知网文件，也未替代此前的隔离后端验收。

### 历史记录：2026-09-11 真实知网数据替换

随后按用户要求，将 `CNKI-20260911184927501.xls`、`CNKI-20260911184034561.xls` 导入本机 `course_crawler`，替换原 12 项演示成果及关联学术数据。备份后以现有导入服务在一个事务中清理、导入 33 张指定业务表的数据，再通过原 Outbox 重建托管图谱。保留账号、角色、配置、历史采集运行和 relation 子系统；V17 结构不变，未新增在线清库接口。

实际核对：479 条来源记录、476 项去重成果（471 篇期刊/辑刊论文、2 篇博士论文、3 项科技成果）、211 名作者、195 家成果机构。席酉民有 210 名共同署名合作作者，全库 546 对合作作者；席酉民与两篇博士论文仅建立指导关系，学生署名照常保存。庄贵军独立署名的《中国企业的营销渠道控制行为研究》未补席酉民署名。所有成果的完整作者名单及顺序均与原文件逐项核对；MySQL 与 Neo4j 的成果、作者、署名和指导数量一致，Outbox 全部完成。

更新后的后端已打包重启，前端已构建；readiness 为 `UP`，门户作者导入入口返回 200。最新状态以本段为准，上方“未导入真实文件”等描述属于之前的启动阶段。恢复材料和固定文件维护工具保留在限制访问、Git 忽略的 `.local/cnki-replacement-20260911/`，SQL 与图谱备份均已核对 SHA256，密码未落盘；不重复运行已完成的替换。43 项不同后端测试、10 项前端测试和构建通过，整仓来源检查仍有既存 retired scholar Git 对象缺失限制；本次未完成真实浏览器视觉验收。确切命令、文件范围和数据边界见 [真实文件替换验收](author-import.md#真实文件替换验收2026-09-11)。
