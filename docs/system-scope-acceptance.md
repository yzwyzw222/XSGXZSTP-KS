# 两系统保留与信息采集名称调整验收

核对日期：2026-09-10。本文记录本次删除与更名的实际范围和验证，不替代仓库指定的项目记忆。

## 任务总结

仅保留学术关系知识图谱构建平台（relation）和学术成果信息采集及可视化系统（crawler）。删除 extraction、scholar 的有效工程、入口配置、专属资源和集成脚本分支。门户登录介绍、卡片、搜索、帮助、日志筛选与验收脚本同步为两个系统。

信息采集系统保留 `crawler` 内部标识、`/crawler/` 路径、数据库和端口；名称变更不涉及业务 API、权限、数据库迁移或依赖调整。删除系统的旧页面、API、静态资源和健康路径不再代理，返回 404；登录和工作区回跳拒绝这些路径。

删除前将两个完整目录归档到本机 Git 忽略的 `.local/subsystem-removal-20260910-122957/`，并保留任务开始时已有改动的备份。255 个被删除源码文件均与归档逐个比对 SHA-256，一致。此前的门户管理与采集诊断改动继续保留，本次未创建 Git 提交、分支或推送。

## 验证结果

以下命令均在仓库根目录执行：

| 命令 | 实际结果 |
| --- | --- |
| `npm.cmd --prefix portal test` | 18 项通过；覆盖两个有效系统、非法配置、被删除系统的路由与代理拦截、回跳、日志筛选、认证及错误处理。 |
| `.\scripts\Test-IntegrationInitialization.ps1` | 通过；仅生成两个 MySQL、两个 Neo4j 和四个数据卷声明，不生成删除系统配置，重复初始化保留已有配置与凭据。使用模拟 Docker，未初始化真实数据库。 |
| `npm.cmd --prefix portal run build` | 通过；仅生成两个系统的维护页，旧专属图片未进入构建产物。最后文案调整后重新构建并复验。 |
| `npm.cmd --prefix systems/relation/frontend run build -- --base=/relation/` | 通过；保留原有大 chunk 提示。 |
| `npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/` | 通过；包含管理页与类型检查，最后导航名称调整后重新构建，保留原有大 chunk 提示。 |
| `npm.cmd --prefix portal run check:source` | 通过；保留系统的原始来源、祖先关系、全部文件及适配哈希匹配；删除系统的历史导入记录明确标记 retired。 |
| `node --check scripts/Import-IntegrationDemoData.mjs` | 语法检查通过。 |
| `node scripts/Test-IntegratedSystems.mjs` | 两个系统的真实业务读接口、共享身份、CSRF 拒绝、未知 API 和统一退出后的旧 Cookie 重放拦截通过。 |
| `node scripts/Test-PortalBrowser.mjs systems/crawler/frontend/node_modules/playwright/index.mjs` | 桌面、390px 窄屏、两个入口和返回、信息采集搜索、帮助、配置错误及重试通过；最终构建后再次通过。 |
| `node scripts/Test-SystemsBrowser.mjs` | 两个系统各两个业务页面、深链接刷新、会话保持与返回门户通过。 |
| `node scripts/Test-UnifiedLoginBrowser.mjs` | 1440/390/320px、错误密码、密码显示、深链接回跳、两个系统退出及外站回跳拦截通过。 |
| `node scripts/Test-PlatformBrowser.mjs` | 两系统顶部返回、持续全屏、统一用户管理、平台日志和后台审计通过；采集失败展示使用浏览器夹具，未发起真实采集。 |
| `node scripts/Import-IntegrationDemoData.mjs --check` | 两个保留系统的既有样例数据只读核对通过；relation 30 篇论文、crawler 12 项成果；没有执行导入。 |
| `git -c core.safecrlf=false diff --check` | 通过，未发现空白差异错误。 |

实际本机门户的 `/integration.json` 返回 relation、crawler 两项；对 extraction、scholar 的根路径、页面、API、资源和健康路径逐个发出 HTTP 请求，均返回 404。

另使用 PowerShell `System.Management.Automation.Language.Parser.ParseFile` 逐个解析 `scripts/*.ps1`，未发现语法错误。核对最终系统目录只包含 relation、crawler，修改文本均为 UTF-8 无 BOM；有效前端源码不再包含“爬虫”名称。浏览器截图保存在 `.local/browser/`、`.local/platform-browser/` 和 `.local/unified-login/screenshots/`，构建日志保存在 `.local/subsystem-removal-*-build.log`。

首轮受限执行时，Node 测试报 `spawn EPERM`，初始化测试的 `Set-Acl` 报 `Attempted to perform an unauthorized operation.`；这是本机工具权限限制。使用获准的本机执行权限后，原命令全部通过，没有修改测试隔离或安全逻辑。停止删除系统时也先遇到进程访问限制，随后经相同的进程身份校验成功停止。

## 项目记忆

实施前读取 `docs/integration-baseline.md` 和 `docs/development.md`，并核对 README、统一登录说明、平台管理记录、Git 差异、配置、脚本和源码。原四系统记忆与修改前代码一致；按当前删除要求同步两份指定记忆的系统范围、名称、端口兼容、初始化范围、登录回跳、日志筛选、来源校验和保留数据约定。移除当前开发说明中删除系统的恢复操作，历史验收明确标记对应阶段。没有创建新的记忆体系。

## 风险、限制与假设

- 删除的是有效工程和集成入口。为保留既有数据，原数据库容器、数据卷、凭据及历史日志未删除；本机源码归档也仍保留。
- 本次使用已有 relation、crawler 后端完成真实接口验证，没有重新打包后端或运行后端全量单元测试；保留后端的本次源码修改仅涉及三个中文注释。
- 前端构建有既有的大 chunk 提示。Development HMR、真实外部信息采集、模型调用、Nginx 本体和公网部署未在本次验收。
- 当前本机门户已重新加载新配置，relation 和 crawler 后端继续运行，删除系统的后端已停止。

## 用户需执行的操作

刷新 `http://127.0.0.1:18000/` 即可使用两个保留系统。无需重新初始化数据库、迁移账号或执行额外安装。

## 变更文件

以下清单相对本次任务开始时的工作区生成；不把此前已有且本次未修改的文件列作本次变更。被删除系统的目录已移至本机忽略归档，因此在有效源码中表现为删除。

<!-- task-files -->

共新增 2 个文件、修改 50 个文件、删除 258 个文件。

| 文件 | 操作 | 说明 |
| --- | --- | --- |
| `README.md` | 修改 | 更新项目功能、目录、启动数量、端口和信息采集名称。 |
| `deploy/systems.json` | 修改 | 仅保留 relation、crawler，更新采集系统名称。 |
| `design-qa.md` | 修改 | 注明历史设计或验收范围，与当前两系统状态区分。 |
| `docs/crawler-acceptance.md` | 修改 | 注明历史设计或验收范围，与当前两系统状态区分。 |
| `docs/development.md` | 修改 | 同步两系统的开发、初始化、登录、日志和来源约定。 |
| `docs/extraction-acceptance.md` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `docs/import-records.json` | 修改 | 将两个删除系统标记 retired，保留原始来源证据。 |
| `docs/integration-acceptance.md` | 修改 | 注明历史设计或验收范围，与当前两系统状态区分。 |
| `docs/integration-baseline.md` | 修改 | 同步当前系统范围与已验证状态，保留来源和管理诊断知识。 |
| `docs/integration-plan.md` | 修改 | 注明历史设计或验收范围，与当前两系统状态区分。 |
| `docs/local-runtime-acceptance.md` | 修改 | 注明历史设计或验收范围，与当前两系统状态区分。 |
| `docs/platform-management.md` | 修改 | 注明历史设计或验收范围，与当前两系统状态区分。 |
| `docs/relation-acceptance.md` | 修改 | 注明历史设计或验收范围，与当前两系统状态区分。 |
| `docs/scholar-acceptance.md` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `docs/source-adaptations.json` | 修改 | 删除退役系统适配条目并同步保留系统本次修改的哈希。 |
| `docs/system-scope-acceptance.md` | 新增 | 记录本次范围、实际验收、限制和完整变更清单。 |
| `docs/unified-login.md` | 修改 | 更新两系统认证契约和权限映射，标明历史验收范围。 |
| `portal/index.html` | 修改 | 移除无脚本说明中的删除系统入口。 |
| `portal/public/assets/portal/README.md` | 修改 | 移除专属插图说明并更新信息采集插图名称。 |
| `portal/public/assets/portal/images/extraction-nlp.webp` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `portal/src/App.vue` | 修改 | 更新两系统帮助、信息采集介绍及搜索提示，删除专属展示。 |
| `portal/src/components/PortalLogin.vue` | 修改 | 登录介绍只保留学术关系和信息采集两类入口。 |
| `portal/src/data/overview.js` | 修改 | 删除两个系统的卡片信息，将采集卡片编号改为 02。 |
| `portal/src/services/auth.js` | 修改 | 回跳与工作区白名单只保留两个业务系统和原管理页。 |
| `portal/src/services/systems.js` | 修改 | 门户响应校验限定为两个有效系统。 |
| `portal/src/style.css` | 修改 | 清除删除系统的专属卡片及标签样式。 |
| `scripts/Build-Integration.ps1` | 修改 | 移除两个删除系统的构建目标。 |
| `scripts/Import-IntegrationDemoData.mjs` | 修改 | 删除退役系统样例生成与导入分支。 |
| `scripts/Initialize-Integration.ps1` | 修改 | 初始化仅生成两个系统的 MySQL、Neo4j 和运行配置。 |
| `scripts/Start-Integration.ps1` | 修改 | 可启动目标仅保留门户及两个系统。 |
| `scripts/Stop-Integration.ps1` | 修改 | 可停止目标仅保留门户及两个系统。 |
| `scripts/Test-IntegratedSystems.mjs` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/Test-IntegrationInitialization.ps1` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/Test-PlatformBrowser.mjs` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/Test-PortalBrowser.mjs` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/Test-SystemsBrowser.mjs` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/Test-UnifiedLoginBrowser.mjs` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/check-source.mjs` | 修改 | 校验有效来源列表与历史退役状态，保留有效系统逐文件验证。 |
| `scripts/lib/config.mjs` | 修改 | 配置、构建路径和启动计划限定为两个系统。 |
| `scripts/lib/platform-audit.mjs` | 修改 | 平台请求分类与筛选限定为门户及两个有效系统。 |
| `scripts/tests/auth.test.mjs` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/tests/config.test.mjs` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/tests/gateway.test.mjs` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/tests/platform.test.mjs` | 修改 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `scripts/tests/systems.test.mjs` | 新增 | 适配两个有效系统的验收范围，保留适用检查并验证删除后的边界。 |
| `systems/crawler/README.md` | 修改 | 将界面、无障碍标签或说明中的系统名称改为信息采集。 |
| `systems/crawler/frontend/src/components/business/AppSidebar.vue` | 修改 | 将界面、无障碍标签或说明中的系统名称改为信息采集。 |
| `systems/crawler/frontend/src/components/business/AppTopbar.vue` | 修改 | 将界面、无障碍标签或说明中的系统名称改为信息采集。 |
| `systems/crawler/frontend/src/config/nav.ts` | 修改 | 采集任务的英文说明改为 Collection，并支持对应搜索词。 |
| `systems/crawler/frontend/src/management/PlatformLogsView.vue` | 修改 | 日志筛选移除删除系统并更新系统数量说明。 |
| `systems/crawler/frontend/src/views/LoginView.vue` | 修改 | 将界面、无障碍标签或说明中的系统名称改为信息采集。 |
| `systems/extraction/.gitattributes` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/.gitignore` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/.mvn/wrapper/maven-wrapper.properties` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/11` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/.gitignore` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/index.html` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/package-lock.json` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/package.json` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/App.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/api/author.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/api/client.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/api/extraction.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/api/graph.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/api/paper.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/author/AuthorCard.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/author/AuthorList.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/charts/CitationChart.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/charts/YearDistributionChart.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/common/LoadingOverlay.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/common/Pagination.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/common/SearchBar.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/graph/CytoscapeGraph.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/graph/GraphLegend.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/graph/GraphToolbar.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/paper/PaperCard.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/components/paper/PaperList.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/composables/useAuth.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/composables/usePagination.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/layout/AppLayout.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/layout/Sidebar.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/layout/Topbar.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/main.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/router/index.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/services/portal-auth.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/styles/_variables.scss` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/styles/global.scss` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/utils/formatters.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/utils/graphStyle.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/views/AuthorDetailView.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/views/AuthorGraphView.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/views/AuthorSearchView.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/views/ExtractionView.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/views/LoginView.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/views/PaperDetailView.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/views/PaperSearchView.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/src/views/StatisticsView.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/frontend/vite.config.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/mvnw` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/mvnw.cmd` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/pom.xml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/AcademicEntityExtractKgConstructionApplication.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/controller/AuthorController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/controller/ExtractionController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/controller/GraphController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/controller/PaperController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/controller/SessionController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/request/ExtractionTriggerRequest.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/request/LoginRequest.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/response/AuthorDetailDto.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/response/AuthorSummaryDto.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/response/ExtractionStatusDto.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/response/GraphDataDto.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/response/PageResponse.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/response/PaperDetailDto.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/response/PaperSummaryDto.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/dto/response/ProblemDetail.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/mapper/AuthorMapper.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/mapper/GraphMapper.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/mapper/PaperMapper.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/application/service/AuthorService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/application/service/ExtractionPipelineService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/application/service/PaperService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/Author.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/EntityRelationship.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/ExtractedEntity.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/OutboxEvent.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/Paper.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/ResearchTopic.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/Venue.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/enums/EntityType.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/enums/ExtractionStatus.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/enums/OutboxEventType.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/enums/RelationshipType.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/model/enums/VenueType.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/repository/AuthorRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/repository/EntityRelationshipRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/repository/ExtractedEntityRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/repository/OutboxEventRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/repository/PaperRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/repository/ResearchTopicRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/domain/repository/VenueRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/config/SecurityConfig.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/exception/AuthorNotFoundException.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/exception/ExternalApiException.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/exception/ExtractionException.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/exception/GlobalExceptionHandler.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/exception/GraphUnavailableException.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/exception/PaperNotFoundException.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/external/llm/LlmProperties.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/external/llm/OpenAiCompatibleClient.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/external/llm/prompt/EntityExtractionPromptBuilder.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/external/semantic/SemanticScholarClient.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/external/semantic/SemanticScholarProperties.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/integration/IntegrationHealthController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/integration/PortalAuthenticationFilter.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/sync/OutboxPollingScheduler.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/resources/application.yaml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/main/resources/db/migration/V001__initial_schema.sql` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/test/java/com/example/academic_entity_extract_kg_construction/AcademicEntityExtractKgConstructionApplicationTests.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/test/java/com/example/academic_entity_extract_kg_construction/api/controller/GraphControllerTest.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/test/java/com/example/academic_entity_extract_kg_construction/application/service/PaperServiceTest.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/test/java/com/example/academic_entity_extract_kg_construction/infrastructure/integration/PortalAuthenticationFilterTest.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/extraction/src/test/java/com/example/academic_entity_extract_kg_construction/infrastructure/sync/OutboxPollingSchedulerTest.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/relation/README.md` | 修改 | 移除删除系统的说明条目，并更新信息采集名称。 |
| `systems/relation/backend/src/main/java/com/xsyu/academicgraph/api/admin/DataImportDtos.java` | 修改 | 仅将中文注释中的“爬虫”改为“信息采集”。 |
| `systems/relation/backend/src/main/java/com/xsyu/academicgraph/application/admin/DataImportService.java` | 修改 | 仅将中文注释中的“爬虫”改为“信息采集”。 |
| `systems/relation/backend/src/main/java/com/xsyu/academicgraph/infrastructure/openalex/OpenAlexClientException.java` | 修改 | 仅将中文注释中的“爬虫”改为“信息采集”。 |
| `systems/scholar/.gitignore` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/.idea/.gitignore` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/.idea/encodings.xml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/.idea/misc.xml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/.idea/webContexts.xml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/.mvn/wrapper/maven-wrapper.jar` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/.mvn/wrapper/maven-wrapper.properties` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/mvnw` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/mvnw.cmd` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/pom.xml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/src/main/java/com/example/web/HelloServlet.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/src/main/java/com/example/web/Shiyan2/Message.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/src/main/webapp/Shiyan2/login.jsp` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/src/main/webapp/Shiyan2/loginCheck.jsp` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/src/main/webapp/Shiyan2/msgBoard.jsp` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/src/main/webapp/Shiyan2/showMessages.jsp` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/src/main/webapp/WEB-INF/web.xml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/src/main/webapp/index.jsp` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/.trae/skills/aacv-backend-api-spec/SKILL.md` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/.trae/skills/aacv-backend-api-spec/agents/openai.yaml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/.trae/skills/aacv-backend-api-spec/references/backend-contract.md` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/.trae/skills/aacv-frontend-style-spec/SKILL.md` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/.trae/skills/aacv-frontend-style-spec/agents/openai.yaml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/.trae/skills/aacv-frontend-style-spec/references/frontend-style.md` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/CNKI-20260907205631135.xls` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/.mvn/wrapper/maven-wrapper.jar` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/.mvn/wrapper/maven-wrapper.properties` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/docs/authorization-matrix.md` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/docs/openapi.yaml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/docs/system-design.md` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/mvnw.cmd` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/package.json` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/pom.xml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/AacvApplication.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/aiselection/AiSelectionController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/aiselection/AiSelectionDtos.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/analytics/AnalyticsController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/analytics/AnalyticsDtos.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/auth/AuthController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/auth/LoginRequest.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/auth/UserResponse.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/author/AuthorDtos.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/author/AuthorSearchController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/common/ConflictException.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/common/GlobalExceptionHandler.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/common/PageResponse.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/common/ProblemResponse.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/common/ResourceNotFoundException.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/graph/GraphResponse.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/graph/GraphStatsResponse.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/graph/ScholarGraphController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/institution/InstitutionDtos.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/institution/InstitutionSearchController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/keyword/KeywordController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/keyword/KeywordDtos.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/paper/PaperDtos.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/paper/PaperSearchController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/scholar/ScholarController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/api/scholar/ScholarDtos.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/aiselection/AiSelectionService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/analytics/AnalyticsService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/auth/AuthService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/author/AuthorSearchService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/common/PaperRowMapper.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/graph/ScholarlyGraphService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/institution/InstitutionSearchService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/keyword/KeywordService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/paper/PaperSearchService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/application/scholar/ScholarProfileService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/config/DataInitializer.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/config/IntegrationAdminBootstrap.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/config/IntegrationHealthController.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/config/SecurityConfig.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/config/WebConfig.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/common/BaseEntity.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/Author.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/AuthorRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/Authorship.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/AuthorshipRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/Institution.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/InstitutionRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/Paper.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/PaperReference.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/PaperReferenceRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/PaperRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/PaperTopic.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/PaperTopicRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/ScholarEntity.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/Topic.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/TopicRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/Venue.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/scholar/VenueRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/user/Role.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/user/User.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/domain/user/UserRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/infrastructure/integration/PortalAuthenticationFilter.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/infrastructure/persistence/user/SpringDataUserRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/infrastructure/persistence/user/UserJpaRepository.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/infrastructure/security/AacvUserDetailsService.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/infrastructure/security/AuthorizationPolicy.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/infrastructure/security/SessionUser.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/main/resources/application.yml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/test/java/com/aacv/AacvApplicationTests.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/test/java/com/aacv/config/IntegrationAdminBootstrapTest.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/test/java/com/aacv/infrastructure/integration/PortalAuthenticationFilterTest.java` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/backend/src/test/resources/application-test.yml` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/index.html` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/package-lock.json` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/package.json` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/scripts/import-cnki.mjs` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/App.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/aiSelection.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/analytics.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/auth.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/author.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/graph.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/institution.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/keyword.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/paper.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/request.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/api/scholar.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/components/ScholarSearch.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/components/layout/AppLayout.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/components/layout/AppSidebar.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/components/layout/AppTopbar.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/composables/useChart.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/composables/useSession.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/main.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/router/index.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/services/portal-auth.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/style.css` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/AiSelection.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/Analytics.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/AuthorSearch.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/Dashboard.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/InstitutionSearch.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/KeywordSearch.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/Login.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/PaperSearch.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/ScholarGraph.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/src/views/ScholarProfile.vue` | 删除 | 删除已移除子系统的文件或专属资源。 |
| `systems/scholar/web/vite.config.js` | 删除 | 删除已移除子系统的文件或专属资源。 |
