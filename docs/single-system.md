# 单系统调整与硕博导师显示

实施日期：2026-09-12。

## 任务总结

删除学术关系知识图谱构建平台 `systems/relation/` 与统一门户 `portal/`，项目仅保留“学术成果信息采集及可视化系统”。根地址进入 `/crawler/`，保留既有后端、数据库和身份体系；成果系统本身的学术关系、成果、背景图谱仍可使用。

指导硕论、指导博论编目新增“导师”列。后端在现有分页查询后批量读取 `achievement_advisor`，对成果和导师的规范身份分别归并、去重并排序，新增 `advisors` 数组；前端以顿号显示多人导师。其他编目返回空数组并隐藏该列。不从署名作者推断导师，没有新增数据库迁移或依赖。

## 变更范围

| 文件或组件 | 变化 |
| --- | --- |
| `portal/`、`systems/relation/` | 删除源码及原独立工程，归档保留本机副本 |
| `scripts/server.mjs`、`scripts/lib/return-path.mjs` | 网关从门户工程移出，保留安全书签跳转 |
| `deploy/systems.json`、构建/启停/初始化/样例导入脚本 | 只处理 crawler；保留旧进程标识以兼容本机状态 |
| `scripts/lib/gateway.mjs`、`auth.mjs`、`platform-audit.mjs` | 根入口、管理书签和原生登录接线；保留 CSRF、Origin、会话转换与日志权限 |
| crawler 前端路由、导航、会话、登录、顶栏与日志页 | 删除门户和 iframe 耦合，恢复自身账号/日志管理，保留全屏；删除旧管理多入口产物 |
| `CatalogEntityItem.java`、`CatalogEntityPageResponse.java`、`CatalogMapper.java`、`MyBatisCatalogRepository.java`、`CatalogMapper.xml` | 导师字段、批量查询、规范身份去重 |
| `CatalogEntitiesView.vue`、`types/api.ts`、`openapi.yaml` | 导师列及接口契约 |
| 后端编目测试、前端候选 fixture、浏览器编目回归、Playwright 配置与网关测试 | 覆盖导师语义、分页、单系统入口、权限和 `/crawler/` 预览前缀 |
| `scripts/Test-SingleSystemBrowser.mjs` | 使用生产产物与模拟后端验证完整单系统；替代被删除的门户专属测试 |
| README、指定项目记忆、认证说明、来源记录、Nginx 说明及受影响历史验收标记 | 同步单系统范围和验收边界 |
| `.local/Start-LocalProject.ps1`（Git 忽略） | 本机启动不再检查 relation 的 Neo4j |

详细逐文件状态另存本机 `.local/single-system-review-20260912/changed-files.md`。该清单区分本次修改与原有 ORCID 改动，不将未修改的原有文件计入本次变更。

## 验证结果

以下根目录命令在 `E:\Program\Java\course_design\XSGXZSTP-KS` 执行；前后端命令注明对应目录。日志位于 `.local/single-system-review-20260912/`。

| 实际执行命令 | 结果 |
| --- | --- |
| `node --test --test-isolation=none scripts/tests/*.test.mjs` | 17 项通过：配置、维护/错误响应、根入口与退役路由、安全回跳、会话和日志权限 |
| `npm.cmd test -- src/router/index.test.ts src/stores/session.test.ts src/views/LoginView.test.ts src/components/business/EntitySuggestInput.test.ts`（crawler/frontend） | 4 个文件、48 项通过 |
| `.\mvnw.cmd -B -f backend/pom.xml '-Dtest=CatalogWorkEntitiesIntegrationTests,OpenApiDocumentTests' test`（systems/crawler） | 6 项通过，BUILD SUCCESS；使用隔离 MySQL/Neo4j Testcontainers，无业务库访问 |
| `npm.cmd run build -- --base=/crawler/`（crawler/frontend） | TypeScript 与生产构建通过；保留原有大于 500 kB 分块提示 |
| `node scripts/Test-SingleSystemBrowser.mjs` | 生产构建与模拟后端：根入口、登录失败/成功、深链接回跳、刷新会话、硕博导师、账号及日志权限、全屏、退出和退役路径通过；登录宽度覆盖 1440/390/320 |
| `$env:AACV_E2E_PREVIEW='1'; $env:AACV_E2E_BASE='/crawler'; npm.cmd run test:e2e -- e2e/compact-research.spec.ts`（crawler/frontend） | 8 项通过，包含多人导师、成果目录与统计页面 1440/2549/390 宽度 |
| `.\scripts\Test-IntegrationInitialization.ps1` | 通过，只声明 crawler 的两个服务和两个卷，重复初始化保留配置与凭据；Docker 调用为模拟 |
| `.\.local\single-system-review-20260912\test-gateway-launch.ps1` | Demo/Development 网关真实启动和停止、健康、根入口跳转及 relation 404 通过；原 crawler 进程身份保持 |
| `node scripts/config.mjs all Demo`、`node scripts/config.mjs all Development` | 启动计划分别只包含 crawler 后端、crawler 后端加前端，无 relation |
| `node --check scripts/server.mjs`、`node --check scripts/Test-SystemsBrowser.mjs`、`node --check scripts/Import-IntegrationDemoData.mjs` | JavaScript 语法检查通过 |
| `node scripts/generate-nginx.mjs` | 配置生成通过；未执行 Nginx 本体或 `nginx -t` |
| `node .local/single-system-review-20260912/verify-source.mjs --refresh` | 当前 crawler 的 742 个文件来源适配通过；33 个非共享原有改动文件 SHA256 未改变 |
| `node scripts/check-source.mjs` | crawler 来源、祖先及全部文件通过；完整历史校验仍因已退役 scholar 的 `f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d` 对象缺失而失败 |
| `git -c core.safecrlf=false diff --check` | 通过 |

初次前端构建发现路由回调类型与新增字段 fixture 不匹配，已修复并重新通过。Node 子进程、Vite 路径检查、Docker API 和临时目录 ACL 初次受到沙箱权限限制，分别使用 Node 单进程测试或正常本机权限重新验证。首次研究页面浏览器测试因预览未应用 `/crawler/` 前缀失败，已同步预览配置，重跑 8 项通过；没有删除或降低原断言。

测试截图位于 `.local/single-system-review-20260912/browser/`，其中导师为“导师甲、导师乙”的模拟数据。已人工查看桌面登录和指导博论截图，不能将其描述为真实数据库记录。

## 项目记忆

读取并更新指定的 `docs/integration-baseline.md`、`docs/development.md`。已按当前源码纠正两系统/门户架构、登录管理路由、构建和初始化范围，补充 `advisors` 契约与运行限制；更新 README、认证说明、退役来源和适配哈希。旧验收文档添加历史标记，没有创建新的项目记忆体系。

既有 ORCID 代码、测试、迁移及导入逻辑的 33 个非共享改动文件保持原 SHA256。共享 README、两份指定记忆、OpenAPI 和来源适配文件按本次范围合并，原 ORCID 功能与接口保留。对照旧记忆发现的主要不一致是本次删除后过时的系统/入口描述，已通过配置、路由、启停脚本与测试校正。

## 风险与运行状态

本轮没有替换正在运行的 crawler JAR，没有操作业务数据或触发 ORCID 外部查询。为解除目录锁，已通过原脚本归属校验停止旧 relation 和门户；测试新网关后也已停止，当前 18000 入口需在下述启用步骤后恢复。不要把源代码、测试成功或前端构建当作新版后端已投入使用。

保留内部 `portalPort`、`PORTAL_SESSION`、`-System portal` 和 `/__integration/*` 作为兼容名称，不再存在门户页面或独立门户依赖。旧数据库、容器和卷仅保留，不再由新初始化创建。Nginx 运行、公网部署、完整 Development HMR 及真实账号/真实数据页面验收未在本轮执行。完整历史来源检查存在上述旧对象缺失限制。

## 启用当前版本

在仓库根目录的 PowerShell 7 中执行以下操作；停止只作用于归属本项目的应用进程，不删除数据库或数据卷。

```powershell
.\scripts\Stop-Integration.ps1 -System all
.\scripts\Build-Integration.ps1
.\start.bat --no-pause
```

本机 start.bat 继续通过原安全输入流程取得 MySQL 运行所需信息；不要把密码贴到对话或保存到代码。新工作区按 README 完成首次初始化和建号。

启动后访问 `http://127.0.0.1:18000/`，进入实体编目的“指导硕论”“指导博论”查看导师。原数据没有指导关系的论文仍不进入指导编目，系统不会补造导师。
