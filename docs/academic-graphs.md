# 三类学术图谱

本次修改对象为 `systems/crawler` 信息采集系统的知识图谱模块。图谱以一位作者为中心，展示作者、专利、论文、指导硕论和指导博论五类实体。沿用现有 Vue、Cytoscape、Spring、MySQL 和 Neo4j，不增加依赖或数据库迁移。

当前三个图谱为独立主导航模块，画布使用整行宽度，内容清单默认收起。成果目录与统计页的紧凑筛选、新增编目及本轮验收见 [研究页面调整](compact-research-ui.md)。

## 页面与交互

| 模块 | 集成入口 | 展示与操作 |
| --- | --- | --- |
| 学术关系图谱 | `/crawler/academic-relations` | 选择作者后展示合作作者及共同作品。点击作者查看已收录资料；点击“共同创作”连线或合作作者清单中的共同创作入口，查看该页作品依据，并可继续打开作品详情。独立作品不计入合作。 |
| 学术成果图谱 | `/crawler/academic-achievements` | 展示本人论文、专利及指导的硕论、博论。画布与成果清单都可以打开摘要、日期、署名、来源等详情，并进入完整目录记录。 |
| 学术背景图谱 | `/crawler/academic-background` | 成果按发表日期由早到晚排列并按年份分组，每项成果直接显示发布时间及该篇成果对应的机构；无日期记录统一放在末尾。每项成果可点击查看详情，不推断教育、任职或毕业经历。 |

作者选择、分类和分页写入 URL。三个独立主导航入口、侧栏和命令面板切换保留 `authorId`，切换模块或分类时从第一页开始。检索候选按规范作者 ID 选择，不按姓名自动合并作者。没有作者、没有成果、参数无效、查询失败都有对应提示；失败不会在新作者标题下继续展示旧作者的数据。刷新可重试，离开页面会取消图谱请求并作废迟到响应。

作者详情复用现有目录证据：姓名、作者编号、ORCID、已收录的机构署名观测。没有录入的履历不会自动补全；机构署名的日期范围不等于连续任职。完整作者/作品目录资料继续要求 `CATALOG_READ`，图谱读取仍要求 `GRAPH_READ`。

背景页逐篇显示“发布时间”和“机构”，机构与成果由作者图谱接口一次返回，沿用 `GRAPH_READ`。机构来自该篇成果的 `PRODUCED_AT`，或本篇作者/导师上标记同一 `achievementBusinessId` 的 `AFFILIATED_WITH`；仅使用托管节点和关系，不把作者其他作品的机构带入本篇，也不推断出版机构或当前任职。多机构去重后并列展示，缺机构或发表日期显示“未收录”；旧后端未返回机构字段时显示“暂未返回”，机构无名称时显示编号。分类、分页、作者切换和刷新会同步更新卡片信息，迟到响应不会覆盖当前作者。

## 实体与关系口径

| 页面实体 | 原有成果编码 | 与中心作者的关系 |
| --- | --- | --- |
| 作者 | `AUTHOR` | 中心作者或当前页共同作者 |
| 专利 | `patent` | `AUTHORED` |
| 论文 | `article`、`review`、`preprint`、`proceedings-article` | `AUTHORED` |
| 指导硕论 | `master-thesis` | `SUPERVISED` |
| 指导博论 | `doctoral-thesis` | `SUPERVISED` |

存储中的成果仍是 `Achievement`，由成果类型区分四种业务实体，避免修改已有目录、导入和 Outbox 契约。本人署名的学位论文不会被当作本人指导成果。合作必须同时存在中心作者、另一作者到同一论文或专利的真实 `AUTHORED` 边；导师与学生的指导关系不能替代共同署名。合作边只连接中心作者与其他作者，不展开其他作者之间的合作。

科技成果、书籍等不属于此次四种成果范围，既有记录继续保留在成果目录。机构、期刊和主题不作为三个学术图谱的展示节点。学术实体使用固定分类颜色；原类型样式配置继续供全局图谱和高级查询使用。

## 查询、分页与兼容

新增 `GET /api/v1/graph/authors/{authorId}`，契约见 [OpenAPI](../systems/crawler/docs/openapi.yaml)。参数为 `category`、`collaborationsOnly`、`chronological`、`page`、`size`；返回 `graph`、`page`、`size`、`totalWorks`。`category` 支持 `PAPER`、`PATENT`、`MASTER_THESIS`、`DOCTORAL_THESIS`，留空表示全部类型。

成果节点的 `properties.publicationDate` 为已收录发表日期；`properties.institutions` 为该篇成果的机构数组，每项含字符串规范 `id` 和 `name`，按机构 ID 升序、最多 100 家；空数组表示未收录。`properties.institutionsTruncated` 标记该篇机构是否超过上限，页面提示“仅显示该成果的前 100 家机构”。机构摘要只随当前页成果返回，不新增机构节点、不占用 300 节点额度，也不改变 `graph.truncated` 的合作证据含义。

按符合条件的成果去重分页，默认每页 20 项，可选 10、20、50 项；API 接受 1–50 项，页码为 0–1000000。时间线正序，其他模块默认倒序；两种排序均把空日期放在末尾，同日期按规范 ID 升序，避免静态数据分页时重复或漏项。`totalWorks` 是当前作者、关系和类型条件下的成果总数；合作作者数、合作边和共同作品依据只覆盖当前页。

每页优先完整保留中心作者和本页作品，再补充合作作者。画布最多 300 个节点，最多读取 1200 条共同署名记录，额外一条用于检测截断；密集合作超限会显示提示，可减小每页成果数。极端单篇论文的共同作者仍可能超限，作品详情与完整目录可继续核对署名。查询沿用 3 秒事务超时、图谱重建门禁和不可用错误，只有标记为托管的数据参与查询。Neo4j 仍由现有 Outbox 异步同步，刚导入的成果可能需要稍后刷新。

`/graph` 默认进入学术关系图谱；原 `centerType=AUTHOR&centerId=...` 链接转换为作者入口，作品等其他中心链接保留高级查询行为。作者导入和工作台的作者入口改为学术成果图谱，便于直接查看包含硕博指导的导入结果。

原全局概览移到 `/graph/overview`。全局概览、高级查询、路径分析、保存的查询与类型样式从新页面的“图谱工具”进入，功能保留。`/graph/entities` 仍是节点样式别名；`/graph/relations`、`/graph/achievements`、`/graph/background` 保留为新独立路径的兼容跳转，原有查询参数继续传递；关系样式的正式地址为 `/graph/settings/edges`。

## 首轮图谱实施与验证记录

实施前读取 `docs/development.md`、`docs/integration-baseline.md` 和 `docs/author-import.md`，核对源码、路由、投影关系及工作区未提交改动。保留既有作者导入解析、业务数据替换和图谱查询排序改动。原记忆中的全局概览主入口、作者两跳入口、关系样式别名与新需求不再一致，现按实际路由和查询同步。

后端测试使用新建的独立 MySQL、Neo4j Testcontainers，浏览器使用合成作者与成果数据。功能实施阶段未重启业务服务，前端构建到仓库 `.local/academic-graph-review/` 下，当时未覆盖运行中的 `dist`；之后的真实实例启用单独记录在下方“本机试用启动”。

以下命令实际执行，工作目录特别标注；重复执行不重复计算测试数量。

| 工作目录 | 命令 | 观察结果 |
| --- | --- | --- |
| `systems/crawler` | `.\mvnw.cmd -o -f backend/pom.xml '-DargLine=-Xmx512m -XX:MaxMetaspaceSize=256m' '-Dtest=AuthorGraphIntegrationTests,GraphQueryIntegrationTests,OpenApiDocumentTests' test -q` | 20 项通过：8 项新作者图谱测试、11 项原图查询测试、1 项契约测试。覆盖分类、指导分离、真实合作、日期排序、315 项成果完整分页、密集合作截断、空态、非法参数及权限。 |
| `systems/crawler` | `.\mvnw.cmd -o -f backend/pom.xml '-DargLine=-Xmx512m -XX:MaxMetaspaceSize=256m' '-Dtest=GraphPresentationServiceTests,OpenApiDocumentTests' test -q` | 4 项通过，含 3 项原合作边派生测试；与上一行合计 23 项不同后端用例通过。 |
| `systems/crawler/frontend` | `npm.cmd test -- src/utils/academic-graph.test.ts src/composables/useAuthorGraph.test.ts src/router/index.test.ts src/components/business/AppSidebar.test.ts src/components/business/GraphCanvas.test.ts src/services/author-import.test.ts` | 50 项通过，覆盖实体分类、时间线、响应校验、请求竞态、错误重试、路由权限、导航及画布。 |
| `systems/crawler/frontend` | `npm.cmd run test:e2e -- e2e/academic-graph.spec.ts e2e/author-import.spec.ts e2e/graph-overview.spec.ts e2e/graph-types.spec.ts --output=../../../.local/academic-graph-review/browser-final` | 首轮 26 项中 24 项通过，桌面连线像素定位及旧导航测试失败。 |
| `systems/crawler/frontend` | `npm.cmd run test:e2e -- e2e/academic-graph.spec.ts e2e/graph-overview.spec.ts --output=../../../.local/academic-graph-review/browser-verified` | 调整画布布局、连线可见性、测试点击点及新导航路径后，12 项全部通过；合并上一轮已通过且未再修改的测试，共 26 项不同浏览器用例通过。包括 1440/390 像素新模块、Canvas 作品及合作边点击、作者/硕博详情、类型筛选、分页、刷新、错误重试和旧工具回归。 |
| `systems/crawler/frontend` | `npm.cmd run build -- --outDir ../../../.local/academic-graph-review/frontend-dist-final` | TypeScript 检查及生产构建通过。仍有既有大于 500 kB 的包体积提示，构建未失败。 |
| `systems/crawler/frontend` | `npm.cmd run test:e2e -- --config ../../../.local/academic-graph-review/playwright-production.config.ts e2e/academic-graph.spec.ts --output=../../../.local/academic-graph-review/browser-production` | 使用隔离生产构建再次运行 3 项新模块测试，全部通过；覆盖真实画布点击合作作者、切换中心作者、返回原作者及作品/合作边详情。临时配置只替换测试服务器为隔离产物预览，首次 CommonJS 环境不支持 `import.meta` 的配置错误已修正。 |
| 仓库根目录 | `node .local/academic-graph-review/sync-adaptations.mjs` | 更新本轮 crawler 适配记录，并复用仓库校验函数核对 relation、crawler 全部有效来源及适配，均通过。 |
| 仓库根目录 | `node scripts/check-source.mjs` | relation、crawler 的来源祖先、文件和适配均通过；命令最终退出 1，原因仍是已退役 scholar 的历史 Git 对象 `f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d^{tree}` 缺失，与修改前记忆记录一致。未跳过历史校验或改写来源记录。 |
| 仓库根目录 | `git diff --check` | 通过，无新增空白错误；保留仓库原有换行约定。 |

首轮构建发现新增路由回调缺少类型，已补充。后端首次测试有一项 JSONPath 对数组计数写法不正确，已改为数组长度断言；前端首次状态测试使用了普通异常，已改为实际 API 的 `ApiError` 契约，未改变错误隐藏策略。浏览器首次筛选定位被 Element Plus 占位层遮挡，改用键盘操作验证；连线点击从真实着色区域中心取点，避免落到作者端点的命中范围。受限进程中的 `spawn EPERM` 及 Docker pipe 权限问题已通过正常本机测试权限解决，没有安装或更换依赖。

截图在 `.local/academic-graph-review/browser-verified/` 中，已查看桌面合作图、成果图、时间线和手机合作图；生产构建截图另存于 `browser-production/` 并再次查看桌面合作图，内容均为测试数据。其他历史失败及中间构建保留在 Git 忽略的验收目录，不进入业务代码。

## 首轮图谱变更文件与记忆同步

以任务开始时的 SHA256 快照为基准，本轮新增 11 个、修改 22 个文件，共 33 个。开始时已有 13 个修改文件，其中与本任务无重叠的 8 个文件重新计算 SHA256 后完全一致；其余 5 个文件仅增加图谱能力、同步相应文档或更新本轮适配条目，原导入逻辑、测试和旧图查询的共同作品排序仍保留。`.local/academic-graph-review/changed-files.json` 记录完整路径；没有修改独立的 relation 子系统、依赖清单或迁移文件。

| 文件 | 本轮变更 |
| --- | --- |
| `README.md` | 更新 crawler 知识图谱入口说明 |
| `docs/academic-graphs.md`（新增） | 三类图谱用法、数据口径、验证命令、修改清单和启用步骤 |
| `docs/author-import.md` | 作者导入后的图谱入口改为学术成果，并链接新的展示口径 |
| `docs/development.md` | 同步三类图谱架构、路由和验证边界，限定旧别名说明的适用阶段 |
| `docs/integration-baseline.md` | 同步当前模块与尚未启用的运行边界，修正旧别名描述 |
| `docs/source-adaptations.json` | 更新本轮 crawler 源码、测试和文档的适配哈希 |
| `systems/crawler/README.md` | 更新模块能力和旧图谱工具位置 |
| `systems/crawler/docs/openapi.yaml` | 增加作者图谱的分页、类型、排序及错误响应契约 |
| `systems/crawler/backend/src/main/java/com/aacv/system/graph/api/GraphController.java` | 增加作者图谱入口及参数验证 |
| `systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphQueryService.java` | 按作者与成果类型分页查询，区分署名及指导，限制合作证据数量 |
| `systems/crawler/backend/src/main/java/com/aacv/system/graph/application/GraphPresentationService.java` | 只派生中心作者与其他作者的合作边 |
| `systems/crawler/backend/src/main/java/com/aacv/system/graph/domain/AuthorGraphView.java`（新增） | 作者分页响应与四类成果枚举 |
| `systems/crawler/backend/src/test/java/com/aacv/system/graph/AuthorGraphIntegrationTests.java`（新增） | 独立 MySQL、Neo4j 下的作者图谱行为和权限测试 |
| `systems/crawler/backend/src/test/java/com/aacv/system/OpenApiDocumentTests.java` | 校验新接口文档入口 |
| `systems/crawler/frontend/src/views/AcademicGraphView.vue`（新增） | 三种图谱页面及选择作者、分类、分页、清单和详情交互 |
| `systems/crawler/frontend/src/components/business/AcademicNodeDetail.vue`（新增） | 作者资料、作品预览和合作作者切换 |
| `systems/crawler/frontend/src/services/academic-graph.ts`（新增） | 作者图谱请求类型和 API 调用 |
| `systems/crawler/frontend/src/composables/useAuthorGraph.ts`（新增） | 请求取消、迟到响应丢弃及错误重试 |
| `systems/crawler/frontend/src/utils/academic-graph.ts`（新增） | 实体分类、时间线、画布样式、合作网络布局及响应校验 |
| `systems/crawler/frontend/src/utils/academic-graph.test.ts`（新增） | 分类、日期、响应一致性和画布转换测试 |
| `systems/crawler/frontend/src/composables/useAuthorGraph.test.ts`（新增） | 切换、取消、错误及重试测试 |
| `systems/crawler/frontend/src/router/index.ts` | 三模块路由、共享工作区和旧链接兼容 |
| `systems/crawler/frontend/src/router/index.test.ts` | 三模块路由权限及兼容跳转测试 |
| `systems/crawler/frontend/src/config/nav.ts` | 知识图谱主导航拆分为三个模块 |
| `systems/crawler/frontend/src/components/business/AppSidebar.vue` | 侧栏切换保留作者编号 |
| `systems/crawler/frontend/src/components/business/AppSidebar.test.ts` | 校验三模块导航结构 |
| `systems/crawler/frontend/src/components/business/ModuleNavigation.vue` | 页签切换保留作者编号 |
| `systems/crawler/frontend/src/components/business/AchievementPreview.vue` | 详情类型显示为现有中文标签 |
| `systems/crawler/frontend/src/services/author-import.ts` | 导入及工作台作者入口指向学术成果图谱 |
| `systems/crawler/frontend/e2e/academic-graph.spec.ts`（新增） | 桌面、手机三模块真实画布交互及错误场景 |
| `systems/crawler/frontend/e2e/author-import.spec.ts` | 导入结果跳转到新的成果图谱 |
| `systems/crawler/frontend/e2e/graph-overview.spec.ts` | 保留旧全局图谱回归并适配新的辅助工具入口 |
| `systems/crawler/frontend/e2e/graph-types.spec.ts` | 适配三模块导航和类型样式正式地址 |

已重新核对两份指定项目记忆与最终路由、服务和契约，更新旧主入口、作者两跳入口及关系样式别名，不改变历史验收数据。本轮没有更新用户全局记忆或创建新的项目记忆体系。

## 在现有本机实例启用

下列命令用于后续重新构建、启用 crawler，需要允许其短暂中断，保留数据库和 relation 服务。2026-09-11 已按用户要求完成一次启用，实际结果见下方记录，无需为了本次试用再次执行。

```powershell
Set-Location E:\Program\Java\course_design\XSGXZSTP-KS
.\scripts\Stop-Integration.ps1 -System crawler
npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/
if ($LASTEXITCODE -ne 0) { throw '前端构建失败，请先处理错误' }
Push-Location systems/crawler
try {
    .\mvnw.cmd -o -f backend/pom.xml -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw '后端打包失败，请先处理错误' }
} finally { Pop-Location }
.\.local\Start-LocalProject.ps1 -System crawler
```

沿用本机安全输入流程，不把凭据写入命令或文档。门户保持运行时仍使用原模式恢复 crawler。当前独立页面位于 `/crawler/academic-relations`、`/crawler/academic-achievements`、`/crawler/academic-background`；选择作者后即可浏览。

## 本机试用启动（2026-09-11）

用户要求启动新模块供其试用。操作前核对指定记忆、当前未提交修改、MySQL80、两个 Neo4j 容器及三个应用进程；数据库与应用原本均正常。只停止旧 crawler，保留门户和 relation，重新构建前端至正式 `dist` 并离线打包后端，再通过已有 `.local/Start-LocalProject.ps1 -System crawler` 启动。MySQL 密码由用户在本机 PowerShell 窗口不回显输入，未写入聊天、脚本、命令参数或文档，也未进行管理员引导。

| 实际命令或检查 | 结果 |
| --- | --- |
| 仓库根目录执行 `.\scripts\Stop-Integration.ps1 -System crawler` | 原 crawler 通过进程归属验证后停止，其他组件保持运行 |
| 仓库根目录执行 `npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/` | TypeScript 与生产构建通过，新产物包含三类学术图谱；仍有既有大分块提示 |
| `systems/crawler` 执行 `.\mvnw.cmd -o -f backend/pom.xml -DskipTests package` | `BUILD SUCCESS`，新 JAR 包含作者图谱响应和控制器；这是打包，不重复计为测试通过 |
| 本机交互窗口执行 `.\.local\Start-LocalProject.ps1 -System crawler` | 新 crawler 启动；运行日志确认端口 `18083`、上下文 `/crawler` |
| `Invoke-RestMethod -Uri 'http://127.0.0.1:18083/crawler/actuator/health/readiness' -TimeoutSec 5` | `UP` |
| `Invoke-RestMethod -Uri 'http://127.0.0.1:18083/crawler/actuator/health' -TimeoutSec 5` | `UP` |
| `Invoke-RestMethod -Uri 'http://127.0.0.1:18083/crawler/actuator/health/graph' -TimeoutSec 10` | `UP` |
| 检查 Flyway 启动日志 | 校验 17 个迁移，当前 schema 版本为 17，`No migration necessary` |
| 分别请求 `/crawler/graph/relations`、`/crawler/graph/achievements`、`/crawler/graph/background` | 未登录时按原规则跳转统一登录，页面返回 200；不将该结果算作登录后图谱数据验收 |
| 检查原门户与 relation 进程 | 两个 PID 均与操作前一致，保持运行 |

真实入口 `http://127.0.0.1:18000/crawler/graph/relations` 已在浏览器打开并显示统一登录页。匿名静态资源核对也返回登录页，故未宣称完成认证后的资源哈希核验或实际作者图谱交互；这些交互留待用户登录试用，前一阶段的隔离浏览器测试结果保持独立。

旧 JAR、旧前端产物、974 个文件的操作前 SHA256、构建日志与脱敏状态记录保存在 Git 忽略目录 `.local/academic-graph-startup-20260911/`，方便核对和恢复。本轮只更新本文、`development.md`、`integration-baseline.md` 的运行状态，不修改业务源码、依赖、迁移或业务数据；原有未提交源码修改保留。此次启动确认原文“尚未启用”已过时，现根据健康端点、进程记录和 Flyway 日志同步修正。
