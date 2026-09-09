# 本地开发与接入维护

## 构建和入口

先运行 `scripts/Initialize-Integration.ps1` 准备本项目独立基础服务；首次执行 `scripts/Build-Integration.ps1 -Restore` 从各系统原有锁文件恢复依赖并构建，后续使用不带 `-Restore` 的命令。各系统继续独立管理 POM、Maven Wrapper、package.json、锁文件和 node_modules。

`scripts/Start-Integration.ps1 -System all -Mode Demo` 启动门户和四个 Java jar，Vite preview 从各系统 dist 提供页面；`Development` 额外启动四个 Vite 开发进程，仍通过 18000 统一访问。两种模式互斥。运行中配置发生变化时网关返回配置错误，必须重启门户以应用新配置。

开发模式可用 `node scripts/Test-SystemsBrowser.mjs --development` 验证真实登录、深链接刷新和通过统一入口建立的 HMR WebSocket 连接。统一登录改造前的四系统接入已通过此检查；本次统一登录的浏览器验收使用 Demo 模式，尚未重跑 Development HMR。演示模式无需运行独立 Vite 开发进程。

构建脚本明确检查正在运行的后端，避免 Windows jar 文件锁。构建使用 `-DskipTests package`，测试在验收阶段单独执行，不能把构建成功当作单元测试成功。

启停记录位于 `.local/integration/processes.json`，校验工作目录、PID、创建时间、可执行文件和命令行。总入口重复启动会拒绝；在门户已运行且模式一致时，可以恢复已停止的单个系统。停止不按端口或进程名批量结束，也不操作原项目。

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

## 验证与环境限制

`scripts/Test-IntegrationInitialization.ps1` 在临时工作区执行初始化脚本，使用替代 Docker 函数验证新配置默认关闭建号、保留本机密码及重复初始化不覆盖配置和凭据，不操作实际数据库。relation、scholar 的 `IntegrationAdminBootstrapTest` 验证未配置密码、旧版密码字段和显式关闭三种情形均不会装配账号引导；原统一身份过滤器用例继续适用。

四系统接入的历史命令和结果见 [本地运行验收](local-runtime-acceptance.md)，本次认证改造的实际命令和结果见 [统一登录说明](unified-login.md)。原有第一阶段记录是历史证据，不能再理解为当前四系统均维护中。

本机受限工具环境的 Node 子进程可能报 `spawn EPERM`，Docker named pipe 和 CIM 也需要工具允许的正常本机权限；这些权限问题不通过修改业务安全设置规避。Java 的 Windows Socket 路径使用本项目独立临时目录。Li 原有 H2 测试使用命令级 `NON_KEYWORDS=YEAR`，不修改业务字段。Li 两个间接工具包在 Node 24.14.0 上有引擎警告，但前端构建和 Edge 实测通过。

官方依据：[Vite 子路径构建](https://vite.dev/guide/build.html)、[Spring Boot 外部配置](https://docs.spring.io/spring-boot/reference/features/external-config.html)、[Spring Boot 4 Flyway 模块要求](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)。Vite preview 仅用作本地入口，Nginx 本体与公网部署仍未验收。
