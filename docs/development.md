# 本地开发与运行维护

本文件与 [integration-baseline.md](integration-baseline.md) 为指定项目记忆，核对日期为 2026-09-13。当前只运行 crawler，首次准备环境、初始化和手动建号见[仓库 README](../README.md)。

## 构建与启停

在仓库根目录使用 PowerShell 7、JDK 21、Node 24.15.0 或更高的 24.x 版本，以及可用的 Docker Desktop Linux Engine。Maven 使用现有 Wrapper，不需要另装。

```powershell
.\scripts\Initialize-Integration.ps1
.\scripts\Build-Integration.ps1 -Restore
.\scripts\Start-Integration.ps1 -System all -Mode Demo
```

初始化只准备 crawler MySQL、Neo4j 和相应数据卷。重复初始化保留已有凭据、配置与数据；新库默认不创建应用账号。构建脚本执行一个前端构建及后端 `-DskipTests package`，不会替代测试。

根目录 `start.bat` 优先使用已有 `.local/Start-LocalProject.ps1`，否则调用标准脚本；`stop.bat` 只停止经过目录、PID、创建时间和命令行归属核验的登记应用，保留数据库。进程登记在 `.local/integration/processes.json`。

Demo 使用 `systems/crawler/frontend/dist` 与 `backend/target/system-0.0.1-SNAPSHOT.jar`；Development 额外启动业务 Vite。两种模式互斥，切换前停止原模式。`-System crawler` 选择后端，`-System portal` 仅选择访问网关。BAT 不自动构建；IDEA 启动的 JVM 由 IDEA 停止。

| 服务 | 标准集成端口或路径 |
| --- | --- |
| Node/Vite 访问网关 | `127.0.0.1:18000` |
| 业务 Vite | `127.0.0.1:5176` |
| Spring Boot | `127.0.0.1:18083/crawler` |
| 初始化 MySQL | `127.0.0.1:23363/course_crawler` |
| Neo4j HTTP / Bolt | `127.0.0.1:27473` / `127.0.0.1:27683` |

已有 Windows MySQL80 环境可以使用本机外部配置中的 `3306/course_crawler`；不能据此覆盖其他工作区的配置。`systems/crawler/tools/development` 是独立调试流程，端口与集成模式不同，运行整个仓库时以本文件和根 README 为准。

## IDEA 调试

仓库当前没有共享的 `.run/CrawlerBackend.run.xml`，不要按不存在的文件或其他电脑的 `system (1)` 模块名启动。

1. 在 IDEA 打开仓库根目录，将 `systems/crawler/backend/pom.xml` 添加为 Maven 工程。不要导入 `.local/` 中的备份 POM。
2. 新建 Application 运行配置，主类选择 `com.aacv.system.AacvSystemApplication`，JDK 选择本机 JDK 21，classpath 选择正式后端 POM 对应的实际模块。
3. 工作目录设置为 `$PROJECT_DIR$/systems/crawler/backend`。
4. Program arguments 填入 `--spring.config.additional-location=file:$PROJECT_DIR$/.local/integration-runtime/crawler/application.properties`。
5. 先按根 README 初始化自己的外部配置，再启动后端；该文件包含本机连接信息，不提交或分享。不要在运行配置中复制密码。外部配置仍引用环境变量时，沿用本机安全输入流程，不猜测连接值。
6. 启动前停止原有同端口后端。核对启动日志使用正式 `backend/target/classes`、集成配置和 `18083 /crawler`。访问网关及前端仍按原运行模式启动。

本机 IDEA 配置由使用者维护，本次文档整理不会改写 `.idea/`。源文件删除后需要重新构建编译输出，防止旧 Controller 或任务类留在 classpath。清理标准 `backend/target` 前先停止使用它的 JVM；不能清理 `.local/` 数据或配置来解决编译问题。

## 数据、任务与兼容

Flyway 保留 V1～V18，历史校验和不能修改。MySQL 业务记录与 Outbox 同事务提交，Neo4j 异步投影。导入文件通过[作者导入](author-import.md)处理，不调用已退役的网络采集 API。

Batch/Quartz 通用配置已归入 `infrastructure`，图谱维护使用 `batchJobOperator`。`crawl` 只保留两个旧调度类名的自清理实现；`ingestion` 只维护原始 Payload 到期清理；`source` 只保留当前来源类型和指标模型。不得把这些兼容用途误认为恢复了采集入口。ORCID 兼容边界见 [author-orcid.md](author-orcid.md)。

清理的退役实现不包含数据库表、旧审计动作、权限编码、来源指标和已存在的合并关系。验证清理时必须保留目录、统计、图谱、导出及历史证据读取能力。

## 页面、登录与日志

当前全系统固定浅色。`frontend/index.html`、`useTheme.ts` 和 `src/styles` 共同提供主题，图表与图谱使用相同色值。路由及导航以 `src/router/index.ts`、`src/config/nav.ts` 为准。

登录、会话过期和退出由业务前端处理，网关转换已有 Cookie，后端执行实时权限与 CSRF 校验。账号管理 `/crawler/users`、操作/登录日志 `/crawler/logs`。旧 `/crawler/overview/activity` 返回工作台；旧运维、管理日志及请求日志入口转到日志页并检查权限。已移除的请求日志 API 不再写入磁盘，历史文件保持原状。

更新前端源码后，Demo 必须重新生成正式 `frontend/dist`；仅构建到临时目录不会更新当前页面。后端 JAR 或网关代码变更需按原模式停止并重启相应组件后生效，源码检查不等于运行验收。

## 测试与检查

在仓库根目录执行：

```powershell
npm.cmd --prefix systems/crawler/frontend test
npm.cmd --prefix systems/crawler/frontend run build -- --base=/crawler/
node --test scripts/tests/*.test.mjs scripts/lib/source-adaptations.test.mjs
node scripts/check-source.mjs
```

后端测试在 `systems/crawler/backend` 执行 `..\mvnw.cmd -o -B test`，已有依赖不足时需按正常依赖准备流程恢复，不能把离线缺失依赖当成代码失败。数据库集成测试使用独立 Testcontainers MySQL/Neo4j，要求可用的 Docker Linux Engine；不对业务库运行测试。浏览器回归使用 `npm.cmd --prefix systems/crawler/frontend run test:e2e`，需要 Edge 和空闲的 4173 端口。

后端验证应覆盖作者导入与导师编目、图谱维护与投影、共享 Batch/Quartz 配置、旧任务清理、原始 Payload 清理及迁移。清理测试必须保证过期 Payload 被有界处理，同时保留哈希和成果证据关联。当前接口契约由 `OpenApiDocumentTests` 检查。

构建后若出现过时类残留，先停止持有正式输出的应用，再执行 Maven `clean test`；也可以在独立构建目录验证源码，避免影响已运行的产物。测试结果及真实运行验证的区别见 [验证指南](crawler-acceptance.md)。

## 来源同步

`docs/import-records.json` 保存不可变来源树及导入证据，`docs/source-adaptations.json` 记录当前 crawler 的逐文件差异。`node scripts/check-source.mjs` 校验来源、祖先及文件哈希；来源文件删除使用 `adaptedBlob: null`，新增后又删除且不再存在的文件不能保留过期记录。

不修改来源 SHA 或校验规则来绕过错误。只同步本次授权修改的文件，其他未提交改动若导致全量校验失败，应单独报告，不自动接受到适配清单。

新克隆可能缺少已退役 scholar 的精确来源对象。需要恢复时，在仓库根目录执行：

```powershell
git -c http.sslBackend=openssl fetch --no-tags --no-recurse-submodules --no-write-fetch-head --no-auto-maintenance origin f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d
git cat-file -t f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d
git rev-parse 'f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d^{tree}'
node scripts/check-source.mjs
```

预期类型为 `commit`，树为 `8c060254a4ddd2fd485c3bbe2824bdd2c9af1353`。这只补回来源证据，不恢复退役源码或改写 Git 历史；仍依赖远端保留对应对象。提交、推送、部署及业务数据变更需另有任务授权。
