# 四系统本地运行验收

> 本文保留统一登录改造前的运行验收。当前已改为 crawler 统一账号和跨系统统一退出，最新认证行为及命令结果见 [统一登录说明](unified-login.md)，不再采用以下历史记录中的独立登录流程。

> 后续提交整理已将系统及门户改动分主题提交并推送至 `origin/dev`；下文的分支、HEAD 和未提交状态属于接入阶段的历史记录，当前状态以 [集成基线](integration-baseline.md) 和 Git 记录为准。

执行日期：2026-09-09。工作目录 `F:\Program\Java\course_design`，分支 `codex/integrate-systems`。本轮目标是把各分支的可运行子系统接入本地统一门户；没有创建提交、推送、部署公网或迁移原业务数据。

## 来源与变更

执行 `git fetch --no-tags origin`。Du、Luo 与已有导入保持一致；Li 完整系统通过 `git archive --format=zip --output=.local/integration-work/Li-source.zip origin/Li` 导入新目录 `systems/scholar`。Ye 远端回退到 `bf2494f…`，保留现有完整 `184f651…`，差异见 [集成基线](integration-baseline.md)。`feature/Li` 和 `feature/Ma` 没有可接入的业务代码。

四套来源共 1072 个原始文件，原三套导入历史保留。各原始树、来源 SHA 与导入方式在 `import-records.json`，具体源文件适配差异和对象哈希在 `source-adaptations.json`。新增 Li 目录保留了其原有 web/backend 布局，根部旧 Servlet 练习工程不参与运行。

集成层涉及 `deploy/systems.json`、网关/配置/Nginx 生成逻辑、统一初始化/构建/启停、来源检查及验收脚本。门户基于用户已有视觉改动增加第四张卡片、两列布局及状态校验；没有覆盖已有图片、图标、主题或其他组件。

## 已执行并通过

下列命令在仓库根执行，另行说明工作目录的除外。

| 命令 | 观察结果 |
| --- | --- |
| `scripts/Initialize-Integration.ps1` | 四个 MySQL 与三个 Neo4j 容器均 Healthy；新建独立账号、库与卷。重复执行保留全部凭据，已用文件哈希比较验证 |
| `scripts/Build-Integration.ps1` | 四套前端及四个后端 jar 均构建成功；后续修复的前端及后端已分别重建 |
| `npm.cmd --prefix portal run build` | 门户构建成功，生成四系统状态快照及维护页 |
| `npm.cmd --prefix portal run test` | 9 项通过、0 失败、0 跳过；维护场景覆盖 140 个 API 方法/路径请求，下游访问为 0；包含 Actuator 和上下文代理检查 |
| `npm.cmd --prefix systems/crawler/frontend run test` | 35 个测试文件、167 项测试通过 |
| `npm.cmd --prefix portal run check:source` | 四套原始来源、全部文件及已记录适配通过；前三套来源祖先检查保留，Li 不伪造祖先关系 |
| `scripts/Test-IntegrationEnvironment.ps1 -Mode Demo` | 门户及四个后端规划端口可绑定 |
| `scripts/Start-Integration.ps1 -System all -Mode Demo` | 门户与四个后端依次就绪，统一入口 127.0.0.1:18000 |
| `node scripts/Test-IntegratedSystems.mjs --direct` | 四个真实后端全部通过 |
| `node scripts/Test-IntegratedSystems.mjs` | 经过门户转发的四系统就绪、认证、查询、CSRF、404、退出与会话隔离全部通过 |
| `node scripts/Test-PortalBrowser.mjs systems/crawler/frontend/node_modules/playwright/index.mjs` | Edge 桌面、390px 门户布局、四入口与返回、搜索、帮助、配置错误及重试通过 |
| `node scripts/Test-SystemsBrowser.mjs` | 四系统真实界面登录、各两个业务页面、深链接刷新、会话保持、返回门户、无脚本异常及无错误提示通过 |
| `scripts/Test-IntegrationEnvironment.ps1 -Mode Development`、`scripts/Start-Integration.ps1 -System all -Mode Development` | 四个后端、四个 Vite 开发服务与门户就绪；开发模式真实 API 验收通过 |
| `node scripts/Test-SystemsBrowser.mjs --development` | 四系统浏览器用例通过；每套 Vite 的 HMR WebSocket 均经 18000 对应子路径收到 connected 帧 |
| `scripts/Test-IntegrationRecovery.ps1` | 逐个停止四套后端：所属 API 返回 BACKEND_UNAVAILABLE/503，门户和其余服务保持健康；单系统恢复全部通过 |
| `scripts/Test-IntegrationLifecycle.ps1` | 端口占用、重复启动、按系统停止、身份不匹配拒绝、数据标记保留、重复停止和门户两种模式通过 |
| `npm.cmd --prefix portal run generate:nginx` | 成功生成忽略目录 `.local/nginx/nginx.conf`；没有执行 Nginx 本体 |
| `python -X utf8 .local/integration-work/final-audit.py`、`git diff --check` | 原有 36 个用户文件全部保留；31 个字节哈希不变，5 个为已审阅的整合适配；本轮 61 个编写/适配文件 UTF-8 检查与 17 个本地文档链接检查通过，diff 无空白错误 |

前端锁文件恢复实际使用：`npm.cmd --prefix <前端目录> ci --no-audit --no-fund --offline=false --registry=https://registry.npmjs.org --cache .local/npm-cache`，依次执行 relation/frontend、extraction/frontend、crawler/frontend、scholar/web，均成功。没有重写锁文件。

本轮单独重建前端的准确命令：

```powershell
npm.cmd --prefix systems/relation/frontend run build -- --base=/relation/
npm.cmd --prefix systems/extraction/frontend run build -- --base=/extraction/
npm.cmd --prefix systems/scholar/web run build -- --base=/scholar/
```

### 后端测试

在 `systems/relation/backend`：

```powershell
.\mvnw.cmd -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/relation/tmp' '-Dtest=OpenAlexResponseParserTest,LlmResultParserTest,OpenAlexWorkMapperTest,GlobalExceptionHandlerTest' test
```

25 项通过。该系统原有整套接口测试依赖默认演示管理员，未对本轮随机凭据环境直接执行；本轮另有真实 HTTP 认证/查询/退出验收。

在 `systems/extraction`（先停止该后端以解除 jar 文件锁）：

```powershell
.\mvnw.cmd -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/extraction/tmp' '-Dspring.config.additional-location=file:F:/Program/Java/course_design/.local/integration-runtime/extraction/application.properties' verify
```

原有上下文测试 1 项通过，verify 与打包成功。使用本轮独立数据库，未连接来源配置的原库。

在 `systems/crawler`：

```powershell
.\mvnw.cmd -B -f backend/pom.xml '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/crawler/tmp' '-Dtest=SecurityIntegrationTests,Neo4jDriverTimeoutConfigurationTests,LocalExportFileStoreTests' test
```

30 项通过，包含原有真实 Testcontainers 安全集成用例。临时测试基础设施不复用本轮演示数据库。

在 `systems/scholar/web/backend`：

```powershell
.\mvnw.cmd -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/scholar/tmp' '-Dspring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR' '-Dspring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true' verify
```

原有上下文测试 1 项通过，verify 与打包成功。命令级适配 H2 的 YEAR 保留字和本机 Java Socket 目录，未改生产数据库字段。

### 浏览器证据

截图位于忽略目录 `.local/browser`：portal-desktop.png、portal-mobile.png、relation-desktop.png、extraction-desktop.png、crawler-desktop.png、scholar-desktop.png。已逐张复核桌面截图及门户窄屏截图。

四套系统的实际查询使用新数据库，图谱和列表为空。Li 的 fcose 非空布局另外使用两节点一连线的浏览器响应夹具验证；该夹具只用于前端布局，不作为真实业务数据或后端能力证据，不写入数据库。

## 失败与重跑

| 初次失败 | 原因和处理 |
| --- | --- |
| 受限环境 `npm.cmd --prefix portal run test` 报 `spawn EPERM`；Docker/CIM 只读检查被拒绝 | 工具环境限制；在获允许的正常本机权限下重跑，9 项测试通过 |
| extraction 首次启动 `Schema validation: missing table [authors]` | 上游 Boot 4 POM 仅声明 flyway-core，缺少自动迁移模块；补齐同版本 Flyway Starter，原 V001 成功执行，保留 ddl-auto=validate |
| extraction 作者查询因测试遗漏必填 name 返回 500；未知 API 也返回 500 | 测试按真实契约补齐 name；现有异常层补充缺失参数 400 和未知资源 404，真实 HTTP 负向用例通过 |
| extraction 重新打包无法将运行中 jar 改名 | Windows 文件占用；停止所属后端后 verify 通过，构建脚本增加运行状态预检 |
| scholar 首次 `mvnw.cmd -B verify` 出现 H2 YEAR 语法问题和 `Unable to establish loopback connection` | 原测试库/本机兼容问题；命令级 NON_KEYWORDS 与 Socket 目录适配后通过，并启用 schema 错误立即失败 |
| 关系图谱刷新后回登录页 | 原前端路由守卫早于会话恢复；补齐 relation、extraction 启动恢复流程，浏览器刷新验证通过 |
| 爬虫浏览器登录按钮等待超时 | 测试选择器假定 submit 按钮，与现有点击按钮不符；修正选择器，没有修改该登录页面 |
| scholar 图页面弹出 `No such layout fcose found` | 截图复核发现上游漏注册已有扩展；注册 fcose、处理空图，并增加错误提示与非空布局验证 |
| 重复初始化 Set-Acl 报 `SeSecurityPrivilege` | 已隔离目录不需要重复设置所有者；改为校验并保留正确 ACL，再次初始化成功，凭据哈希未变 |
| 一次临时打包命令重定向路径少算目录层级 | 未执行到 Maven；改用本仓库绝对日志路径后重新执行成功 |
| 最终文件审计首次将 overview.js 判为未授权改动 | 临时审计比较混用了 Windows 反斜杠与正斜杠；规范路径后重跑通过，实际变更仍是计划内的五个文件 |

这些初次失败没有计入通过结果。没有删除、跳过或降低有效测试断言来使结果通过。

## 交付时状态与最后复核

开发模式验收结束后，执行 `scripts/Stop-Integration.ps1 -System all`，再按 Demo 模式检查环境并启动全部系统；重新执行真实 API 和门户浏览器验收，全部通过。交付时保留门户及四个后端运行，七个基础服务容器均为 healthy。

使用 `Read-IntegrationState`、`Test-IntegrationIdentity` 和 `Get-NetTCPConnection -State Listen` 逐个核对五个应用进程身份及监听地址，全部只绑定 127.0.0.1。使用 `docker ps --filter 'label=com.docker.compose.project=course-integration' --format '{{.Names}} | {{.Status}} | {{.Ports}}'` 核对基础服务归属、健康状态及本地端口；没有将凭据、完整容器配置或进程环境输出到日志。

运行中调用 `scripts/Build-Integration.ps1` 按预期拒绝重新打包，异常由验证命令断言后捕获。`Get-Acl -LiteralPath '.local/integration-runtime'` 与 `git check-ignore .local/integration-runtime/compose.json .local/integration-runtime/relation/credentials.json` 确认私有权限及 Git 忽略生效。复核完整变更、源文件适配记录、既有锁文件及 HEAD；现有锁文件未变，HEAD 仍为 `350ab2b`。

源文件适配记录为 relation 8 项、extraction 8 项、crawler 3 项、scholar 9 项，均与已审阅差异一致。1072 个来源文件以及新增适配文件再次通过来源校验。临时日志、截图、凭据、构建产物和审计辅助脚本均位于既有忽略范围，未纳入源文件导入记录。

## 边界与未执行项

- 没有导入原业务数据或随意填充实际数据库；门户统计仍是演示内容。
- 未调用外部 LLM、Semantic Scholar、OpenAlex、Crossref 的真实采集任务；外部凭据、限流与数据质量不在本次运行验收内。
- 未执行四套系统全部业务回归；relation、scholar 虽声明 Vitest 命令，但没有现有前端测试文件，extraction 没有前端 test 命令，没有用允许空测试参数伪造成功。
- Li 的两个间接开发工具包在本机 Node 24.14.0 上发出引擎警告；前端均已构建并通过 Edge 运行检查。图表类前端仍有大 chunk 构建警告，未做无关性能重构或依赖升级。
- Nginx 配置生成器已适配上下文并有测试；Nginx 本体、Windows PowerShell 5.1、其他浏览器和公网部署未验收。
- 接入验收时工作区尚未提交；后续已将这些改动和原有门户视觉内容纳入 `dev`。克隆 `dev` 可获取版本化源码，本机凭据、数据库卷和构建产物仍需依照 README 在本地准备。

## 2026-09-09 提交前复核

本次仅整理已有改动并同步提交状态说明，没有重新执行真实数据库、浏览器、生命周期或公网部署验收。以下命令在提交前实际执行，未将历史运行结果当作本次验证。

| 命令 | 工作目录 | 本次结果 |
| --- | --- | --- |
| `npm.cmd --prefix portal test` | 仓库根目录 | 13 项通过 |
| `node scripts/check-source.mjs` | 仓库根目录 | 四系统来源树、适配内容及适用的祖先关系全部通过 |
| `npm.cmd --prefix systems/crawler/frontend test -- src/stores/session.test.ts src/router/index.test.ts` | 仓库根目录 | 2 个测试文件、35 项通过 |
| `.\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/relation/tmp' '-Dtest=PortalAuthenticationFilterTest' test` | `systems/relation/backend` | 6 项通过 |
| `.\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/extraction/tmp' '-Dtest=PortalAuthenticationFilterTest' test` | `systems/extraction` | 6 项通过 |
| `.\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/scholar/tmp' '-Dtest=PortalAuthenticationFilterTest' test` | `systems/scholar/web/backend` | 6 项通过 |
| `npm.cmd --prefix portal run build` | 仓库根目录 | 通过 |
| `npm.cmd --prefix systems/relation/frontend run build -- --base=/relation/` | 仓库根目录 | 类型检查及构建通过 |
| `npm.cmd --prefix systems/extraction/frontend run build -- --base=/extraction/` | 仓库根目录 | 构建通过 |
| `npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/` | 仓库根目录 | 类型检查及构建通过 |
| `npm.cmd --prefix systems/scholar/web run build -- --base=/scholar/` | 仓库根目录 | 构建通过 |
| `git -c core.safecrlf=false diff --cached --check` | 仓库根目录，分批暂存后 | 门户批次通过；系统批次提示 CNKI 导入表格行尾空白及三个 `IntegrationHealthController.java` 文件末尾空行 |

四套业务前端仍有大 chunk 构建警告。上述空白问题存在于本次整理前的文件内容中，来源及适配哈希检查通过；为保留既有工作，本次没有进行格式清理。最初只读审计脚本因沙箱限制出现 `spawnSync git EPERM`，经工具权限机制以相同只读范围重试后完成。被忽略的本机凭据和运行目录未纳入提交。

## 项目记忆同步

读取并核对 `integration-baseline.md`、`development.md`、`integration-plan.md`、`integration-acceptance.md` 与 `import-records.json`。当前来源、四套系统状态、真实端口、运行方式、必要修复、验证和限制已同步到原有记忆体系；第一阶段文档明确标记为历史记录。没有另建独立记忆体系，也没有记录凭据或原业务数据。
