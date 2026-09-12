# 平台管理、全屏与采集启动诊断

> 历史记录：本文的门户、多系统入口与相关测试脚本已在 2026-09-12 单系统调整中退役。当前范围、入口及验证以 [单系统调整记录](single-system.md) 为准。

> 历史范围说明：本文保留对应阶段的设计或验收记录。2026-09-10 已删除 extraction、scholar，当前仅保留 relation、crawler；原四系统描述、已删除文件和历史命令不代表当前运行范围。当前配置与说明见 `deploy/systems.json`、`docs/development.md`。

核对日期：2026-09-10。此文是本次实现与验收记录，指定项目记忆仍是 `integration-baseline.md` 与 `development.md`。

## 实现结果

- 统一门户顶部增加“用户管理”“日志管理”“全屏”。用户管理复用现有统一账号库以及新增、编辑、启停、角色分配、密码重置和乐观锁能力；既有后端权限及 CSRF 校验保持。
- 管理页面位于 `/management/users`、`/management/users/overview`、`/management/logs`、`/management/audits`。前两项受 `USER_LIST` 保护，后两项受 `AUDIT_READ` 保护。管理页由 crawler 前端的独立 `management.html` 产物装配，不显示 crawler 的业务标题和导航。
- 集成模式移除 crawler 日志与用户管理菜单、路由及大屏近期日志区域，移除 relation 的权限管理入口。原地址跳转门户管理，不产生两套账号管理。来源系统独立运行时保留原管理兼容行为。
- 四系统返回统一门户的链接移至各自顶部左侧，右下角入口删除。门户以同源 iframe 承载业务页面，子系统全屏按钮删除，整个项目的全屏由顶层文档控制。返回门户会卸载子页面，释放其轮询、图表及请求。
- 子系统内部路由同步至门户的 `workspace` 参数，刷新恢复当前业务页面；消息必须来自当前 iframe、同一来源，路径必须通过白名单。浏览器整页刷新、关闭页面会结束原生全屏；Esc 可随时退出。

## 日志范围

平台日志记录经统一网关的门户登录/退出以及四系统 `/api/v1` 请求。统一用户管理的请求归属“统一门户”。界面可按系统、用户、时间、结果筛选并分页；管理日志查询自身和身份轮询不进入请求日志，避免循环记录。

记录字段限定为时间、用户名、系统、方法、脱敏路径、状态与耗时。不记录请求体、查询值、资源标识内容、密码、Cookie、令牌或原始异常。客户端提前关闭请求显示 HTTP 499。身份服务不可用时标记身份无法确认，不伪造用户名。

文件为 `.local/integration/platform-audit.jsonl`，在 Git 忽略目录内。上限约一万条，超过后整理为最近九千条；重启后读取已有记录，写入和读取错误可见。既有 crawler 登录和后台任务审计继续从数据库读取。其余 JVM 原始进程日志保留在原 `.local/integration` 目录，本次不把请求日志当成全部后台进程日志。

## 图一运行失败的核查边界

当前工作区本机库只包含两条试用运行，未找到截图编号 `bc7aebb2-aa95-4870-9a48-629dffd1fa93`，截图中的六个采集任务也不在该库。只读查询使用经归属核对的项目容器，未打印或修改凭据。

修改前已有采集集成测试通过；增加事务提交后启动场景仍通过。运行 jar 内的 `SpringBatchCrawlRunLauncher`、`CrawlJobLauncherConfiguration` 与当前编译类的 SHA-256 相同。没有证据支持直接认定该次失败由事务配置、来源网络或依赖版本引起，因此未作推测性依赖升级或事务重写。

已修复可确认的诊断缺陷：原 `failLaunch` 仅将运行改为 FAILED，没有写失败明细或审计。现在同一事务保存 SYSTEM 失败明细、固定安全分类和 OPERATION_FAILED 审计，包含运行编号；重复处理终态不重复插入。队列繁忙、存储不可用、事务异常、无效参数和未知错误各有固定建议，不泄露异常内连接信息。启动失败没有学术原始记录，不标记为“失败记录重试”，应排查后对任务重新执行。界面会明确指出批次尚未启动。

**截图中该次运行的原始触发根因仍未确认，需要截图所用访问地址或对应运行环境。不能将诊断缺陷修复表述为已经复现并消除该次运行故障。**

## 验证命令与结果

以下均实际执行，除注明外在仓库根目录运行。没有安装或升级依赖。

| 命令 | 观察结果 |
| --- | --- |
| `npm.cmd --prefix portal test` | 17 项通过，含日志脱敏、四系统归属、持久化、分页筛选、保留上限、权限拒绝、身份服务故障和管理回跳白名单。 |
| `npm.cmd --prefix systems/crawler/frontend run test -- src/router/index.test.ts src/stores/session.test.ts src/views/LogsView.test.ts src/components/business/AppSidebar.test.ts` | 46 项通过。首次失败暴露独立模式旧路由兼容问题，恢复独立模式原路由后通过，没有弱化原断言。 |
| `..\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/crawler/tmp' '-Dtest=SpringBatchCrawlRunLauncherTests,CrawlRunServiceTests,OpenAlexBatchOrchestrationIntegrationTests' test` | 在 `systems/crawler/backend` 执行，11 项通过；覆盖回滚不启动、异常分类、终态幂等、真实数据库失败记录与审计、提交后启动、采集、重试、额度等待及恢复。MySQL/Neo4j 使用隔离测试容器。 |
| `..\mvnw.cmd -o -B '-DskipTests' package` | 同目录，后端打包通过；测试已由上一行独立执行。 |
| `npm.cmd --prefix portal run build` | 通过。 |
| `npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/` | TypeScript 与多入口构建通过。首次新增筛选表单出现可选时间字段类型错误，修正后通过。 |
| `npm.cmd --prefix systems/relation/frontend run build -- --base=/relation/` | 通过。 |
| `npm.cmd --prefix systems/extraction/frontend run build -- --base=/extraction/` | 通过。 |
| `npm.cmd --prefix systems/scholar/web run build -- --base=/scholar/` | 通过。 |
| `node scripts/Test-PlatformBrowser.mjs` | 通过：1440/390px 门户、四系统顶部返回、切换后持续全屏、子系统全屏/管理入口移除、统一账号页、请求日志、后台审计及业务页刷新恢复。用户概况跳转登录审计和启动失败提示/明细也已验证；启动失败页面使用明确标记的浏览器固定测试响应，不是截图原运行的复现。 |
| `node scripts/Test-PortalBrowser.mjs systems/crawler/frontend/node_modules/playwright/index.mjs` | 通过：原门户搜索、帮助、响应式、系统入口及配置失败重试。 |
| `node scripts/Test-SystemsBrowser.mjs` | 四系统业务路由、直接访问、深链接刷新和共享身份通过。 |
| `node scripts/Test-UnifiedLoginBrowser.mjs` | 单独执行通过：三种宽度、错误密码、密码可见性、深链接回跳、四系统退出与外站回跳拦截。首次并发运行遇到 `net::ERR_ABORTED` 导航中止；单独复验通过，并增加等待页面数据加载完成再执行退出。 |
| `node scripts/Test-IntegratedSystems.mjs` | 四个后端统一身份、真实读接口、CSRF 拒绝、未知 API、统一退出及会话撤销通过。 |
| `npm.cmd --prefix portal run check:source` | 四系统来源树及适配哈希校验通过，来源 SHA 未改变。 |
| `git -c core.safecrlf=false diff --check` | 最终通过。初次检查发现两处删除悬浮入口后多余的末尾空行，修正并同步适配哈希后通过。 |

首次在 crawler/backend 使用 `mvnw.cmd` 失败，因为该系统的 Wrapper 位于父目录；后续命令均使用 `..\mvnw.cmd`。首次受限环境 Docker 查询和 Node 子进程调用不可用，使用获准的本机执行权限后完成。只读 SQL 初次误用 `run_no`，根据实际迁移修正为 `run_number` 后完成查询。一次门户启停与尚在执行的 crawler 启动发生锁冲突，等待后按顺序重试成功。没有绕过启停锁或按端口强制杀进程。

最终已重新构建五个前端、打包 crawler，并使用现有启停脚本重新启动本工作区的 crawler 和门户；其余后端及数据库保留。截图在 `.local/platform-browser/`，构建和测试日志在 `.local/platform-*.log`，均不提交。前端构建仍有既有的大 chunk 提示。Development 模式的管理页转发有实现，但本次真实浏览器验收使用 Demo，未新增验收公网/Nginx或真实外部采集。

## 项目记忆同步

实施前读取 `docs/integration-baseline.md`、`docs/development.md`，并核对 README、统一登录契约、代码、迁移、测试和运行 jar。两份指定记忆已同步管理路由、全屏容器、消息和深链接契约、日志范围与保留策略、采集诊断行为及原故障未复现的边界。README 与统一登录说明中旧的 `/crawler/users` 当前入口改为 `/management/users`。来源适配记录同步实际子系统改动，不改变来源 SHA。不新增 MEMORY.md 或其他记忆体系。

## 用户后续操作

打开或刷新 [统一门户](http://127.0.0.1:18000/)，使用既有账号。核查原失败还需要提供截图所用的访问地址，不需要提供密码或凭据。本次未改账号、密码、业务 schema 或既有业务数据，未创建 Git 提交、分支或推送。

## 实际变更文件

下面的清单在最终差异核对时生成，仅列本次新增或修改的文件。
<!-- changed-files -->

共 50 个版本化源码、测试和说明文件。

| 文件 | 变更说明 |
| --- | --- |
| `README.md` | 同步平台管理入口、全屏容器、日志与采集诊断契约。 |
| `docs/development.md` | 同步平台管理入口、全屏容器、日志与采集诊断契约。 |
| `docs/integration-baseline.md` | 同步平台管理入口、全屏容器、日志与采集诊断契约。 |
| `docs/platform-management.md` | 记录实现范围、实测证据、限制与实际变更文件。 |
| `docs/source-adaptations.json` | 同步实际子系统适配哈希，保留原始来源。 |
| `docs/unified-login.md` | 同步平台管理入口、全屏容器、日志与采集诊断契约。 |
| `portal/src/App.vue` | 增加统一管理与全屏入口，保留顶层页面并同步安全业务地址。 |
| `portal/src/PortalRoot.vue` | 增加统一管理与全屏入口，保留顶层页面并同步安全业务地址。 |
| `portal/src/services/auth.js` | 增加统一管理与全屏入口，保留顶层页面并同步安全业务地址。 |
| `portal/src/style.css` | 增加统一管理与全屏入口，保留顶层页面并同步安全业务地址。 |
| `scripts/Test-PlatformBrowser.mjs` | 验证平台管理、共享会话、导航、全屏与日志，适配门户容器。 |
| `scripts/Test-PortalBrowser.mjs` | 验证平台管理、共享会话、导航、全屏与日志，适配门户容器。 |
| `scripts/Test-SystemsBrowser.mjs` | 验证平台管理、共享会话、导航、全屏与日志，适配门户容器。 |
| `scripts/Test-UnifiedLoginBrowser.mjs` | 验证平台管理、共享会话、导航、全屏与日志，适配门户容器。 |
| `scripts/lib/gateway.mjs` | 接入管理页面、实时权限检查及全系统日志。 |
| `scripts/lib/platform-audit.mjs` | 实现平台日志分类、脱敏、有界持久化与查询。 |
| `scripts/tests/gateway.test.mjs` | 验证平台管理、共享会话、导航、全屏与日志，适配门户容器。 |
| `scripts/tests/platform.test.mjs` | 验证平台管理、共享会话、导航、全屏与日志，适配门户容器。 |
| `systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/CrawlRunService.java` | 分类并持久化启动失败明细与审计，保留运行状态契约。 |
| `systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/port/CrawlRepository.java` | 分类并持久化启动失败明细与审计，保留运行状态契约。 |
| `systems/crawler/backend/src/main/java/com/aacv/system/crawl/domain/CrawlLaunchFailure.java` | 分类并持久化启动失败明细与审计，保留运行状态契约。 |
| `systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/batch/SpringBatchCrawlRunLauncher.java` | 分类并持久化启动失败明细与审计，保留运行状态契约。 |
| `systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/persistence/CrawlMapper.java` | 分类并持久化启动失败明细与审计，保留运行状态契约。 |
| `systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/persistence/MyBatisCrawlRepository.java` | 分类并持久化启动失败明细与审计，保留运行状态契约。 |
| `systems/crawler/backend/src/main/resources/mapper/crawl/CrawlMapper.xml` | 分类并持久化启动失败明细与审计，保留运行状态契约。 |
| `systems/crawler/backend/src/test/java/com/aacv/system/crawl/application/CrawlRunServiceTests.java` | 验证事务提交后启动、失败诊断、回滚与重复处理。 |
| `systems/crawler/backend/src/test/java/com/aacv/system/crawl/infrastructure/batch/SpringBatchCrawlRunLauncherTests.java` | 验证事务提交后启动、失败诊断、回滚与重复处理。 |
| `systems/crawler/backend/src/test/java/com/aacv/system/source/infrastructure/openalex/OpenAlexBatchOrchestrationIntegrationTests.java` | 验证事务提交后启动、失败诊断、回滚与重复处理。 |
| `systems/crawler/frontend/management.html` | 装配独立平台管理页面并复用现有账号、审计和组件依赖。 |
| `systems/crawler/frontend/src/App.vue` | 移除右下角悬浮返回入口。 |
| `systems/crawler/frontend/src/components/business/AppTopbar.vue` | 将返回门户放入顶部左侧，并移除子系统管理或全屏入口。 |
| `systems/crawler/frontend/src/config/nav.ts` | 移除集成模式的子系统管理菜单或日志展示入口。 |
| `systems/crawler/frontend/src/management/ManagementApp.vue` | 装配独立平台管理页面并复用现有账号、审计和组件依赖。 |
| `systems/crawler/frontend/src/management/PlatformLogsView.vue` | 装配独立平台管理页面并复用现有账号、审计和组件依赖。 |
| `systems/crawler/frontend/src/management/logs.ts` | 装配独立平台管理页面并复用现有账号、审计和组件依赖。 |
| `systems/crawler/frontend/src/management/main.ts` | 装配独立平台管理页面并复用现有账号、审计和组件依赖。 |
| `systems/crawler/frontend/src/router/index.ts` | 同步业务深链接，移除或重定向集成模式的旧管理入口。 |
| `systems/crawler/frontend/src/views/CrawlTasksView.vue` | 明确显示批次未启动及下一步排查建议。 |
| `systems/crawler/frontend/src/views/OverviewView.vue` | 移除集成模式的子系统管理菜单或日志展示入口。 |
| `systems/crawler/frontend/src/views/UsersView.vue` | 用户概况中的登录记录链接改到平台审计。 |
| `systems/crawler/frontend/vite.config.ts` | 装配独立平台管理页面并复用现有账号、审计和组件依赖。 |
| `systems/extraction/frontend/src/App.vue` | 移除右下角悬浮返回入口。 |
| `systems/extraction/frontend/src/layout/Topbar.vue` | 将返回门户放入顶部左侧，并移除子系统管理或全屏入口。 |
| `systems/extraction/frontend/src/router/index.js` | 同步业务深链接，移除或重定向集成模式的旧管理入口。 |
| `systems/relation/frontend/src/App.vue` | 移除右下角悬浮返回入口。 |
| `systems/relation/frontend/src/router/index.ts` | 同步业务深链接，移除或重定向集成模式的旧管理入口。 |
| `systems/relation/frontend/src/views/LayoutView.vue` | 将返回门户放入顶部左侧，并移除子系统管理或全屏入口。 |
| `systems/scholar/web/src/App.vue` | 移除右下角悬浮返回入口。 |
| `systems/scholar/web/src/components/layout/AppTopbar.vue` | 将返回门户放入顶部左侧，并移除子系统管理或全屏入口。 |
| `systems/scholar/web/src/router/index.js` | 同步业务深链接，移除或重定向集成模式的旧管理入口。 |
