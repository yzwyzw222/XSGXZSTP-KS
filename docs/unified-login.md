# 统一登录说明与验收

核对日期：2026-09-09。本文记录统一登录改造；仓库指定项目记忆仍为 `integration-baseline.md` 和 `development.md`。此前的 `local-runtime-acceptance.md` 及四份系统接入验收保留为改造前的历史证据。

## 使用方式与范围

打开 http://127.0.0.1:18000/login ，使用现有 crawler 账号登录。登录后从门户访问四个子系统，业务深链接和刷新也使用同一身份。管理员从门户账号菜单的“管理平台账号”进入 `/crawler/users`；忘记密码或需要账号时联系管理员。

统一认证只在当前集成模式启用：前端构建 BASE_URL 为各自的 `/<id>/`，三个接入后端使用 `integration` profile。独立运行时保留原系统认证代码和路由。没有合并数据库、迁移旧账号、修改依赖或创建提交。其他三套系统的旧账号不能用于统一入口。

## 认证契约

- 门户 `/__integration/auth/csrf`、`me` 使用 GET，`login`、`logout` 使用 POST；转发到 crawler 现有认证服务。登录与退出保留 CSRF 校验，并检查同源 Origin。
- 浏览器只将 `PORTAL_SESSION` 作为统一身份，Cookie 使用 `Path=/`、HttpOnly、SameSite=Lax。网关在内部转换为 crawler 的 `CRAWLER_SESSION`；重复或异常格式的门户 Cookie 被拒绝，旧子系统 Cookie 不能绕过统一身份校验。
- relation、extraction、scholar 的 `PortalAuthenticationFilter` 在已有安全链中逐请求向门户确认身份，覆盖当前请求的 SecurityContext；不创建本地账号、不保存跨系统授权缓存，原业务 CSRF 和权限检查继续生效。
- 旧的子系统登录、注册、会话过期页面跳转统一入口；集成网关的旧认证写接口返回 410。relation 旧账号管理接口也返回 410，账号统一在 crawler 管理。
- 回跳只接受四个系统的业务地址或门户，拒绝外站、编码分隔符、路径越界、认证接口及重复登录地址。API 的 401 导向统一登录；初始化时服务不可用会返回门户并显示可重试提示。
- 在门户或任一子系统退出都会销毁同一 crawler 服务端会话，并清除门户 Cookie。退出前的 Cookie 无法再次访问四个后端；已打开页面的本地显示由后续请求或刷新更新。
- 三个接入后端通过启动计划获得 `--integration.portal-url=http://127.0.0.1:<portalPort>`，仅允许本机 HTTP 地址，不跟随身份服务重定向。连接/读取超时为 2/4 秒，网关认证转发超时为 6 秒，前端单次认证请求超时为 8 秒。

| crawler 角色 | relation 身份 | extraction 身份 | scholar 身份 |
| --- | --- | --- | --- |
| ADMIN | ADMIN，原管理员权限 | ADMIN | ADMIN |
| DATA_OPERATOR | ANALYST，原分析员权限 | OPERATOR | OPERATOR |
| RESEARCHER | RESEARCHER，data:read / analytics:read | RESEARCHER | VIEWER |

多角色取管理员、数据操作员、研究人员的顺序。三个接入后端额外限制 RESEARCHER 仅能使用 GET、HEAD、OPTIONS；没有把研究人员提升为写入用户。crawler 继续使用自身角色与实时账号状态校验。

## 登录界面约定

登录页以完成统一登录并进入四个研究系统为目标（Operate），视觉依据为既有门户。以下约定以 `portal/src/components/PortalLogin.vue`、`portal/src/PortalRoot.vue` 的当前实现为准。

- **沿用门户主题**：复用深蓝科技背景、品牌标识、青蓝主按钮和 `PortalIcon`，不增加图片、字体或组件库。色彩与中文字体沿用 `tokens.css`、`style.css`：默认正文使用 `--ink`，辅助说明使用 `--muted`，标题重点与工具图标使用 `--cyan`，字体继承 `--font-ui`。主标题、表单标题、正文与标签保持清晰层级，辅助说明不抢占登录操作。
- **先介绍，再登录**：桌面左侧为主标题和四类研究工具，右侧为登录表单；表单面板最大宽度为 440px。900px 及以下改为单列，保留简短主标题并隐藏介绍段落和工具列表；480px 及以下进一步收起页眉辅助文案并缩减面板内边距，保留完整的账号、密码与提交操作。
- **控件边界与焦点清晰**：面板使用深色底与圆角（16px），输入框和提交按钮采用一致圆角（7px）及最小高度（50px）。面板与输入框使用同一蓝色边线（`#367cb5`）；输入区域获得焦点时边线和轮廓切换为 `--cyan`，按钮和链接保留全局 `--focus` 键盘焦点提示。主按钮沿用门户青蓝底色、浅色描边与悬停高亮。
- **表单含义明确**：账号和密码都保留显式关联标签，分别提供 `username`、`current-password` 自动填充标识；占位文字只作输入提示。密码显示按钮使用可读的“显示／隐藏”文字，并同步无障碍名称与按下状态。账号为空或仅含空白、密码为空时禁用提交。
- **反馈跟随操作**：首次进入先以 `role="status"` 显示“正在确认登录状态…”，确认会话后再展示门户或登录页。提交时表单标记忙碌、输入只读、按钮显示“正在登录…”并禁用，同时由根组件阻止重复请求。失败信息以 `role="alert"` 就近展示并关联两个输入框；账号申请与密码找回通过提示说明需联系平台管理员。

登录页已检查 1440、390、320px 三种宽度，均无横向溢出。独立终检提出的输入框边线对比度问题已采用现有面板边线 `#367cb5` 修正，并重新构建、截图及验证。截图保存在忽略目录 `.local/unified-login/screenshots/`，不包含登录凭据。

## 实际验证

所有命令均在本工作区执行。下列命令使用已有依赖与本机基础服务，没有安装或升级依赖。

| 命令 | 执行目录 | 观察结果 |
| --- | --- | --- |
| `npm.cmd --prefix portal run test` | 仓库根目录 | 13 项通过；覆盖 Cookie、认证代理、CSRF、非法输入、认证故障、回跳和旧入口拦截，以及原配置/网关用例 |
| `npm.cmd --prefix systems/crawler/frontend run test -- src/stores/session.test.ts src/router/index.test.ts` | 仓库根目录 | 35 项通过，覆盖原会话与路由行为 |
| `npm.cmd --prefix portal run build` | 仓库根目录 | 通过；边线修正后重新构建通过 |
| `npm.cmd --prefix systems/relation/frontend run build -- --base=/relation/` | 仓库根目录 | 通过 |
| `npm.cmd --prefix systems/extraction/frontend run build -- --base=/extraction/` | 仓库根目录 | 通过 |
| `npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/` | 仓库根目录 | 通过 |
| `npm.cmd --prefix systems/scholar/web run build -- --base=/scholar/` | 仓库根目录 | 通过 |
| `.\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/relation/tmp' '-Dtest=PortalAuthenticationFilterTest' test` | `systems/relation/backend` | 6 项通过 |
| `.\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/extraction/tmp' '-Dtest=PortalAuthenticationFilterTest' test` | `systems/extraction` | 6 项通过 |
| `.\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/scholar/tmp' '-Dtest=PortalAuthenticationFilterTest' test` | `systems/scholar/web/backend` | 6 项通过 |
| `.\mvnw.cmd -o -B '-DskipTests' package` | 上述三个后端目录各执行一次 | 三个后端打包通过；测试已由上一组命令独立运行 |
| `.\scripts\Stop-Integration.ps1 -System all` | 仓库根目录 | 旧应用进程停止；保留基础服务与数据 |
| `.\scripts\Start-Integration.ps1 -System all -Mode Demo` | 仓库根目录 | 门户和四个后端启动就绪，运行新构建 |
| `node scripts/Test-IntegratedSystems.mjs` | 仓库根目录 | 四个系统真实读接口、CSRF、未知 API、单次登录共享身份、退出后旧 Cookie 重放拦截通过 |
| `node scripts/Test-PortalBrowser.mjs systems/crawler/frontend/node_modules/playwright/index.mjs` | 仓库根目录 | 门户桌面/移动端、搜索、帮助、配置故障重试和四个系统入口通过 |
| `node scripts/Test-SystemsBrowser.mjs` | 仓库根目录 | 四个系统路由切换、深链接刷新、原登录表单移除和浏览器脚本检查通过 |
| `node scripts/Test-UnifiedLoginBrowser.mjs` | 仓库根目录 | 三种宽度、空表单、错误密码、密码显示/隐藏、深链接回跳、四个退出按钮、门户退出和外站回跳拦截通过；边线修正后再次通过 |
| `C:\Users\likecandy\.codex\skills\impeccable\scripts\impeccable.cmd detect --json portal/src/components/PortalLogin.vue` | 仓库根目录 | 检测结果为空数组 |
| `npm.cmd --prefix portal run check:source` | 仓库根目录 | 四个来源树、全部原始文件和适配哈希通过；来源 SHA 未更改 |
| `git diff --check` | 仓库根目录 | 通过；只有已有 Windows 换行转换提示，没有空白错误 |

三个后端各自的过滤器测试覆盖匿名/旧会话/重复 Cookie 拒绝、管理员及非管理员映射、研究人员写入拒绝、身份失效后的重新校验、畸形身份和服务不可用时拒绝访问、健康与 CSRF 放行、旧登录接口关闭以及非本机身份地址拒绝。真实服务验收使用本机现有管理员账号；非管理员角色通过过滤器单元测试验证，没有创建或修改业务账号。

首次 Node 测试因受限工具环境的 `spawn EPERM` 未能运行，使用允许的本机权限重试后通过。首次三个 Java 测试命令 `.\mvnw.cmd -o -B '-Dtest=PortalAuthenticationFilterTest' test` 因 Windows Socket 临时路径导致 `Unable to establish loopback connection`，采用上表命令级专用临时目录后 18 项全部通过。没有关闭认证、CSRF 或弱化断言来规避环境问题。

浏览器脚本最初将 crawler 首页误写为不存在的 `/crawler/dashboard`，导致退出菜单定位失败；经实际 Router 核对，正确首页为 `/crawler/`。验收脚本已修正路径并增加“不出现页面不存在”的断言，随后通过。此处没有更改业务路由来迎合测试。前端构建保留原有大 chunk 提示，没有为本次登录改动重构打包。

## 项目记忆同步

实施前读取 `integration-baseline.md`、`development.md`，并对照原接入方案/验收、导入记录、当前 Git 状态和实际认证调用路径。原“独立认证、退出互不影响”与改造前代码一致；本次改为统一认证后，已在两个指定记忆文件和 README 同步入口、账号来源、会话作用域、权限映射、管理入口及认证服务依赖。旧验收文档增加历史说明，避免被误读为当前认证契约。

子系统来源 SHA 保持不变，本次适配文件的内容哈希同步到 `source-adaptations.json`，保持原来源核验机制。未创建新的 MEMORY.md、PRODUCT.md、DESIGN.md 或其他记忆体系。

## 限制与运行要求

- 本次验收针对现有本机 Node/Vite Demo 网关。旧 Nginx 生成模板尚未接入统一认证，不能用作此次统一登录入口；Nginx、公网 HTTPS 部署、Development 模式的实际浏览器 HMR 未验收。
- 统一身份依赖 crawler 和门户。身份服务无法访问时，三个接入后端返回 503；不回退到旧系统登录、不使用过期身份缓存。
- 各系统仍在同一来源的不同路径运行，子路径不构成不互信应用之间的安全隔离边界。
- 没有验证全量业务写操作、外部 LLM、真实外部采集或历史数据迁移；本次仅验证登录相关改动与已有可运行入口。
- 运行新版本无需重新初始化数据库。当前 Demo 已启动，可直接访问统一入口；后续通常使用原有启停命令。

## 本次变更文件

以下清单按任务开始时的文件哈希核对，列出本次实际新增或修改的 58 个文件；原有其他未提交改动予以保留。没有删除、移动或重命名文件。

| 文件（相对仓库根目录） | 操作 | 本次用途 |
| --- | --- | --- |
| `docs/crawler-acceptance.md` | 修改 | 标记为统一登录改造前的历史接入验收，并链接当前契约。 |
| `docs/development.md` | 修改 | 同步统一会话、角色映射、运行参数和网关限制。 |
| `docs/extraction-acceptance.md` | 修改 | 标记为统一登录改造前的历史接入验收，并链接当前契约。 |
| `docs/integration-baseline.md` | 修改 | 同步当前统一认证架构及记忆一致性。 |
| `docs/local-runtime-acceptance.md` | 修改 | 标记为统一登录改造前的历史接入验收，并链接当前契约。 |
| `docs/relation-acceptance.md` | 修改 | 标记为统一登录改造前的历史接入验收，并链接当前契约。 |
| `docs/scholar-acceptance.md` | 修改 | 标记为统一登录改造前的历史接入验收，并链接当前契约。 |
| `docs/source-adaptations.json` | 修改 | 记录本次 33 个子系统适配文件的当前内容哈希。 |
| `docs/unified-login.md` | 新增 | 新增统一认证契约、界面约定、实际验收和变更说明。 |
| `portal/src/App.vue` | 修改 | 显示真实账号，增加统一退出与平台账号管理入口，更新帮助。 |
| `portal/src/components/PortalLogin.vue` | 新增 | 新增沿用现有风格的响应式统一登录表单。 |
| `portal/src/main.js` | 修改 | 将应用入口切换为统一会话根组件。 |
| `portal/src/PortalRoot.vue` | 新增 | 管理会话恢复、登录、退出、失败提示与安全回跳。 |
| `portal/src/services/auth.js` | 新增 | 封装统一认证请求、超时、CSRF 和回跳校验。 |
| `README.md` | 修改 | 更新统一入口、账号使用和验收命令。 |
| `scripts/lib/auth.mjs` | 新增 | 新增统一认证代理及会话 Cookie 转换。 |
| `scripts/lib/config.mjs` | 修改 | 为三个接入后端生成本机门户认证地址参数。 |
| `scripts/lib/gateway.mjs` | 修改 | 统一旧登录入口跳转、关闭旧认证写接口并转换 crawler Cookie。 |
| `scripts/lib/portal-test-session.mjs` | 新增 | 供验收复用内存中的登录会话及清理逻辑，避免输出凭据。 |
| `scripts/Test-IntegratedSystems.mjs` | 修改 | 按单次统一登录验证四后端身份、读接口、CSRF 和会话撤销。 |
| `scripts/Test-PortalBrowser.mjs` | 修改 | 适配统一登录及测试结束后的统一退出。 |
| `scripts/Test-SystemsBrowser.mjs` | 修改 | 用一次登录访问四系统，校正 crawler 首页并检查真实页面。 |
| `scripts/Test-UnifiedLoginBrowser.mjs` | 新增 | 新增响应式登录、错误、回跳和四系统退出的浏览器验收。 |
| `scripts/tests/auth.test.mjs` | 新增 | 新增统一 Cookie、回跳及认证网关行为测试。 |
| `scripts/tests/gateway.test.mjs` | 修改 | 补充匿名深链接、旧登录页跳转和旧认证接口关闭测试。 |
| `systems/crawler/frontend/src/App.vue` | 修改 | 集成模式会话失效时转回统一登录。 |
| `systems/crawler/frontend/src/layouts/BusinessLayout.vue` | 修改 | 退出按钮改为统一退出，成功后跳转入口并保留错误提示。 |
| `systems/crawler/frontend/src/router/index.ts` | 修改 | 集成模式移除独立登录路由并接入统一登录守卫。 |
| `systems/crawler/frontend/src/services/portal-auth.ts` | 新增 | 封装集成模式回跳与带 CSRF 校验的统一退出。 |
| `systems/crawler/frontend/src/stores/session.ts` | 修改 | 集成模式使用统一退出服务，保留原会话清理流程。 |
| `systems/extraction/frontend/src/api/client.js` | 修改 | 业务请求 401 时回到统一登录入口。 |
| `systems/extraction/frontend/src/composables/useAuth.js` | 修改 | 接入统一退出，区分会话失效与服务不可用。 |
| `systems/extraction/frontend/src/layout/Topbar.vue` | 修改 | 退出按钮改为统一退出，成功后跳转入口并保留错误提示。 |
| `systems/extraction/frontend/src/router/index.js` | 修改 | 集成模式移除独立登录路由并接入统一登录守卫。 |
| `systems/extraction/frontend/src/services/portal-auth.js` | 新增 | 封装集成模式回跳与带 CSRF 校验的统一退出。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/api/controller/SessionController.java` | 修改 | 当前用户接口读取安全上下文中的统一身份。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/config/SecurityConfig.java` | 修改 | 仅在 integration profile 安装统一身份过滤器。 |
| `systems/extraction/src/main/java/com/example/academic_entity_extract_kg_construction/infrastructure/integration/PortalAuthenticationFilter.java` | 新增 | 集成模式逐请求验证统一身份并映射当前系统权限，关闭旧登录接口。 |
| `systems/extraction/src/test/java/com/example/academic_entity_extract_kg_construction/infrastructure/integration/PortalAuthenticationFilterTest.java` | 新增 | 验证统一身份、权限、过期会话、异常身份和服务不可用的拒绝行为。 |
| `systems/relation/backend/src/main/java/com/xsyu/academicgraph/api/auth/AuthController.java` | 修改 | 当前用户接口直接返回已验证的门户身份。 |
| `systems/relation/backend/src/main/java/com/xsyu/academicgraph/infrastructure/integration/PortalAuthenticationFilter.java` | 新增 | 集成模式逐请求验证统一身份并映射当前系统权限，关闭旧登录接口。 |
| `systems/relation/backend/src/main/java/com/xsyu/academicgraph/infrastructure/security/SecurityConfig.java` | 修改 | 仅在 integration profile 安装统一身份过滤器。 |
| `systems/relation/backend/src/test/java/com/xsyu/academicgraph/infrastructure/integration/PortalAuthenticationFilterTest.java` | 新增 | 验证统一身份、权限、过期会话、异常身份和服务不可用的拒绝行为。 |
| `systems/relation/frontend/src/api/http.ts` | 修改 | 业务请求 401 时回到统一登录入口。 |
| `systems/relation/frontend/src/api/index.ts` | 修改 | 集成模式退出 API 改为统一认证服务。 |
| `systems/relation/frontend/src/main.ts` | 修改 | 恢复会话失败时区分未登录与认证服务不可用。 |
| `systems/relation/frontend/src/router/index.ts` | 修改 | 集成模式移除独立登录路由并接入统一登录守卫。 |
| `systems/relation/frontend/src/services/portal-auth.ts` | 新增 | 封装集成模式回跳与带 CSRF 校验的统一退出。 |
| `systems/relation/frontend/src/views/LayoutView.vue` | 修改 | 退出按钮改为统一退出，成功后跳转入口并保留错误提示。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/config/SecurityConfig.java` | 修改 | 仅在 integration profile 安装统一身份过滤器。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/infrastructure/integration/PortalAuthenticationFilter.java` | 新增 | 集成模式逐请求验证统一身份并映射当前系统权限，关闭旧登录接口。 |
| `systems/scholar/web/backend/src/main/java/com/aacv/infrastructure/security/SessionUser.java` | 修改 | 支持仅供当前请求使用的门户身份对象。 |
| `systems/scholar/web/backend/src/test/java/com/aacv/infrastructure/integration/PortalAuthenticationFilterTest.java` | 新增 | 验证统一身份、权限、过期会话、异常身份和服务不可用的拒绝行为。 |
| `systems/scholar/web/src/api/request.js` | 修改 | 业务请求 401 时回到统一登录入口。 |
| `systems/scholar/web/src/components/layout/AppTopbar.vue` | 修改 | 退出按钮改为统一退出，成功后跳转入口并保留错误提示。 |
| `systems/scholar/web/src/composables/useSession.js` | 修改 | 接入统一退出，区分会话失效与服务不可用。 |
| `systems/scholar/web/src/router/index.js` | 修改 | 集成模式移除独立登录路由并接入统一登录守卫。 |
| `systems/scholar/web/src/services/portal-auth.js` | 新增 | 封装集成模式回跳与带 CSRF 校验的统一退出。 |
