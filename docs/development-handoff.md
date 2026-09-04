# AACV System 开发环境迁移交接手册

## 1. 文档目的

本文用于将当前 `AACV_System` 项目通过压缩包迁移到另一台 Windows 电脑，并在新电脑上继续本地开发。

本文只覆盖开发环境交接，不代表服务器部署、生产发布或阶段 8 验收。建议先迁移源码并建立全新本地数据库；只有确实需要保留当前业务数据时，才按本文的“可选数据迁移”章节单独处理数据库备份。

文档核对日期：2026-09-04。

在新电脑解压并用 Codex 打开项目后，可直接使用[新电脑 Codex 接管提示词](./new-computer-handoff-prompt.md)完成环境恢复、启动和验证。

## 2. 当前项目状态

### 2.1 已实现范围

项目当前已完成开发计划阶段 0 至阶段 7，主要包括：

- Spring Boot 后端、Vue 3 前端和本地开发基础设施；
- 基于会话、CSRF、角色和权限的身份安全能力；
- OpenAlex、Crossref 双源采集、治理和质量指标；
- MySQL 权威数据、事务 Outbox 和 Neo4j 可重建图投影；
- 成果目录、局部图谱、统计分析、异步导出、系统内告警和运行监控。

阶段8正在实施：100,000条成果、413,000个图节点、1,000,000条图关系和四个HTTP P95场景已经实测通过；真实本地账号联合运行、受控故障复验和备份恢复演练尚未完成。服务器部署、生产HTTPS和公网发布不在当前范围内。

### 2.2 运行架构

| 组件 | 当前运行方式 | 默认地址或版本 | 说明 |
| --- | --- | --- | --- |
| 前端 | Node.js + Vite | `http://127.0.0.1:5173` | 将 `/api`、`/actuator` 代理到后端 |
| 后端 | JDK 21 + Maven Wrapper + Spring Boot | `http://127.0.0.1:8080` | REST API、调度、批处理和图同步 |
| MySQL | Windows 本机 MySQL80 服务 | `127.0.0.1:3306`，当前主机实测8.0.41；文档基线8.0.42 | 唯一业务权威数据源 |
| Neo4j | Docker Desktop Linux Engine | Neo4j `5.26-community` | 可从 MySQL 重建的图投影 |

MySQL 数据不在项目目录内，Neo4j 数据位于 Docker 命名卷内，二者都不会自动进入源码压缩包。

### 2.3 当前 Git 状态

2026-09-04 已配置远程 `origin` 为 `https://github.com/yzwyzw222/XSGXZSTP-KS.git`，当前本地分支为 `feature/Luo` 并跟踪 `origin/feature/Luo`。源码、文档、锁文件和 `.env.example` 已完成提交前跟踪边界核对；真实 `.env`、迁移 bundle 和校验产物保持本地忽略。以下内容保留为 2026-09-03 压缩包生成时的历史状态：

- 当前分支：`dev`；
- `dev` 与 `main` 均指向初始化提交 `d5243b8`；
- 未配置 Git 远端；
- `.gitattributes`、`.gitignore`、源码、文档和配置目前均为未跟踪文件。
- `.git` 当前含约 312.23 MiB 松散对象及大量不可达对象，可达历史只有上述初始化提交。

因此，不应直接把整个 `.git` 目录放进压缩包。本文使用 `git bundle --branches` 只保存可达的 `main`/`dev` 分支元数据，并明确排除原 `.git` 中的不可达对象。当前源码没有进入任何提交，其完整性仍依赖源码压缩包本身。迁移前后都必须执行以下命令并保存输出用于核对：

```powershell
git status --short --branch
git branch --show-current
git log --oneline --decorate -5
git remote -v
```

本次交接不要求也不执行 Git 提交、垃圾回收或不可达对象清理。若以后需要建立完整版本历史，应在确认无凭据、无大文件且变更范围正确后，由项目负责人单独决定是否提交。

## 3. 压缩包内容边界

### 3.1 必须携带

以下内容是继续开发所必需的：

- `AACV_System-history.bundle`：通过 `git bundle --branches` 单独生成，只保留可达的 `main`/`dev` 分支元数据；
- `.gitattributes`、`.gitignore`、`.env.example`；
- `.mvn/`、`mvnw`、`mvnw.cmd`：Maven Wrapper；
- `backend/pom.xml`、`backend/src/`：后端依赖、源码、迁移和测试；
- `frontend/package.json`、`frontend/package-lock.json`、`frontend/src/`、`frontend/e2e/` 及前端配置文件；
- `deploy/compose.yaml`：Neo4j 本地 Compose 配置；
- `README.md`、`docs/`：运行说明、设计、契约、权限矩阵、开发计划和验收记录。

### 3.2 不应携带

以下内容可以在新电脑重新生成，或者不应进入交接包：

| 路径或模式 | 原因 |
| --- | --- |
| `backend/target/` | Maven 构建产物，可重新构建 |
| `frontend/node_modules/` | 依赖目录体积大，应通过 `npm ci` 按锁文件重建 |
| `frontend/dist/` | 前端构建产物，可重新构建 |
| `frontend/test-results/`、`frontend/playwright-report/`、`frontend/coverage/` | 测试临时产物 |
| `.npm-cache/`、`*.log` | 本地缓存或日志 |
| `.env`、`.env.local`、`.env.*.local` | 可能包含真实凭据；已复核的 `.env.example` 必须纳入 |
| `backend/src/main/resources/application-local.yml`、`application-local.yaml` | 可能包含本机凭据或覆盖配置 |
| `images/neo4j-4.4.tar` | 约 315.62 MiB 的历史镜像，与当前 Neo4j 5.26 基线不一致 |
| `.idea/`、`.vscode/`、`*.iml` | 本机 IDE 状态，接手人可自行配置 |
| `.git/` | 当前约 312.23 MiB 且含大量不可达对象；改用只含可达分支的 Git bundle |

`.gitignore` 不会自动控制普通压缩工具的行为，所以打包时仍要显式排除这些路径。

### 3.3 必须单独处理的敏感数据

以下内容不得混入普通源码压缩包：

- MySQL 业务数据库备份；
- 数据库、Neo4j 或管理员密码；
- `.env`、本地配置覆盖、IDE 私有运行配置；
- API Key、访问令牌、Cookie、私钥、证书；
- 可能包含用户、审计或业务数据的日志和导出文件。

如需迁移这些内容，应使用独立加密介质或组织批准的安全传输方式，并限制访问权限。不要通过聊天、邮件正文、文档或源码提交传递密码。

## 4. 在原电脑制作源码压缩包

### 4.1 打包前检查

在项目根目录执行：

```powershell
Set-Location E:\Program\Java\AACV_System

git status --short --branch

Get-ChildItem -Force -Recurse -File -Include `
  .env,.env.*,application-local.yml,application-local.yaml,*.pem,*.key,*.p12,*.jks,*.keystore
```

如果第二条命令发现真实本地配置、证书或密钥文件，先确认它们不会进入压缩包。不要为了打包而删除仍需保留的本机文件。

### 4.2 推荐打包命令

先在项目根目录生成只包含可达分支历史的 Git bundle，并进行校验：

```powershell
Set-Location E:\Program\Java\AACV_System

$bundlePath = 'E:\Program\Java\AACV_System-history.bundle'
git bundle create $bundlePath --branches
git bundle verify $bundlePath
```

再从项目的上一级目录制作压缩包。该命令显式排除原 `.git`、可重建产物、本地凭据配置及历史 Neo4j 4.4 镜像，同时把 Git bundle 一并放入压缩包：

```powershell
Set-Location E:\Program\Java

$bundlePath = Join-Path (Get-Location) 'AACV_System-history.bundle'
$archivePath = Join-Path (Get-Location) 'AACV_System-handoff-20260903.zip'

tar.exe -a -c -f $archivePath `
  --exclude='AACV_System/.git' `
  --exclude='AACV_System/backend/target' `
  --exclude='AACV_System/frontend/node_modules' `
  --exclude='AACV_System/frontend/dist' `
  --exclude='AACV_System/frontend/coverage' `
  --exclude='AACV_System/frontend/test-results' `
  --exclude='AACV_System/frontend/playwright-report' `
  --exclude='AACV_System/.npm-cache' `
  --exclude='AACV_System/.idea' `
  --exclude='AACV_System/.vscode' `
  --exclude='AACV_System/frontend/.vscode' `
  --exclude='AACV_System/.env' `
  --exclude='AACV_System/.env.local' `
  --exclude='AACV_System/.env.*.local' `
  --exclude='AACV_System/backend/.env' `
  --exclude='AACV_System/backend/.env.local' `
  --exclude='AACV_System/backend/.env.*.local' `
  --exclude='AACV_System/frontend/.env' `
  --exclude='AACV_System/frontend/.env.local' `
  --exclude='AACV_System/frontend/.env.*.local' `
  --exclude='AACV_System/backend/src/main/resources/application-local.yml' `
  --exclude='AACV_System/backend/src/main/resources/application-local.yaml' `
  --exclude='AACV_System/images/neo4j-4.4.tar' `
  --exclude='*.log' `
  AACV_System-history.bundle `
  AACV_System
```

不要使用 `Compress-Archive .\AACV_System\*` 作为首选方案，因为它不会自动按 `.gitignore` 排除依赖、构建产物和凭据文件。也不要直接复制当前 `.git`；Git bundle 已保存有效的分支历史。

### 4.3 校验压缩包

```powershell
$entries = tar.exe -tf $archivePath

$entries | Select-String -Pattern 'AACV_System-history.bundle|README.md|docs/development-handoff.md|backend/pom.xml|frontend/package-lock.json|deploy/compose.yaml'

$forbiddenEntries = $entries | Where-Object {
  $_ -match '(^|/)(node_modules|target|dist|coverage|test-results|playwright-report)(/|$)' -or
  $_ -match '(^|/)\.git(/|$)' -or
  (($_ -match '(^|/)\.env($|\.)') -and ($_ -notmatch '(^|/)\.env\.example$')) -or
  $_ -match 'application-local\.ya?ml$' -or
  $_ -match 'neo4j-4\.4\.tar$' -or
  $_ -match '\.(pem|key|p12|jks|keystore)$'
}

if ($forbiddenEntries) {
  $forbiddenEntries
  throw '压缩包包含不应交接的文件，请修正排除规则后重新打包。'
}

Get-Item -LiteralPath $archivePath | Select-Object FullName,Length,LastWriteTime
Get-FileHash -LiteralPath $archivePath -Algorithm SHA256
```

将 SHA-256 值通过与压缩包不同的可信渠道交给接手人。新电脑必须重新计算并比对；不一致时不要解压使用。

## 5. 新电脑环境准备

### 5.1 软件要求

| 软件 | 要求 | 说明 |
| --- | --- | --- |
| Windows | Windows 10/11 开发环境 | 当前项目命令以 PowerShell 为基准 |
| Git | 可用的稳定版本 | 用于从随包携带的 Git bundle 重建有效分支历史 |
| JDK | JDK 21 | 当前后端编译目标为 Java 21 |
| Maven | 无需全局安装 | `mvnw.cmd` 会使用 Maven 3.9.16 |
| Node.js | `20.19+`、`22.12+` 或更高兼容版本 | 当前原电脑为 Node.js 24.14.0、npm 11.9.0 |
| Docker Desktop | Linux Engine | 用于 Neo4j 5.26 和后端 Testcontainers 测试 |
| MySQL | MySQL Community Server 8.0.42 | 服务名通常为 `MySQL80`，默认端口 3306 |
| Microsoft Edge | 与 Playwright 兼容 | E2E 配置固定使用 `msedge` channel |

Maven、npm、Docker 镜像以及 OpenAlex/Crossref 在线联调均需要相应网络访问。若新电脑处于离线环境，应另行准备 Maven/npm 缓存和 Neo4j 5.26 镜像；历史 `neo4j-4.4.tar` 不能替代当前镜像。

建议开发机至少为 4 核 CPU、8 GB 内存和 SSD。运行完整后端 Testcontainers 测试时，Docker 还会临时启动 MySQL 8.0.42 和 Neo4j 5.26 容器。

### 5.2 环境检查命令

```powershell
git --version
java -version
node --version
npm --version
docker version
docker compose version
Get-Service -Name MySQL80
```

`docker version` 必须同时显示 Client 和 Server。只有 Client、没有 Server，通常表示 Docker Desktop 或 Linux Engine 尚未启动。

## 6. 解压与源码核对

以下示例将项目解压到 `D:\Program\Java`，可根据新电脑磁盘调整，但不要解压到临时目录或会自动同步、自动改写文件的目录。

```powershell
$archivePath = 'D:\Transfer\AACV_System-handoff-20260903.zip'
$expectedSha256 = '由原电脑校验结果提供'

$actualSha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash
if ($actualSha256 -ne $expectedSha256) {
  throw '压缩包 SHA-256 不一致，停止解压。'
}

New-Item -ItemType Directory -Path 'D:\Program\Java' -Force
tar.exe -xf $archivePath -C 'D:\Program\Java'
Set-Location 'D:\Program\Java\AACV_System'
```

解压后先核对源码和 Git bundle：

```powershell
Test-Path .\README.md
Test-Path .\docs\development-handoff.md
Test-Path .\backend\pom.xml
Test-Path .\frontend\package-lock.json
Test-Path .\deploy\compose.yaml
Test-Path ..\AACV_System-history.bundle
Test-Path .\.git
```

最后一项预期为 `False`。从只含可达历史的 bundle 重建 Git 元数据：

```powershell
git init -b handoff-import
git fetch ..\AACV_System-history.bundle "refs/heads/*:refs/heads/*"
git switch dev

git status --short --branch
git branch --show-current
git log --oneline --decorate -5
git remote -v
```

预期重建后处于 `dev` 分支，`dev`/`main` 指向 `d5243b8`，项目主体显示为未跟踪文件，且 `git remote -v` 没有输出。临时名称 `handoff-import` 是未出生分支，不会形成额外提交或分支引用。若结果不同，先与原电脑核对，不要执行 `git reset --hard`、`git clean` 或其他会丢失文件的命令。

## 7. 初始化本地数据库与配置

### 7.1 MySQL

默认方案是在新电脑建立空的 `aacv_system` 数据库，由后端启动时通过 Flyway V1 至 V11 创建结构。使用具备建库权限的账号进入 MySQL 后执行：

```sql
CREATE DATABASE aacv_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

不要把 MySQL 密码写入源码、脚本、命令参数、`.env.example` 或共享 IDE 配置。真实本地配置只允许写入受 `.gitignore` 保护的根目录 `.env`，并应限制该文件的本机访问权限。

### 7.2 根目录 `.env`

在项目根目录创建本地配置：

```powershell
Copy-Item .\.env.example .\.env
notepad .\.env
```

至少替换数据库和 Neo4j 的 `change-me-before-use`。Compose 与后端的 Neo4j 用户名和密码必须一致。`.env` 使用兼容 Spring Boot Properties 和 Docker Compose 的 `KEY=value` 格式，不使用 `export` 前缀，不得提交、分享、输出或放入同步盘。JDK 的 `JAVA_HOME` 仍属于本机工具配置，不写入项目 `.env`。

`application.yml`通过`spring.config.import`检查当前工作目录和上一层目录中的`.env`，以兼容Maven插件使用`backend`作为工作目录的情况；日常命令仍从项目根目录执行。Neo4j Driver连接、连接池获取和事务重试默认限制为5秒，分别由`AACV_NEO4J_CONNECTION_TIMEOUT`、`AACV_NEO4J_CONNECTION_ACQUISITION_TIMEOUT`和`AACV_NEO4J_MAX_TRANSACTION_RETRY_TIME`配置。该边界用于保证Neo4j不可用时图请求有界失败；不要为了掩盖依赖故障而调大或禁用超时。

### 7.3 首次管理员

仅当新数据库的 `sys_user` 为空时，才可在首次启动前把 `.env` 中的 `AACV_BOOTSTRAP_ADMIN_ENABLED` 临时改为 `true`，并填写管理员用户名和密码。

密码长度必须为 12 至 128 位，不能只包含空白或包含控制字符。管理员创建成功后停止后端，将 `AACV_BOOTSTRAP_ADMIN_ENABLED` 恢复为 `false`、清空 `AACV_BOOTSTRAP_ADMIN_PASSWORD`，再重新启动后端。

## 8. 安装依赖与启动

### 8.1 安装前端依赖

在项目根目录执行：

```powershell
npm --prefix .\frontend ci
```

必须使用 `npm ci` 和现有 `package-lock.json`，不要在迁移过程中随意升级依赖或重写锁文件。

### 8.2 启动 Neo4j

确保根目录 `.env` 已填写 `NEO4J_USERNAME` 和 `NEO4J_PASSWORD`：

```powershell
docker compose --env-file .\.env -f .\deploy\compose.yaml config --quiet
docker compose --env-file .\.env -f .\deploy\compose.yaml up -d neo4j
docker compose --env-file .\.env -f .\deploy\compose.yaml ps
```

预期 Neo4j Browser 为 `http://127.0.0.1:7474`，Bolt 为 `127.0.0.1:7687`。

### 8.3 启动后端

打开 PowerShell 并从项目根目录启动，后端会加载根目录 `.env`：

```powershell
Set-Location 'D:\Program\Java\AACV_System'
.\mvnw.cmd -f .\backend\pom.xml spring-boot:run
```

首次运行会下载 Maven 3.9.16 和后端依赖。后端启动时会运行 Flyway V1 至 V11，但不会隐式为既有成果生成图投影事件。

### 8.4 启动前端

在另一个 PowerShell 窗口执行：

```powershell
Set-Location 'D:\Program\Java\AACV_System'
npm --prefix .\frontend run dev
```

浏览器访问 `http://127.0.0.1:5173/login`。

## 9. 迁移后验证

### 9.1 健康检查

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/liveness
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/readiness
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/graph
```

预期 liveness、readiness 和 graph 均为 `UP`。Neo4j 异常不会使 MySQL 目录和统计功能整体下线，但 graph 健康状态会单独反映异常。

### 9.2 自动化验证

先启动 Docker Desktop Linux Engine，再依次执行：

```powershell
.\mvnw.cmd -f .\backend\pom.xml verify
npm --prefix .\frontend run test
npm --prefix .\frontend run build
npm --prefix .\frontend run test:e2e
docker compose --env-file .\.env -f .\deploy\compose.yaml config --quiet
```

说明：

- 后端完整测试使用独立的 MySQL 8.0.42 和 Neo4j 5.26 Testcontainers，不会清理或覆盖本机 `aacv_system`；
- E2E 测试固定使用本机 Microsoft Edge，并以单 worker 运行；
- 当前阶段 7 基线曾验证 159 项后端测试、24 项前端 Vitest、生产构建和 7 项 Edge 流程，但新电脑必须重新执行，不能直接沿用原电脑结果；
- 在线 OpenAlex/Crossref 联调不是确定性构建的必需条件，是否运行应根据新电脑网络条件另行确认。

### 9.3 最小人工检查

1. 使用一次性管理员或迁移后的已有账号登录；
2. 打开成果目录、统计和运行监控页面；
3. 确认无数据时显示正常空状态，而不是 500 错误；
4. 检查管理员用户管理、数据源和采集任务页面的权限；
5. 若 MySQL 已有业务数据，检查图同步状态，并按需执行受控初始回填；
6. 不要把路由模拟的 Playwright 测试视为真实账号、真实持久化数据库联合验收。

## 10. 可选：迁移现有 MySQL 业务数据

只有需要保留当前采集数据、用户、审计和任务状态时才执行本节。数据库备份包含敏感业务信息和密码哈希，必须与源码压缩包分开保存、加密传输并限制访问。

### 10.1 原电脑导出

在安全目录中执行，使用 `-p` 让 MySQL 客户端交互式询问密码：

```powershell
mysqldump.exe -u root -p `
  --single-transaction `
  --routines `
  --triggers `
  --events `
  --default-character-set=utf8mb4 `
  --result-file=E:\AACV_System_Backups\aacv_system-handoff-20260903.sql `
  aacv_system

Get-FileHash -LiteralPath E:\AACV_System_Backups\aacv_system-handoff-20260903.sql -Algorithm SHA256
```

导出前应确认 `E:\AACV_System_Backups` 位于受控位置并有足够空间。不要将 SQL 文件放入项目目录或 Git 仓库。

### 10.2 新电脑恢复

只恢复到已确认的空开发数据库。目标数据库已有数据时，先停止并评审，避免重复数据或不可逆覆盖。

```powershell
mysql.exe -u root -p
```

进入 MySQL 客户端后执行：

```sql
USE aacv_system;
SOURCE D:/SecureTransfer/aacv_system-handoff-20260903.sql;
```

恢复后启动后端，确认 Flyway 校验通过，再执行健康检查和数据抽样。不要修改或重排已经应用的 V1 至 V11 迁移。

### 10.3 Neo4j 数据处理

默认不迁移 Neo4j Docker 卷。Neo4j 只是 MySQL 的可重建投影；恢复 MySQL 后，通过管理员运行监控中的图维护入口执行受控初始回填或全量重建。全量重建必须使用确认值 `REBUILD_AACV_MANAGED_GRAPH`，且只能删除 `aacvManaged=true` 的系统受管投影。

不要为了处理认证或迁移问题直接执行 `docker compose down -v`，该命令会删除 Neo4j 命名卷。只有明确确认不需要卷内数据时才可执行破坏性清理。

导出目录中的 CSV/JSON 是 24 小时临时文件，不是业务备份，不需要随项目迁移。

## 11. 常见问题

### 11.1 `Could not find a valid Docker environment`

Docker Desktop 或 Linux Engine 未启动，或者当前终端无权访问 Docker 命名管道。先执行 `docker version`，确认 Client 和 Server 均可用，再原样重试测试；不要通过删除 Testcontainers 测试绕过问题。

### 11.2 后端提示缺少 `AACV_DB_USERNAME` 或 `AACV_DB_PASSWORD`

根目录 `.env` 不存在、仍含占位值，或后端不是从项目根目录启动。重新执行第 7.2 节，不要把密码写入 `application.yml` 或 `.env.example`。

### 11.3 Neo4j 认证失败

确认 Compose 使用的 `NEO4J_USERNAME`/`NEO4J_PASSWORD` 与后端使用的 `AACV_NEO4J_USERNAME`/`AACV_NEO4J_PASSWORD` 一致。已有 Neo4j 卷会保留首次初始化密码，不会因为修改环境变量自动重置。

### 11.4 `Error: spawn EPERM`

这通常是受限执行上下文不允许创建子进程。请在新电脑的普通本地 PowerShell 中原样重试，不要修改测试或构建配置掩盖失败。

### 11.5 端口被占用

```powershell
Get-NetTCPConnection -LocalPort 3306,7474,7687,8080,5173 -ErrorAction SilentlyContinue
```

默认端口被占用时，先确认占用进程和现有服务用途。不要随意结束未知进程。后端地址和端口可分别通过 `AACV_SERVER_ADDRESS`、`AACV_SERVER_PORT` 覆盖；修改前还要确认前端代理配置是否需要同步。

### 11.6 Maven、npm 或 Docker 下载失败

确认新电脑可访问 Maven Central、npm Registry 和 Docker Hub，或按组织代理规范配置。不要删除锁文件、随意切换依赖版本，也不要用 Neo4j 4.4 历史镜像替代 5.26。

## 12. 接手开发前的必读资料

建议按以下顺序阅读：

1. [项目 README](../README.md)：本地配置、启动、接口和停止命令；
2. [需求分析](./requirements-analysis.md)：功能与非功能需求边界；
3. [系统设计](./system-design.md)：架构、数据一致性、安全和恢复原则；
4. [开发计划](./development-plan.md)：阶段状态、实施批次和阶段 8 未完成内容；
5. [OpenAPI 契约](./openapi.yaml)：当前 API 契约；
6. [权限矩阵](./authorization-matrix.md)：角色与操作权限；
7. [阶段 7 验收记录](./stage7-acceptance.md)：最近一次完整回归证据及已知边界。
8. [新电脑 Codex 接管提示词](./new-computer-handoff-prompt.md)：新电脑解压后可直接复制使用的接管指令。
9. [阶段 8 验收记录](./stage8-acceptance.md)：本轮新证据、隔离工具和剩余门禁。
10. [备份与恢复说明](./backup-and-recovery.md)与[已知限制](./known-limitations.md)：暂缓决策和不可误报的边界。

继续开发时，应先重新读取这些文档并核对当前代码、迁移、依赖和 Git 状态。历史验收结果只能说明当时环境，不替代新电脑上的实际验证。

### 12.1 阶段8后续执行入口

必须在普通本地PowerShell中执行以下脚本，并在每个`Get-Credential`窗口由用户本人输入凭据。不要把密码改成参数、脚本常量或`.env`文件。阶段8 PowerShell脚本使用UTF-8 BOM保存，已验证可由Windows PowerShell 5.1和PowerShell 7解析；后续编辑时必须保留BOM，避免中文字符串被错误代码页破坏。

~~~powershell
.\tools\stage8\Start-Stage8Environment.ps1
.\tools\stage8\Initialize-Stage8Capacity.ps1
.\tools\stage8\Measure-Stage8Performance.ps1
.\tools\stage8\Restart-Stage8Backend.ps1
.\tools\stage8\Test-Stage8FailureRecovery.ps1 -ConfirmFaultInjection
.\tools\stage8\Invoke-Stage8RealSourceAcceptance.ps1 -ConfirmExternalRequests
.\tools\stage8\New-Stage8DatabaseBackup.ps1 -InitializeBackupRoot -ApplyRetention
.\tools\stage8\Test-Stage8BackupRecovery.ps1 -ConfirmIsolatedRestore
~~~

前三个脚本使用固定隔离数据库`aacv_stage8_capacity_20260903`和固定阶段8 Docker卷。容量脚本只在全部容量表为空且辅助序列表不存在时执行全量写入；若MySQL或Neo4j计数完整匹配则安全复用。MySQL仅允许从“成果引用和图投影状态同时缺失”或“仅图投影状态缺失”两个精确尾部状态通过事务续写，其他部分或不一致状态都会停止且不会清空或覆盖。容量引用同时写入V8要求的`referenced_id_type`和`referenced_id_value`，并保留兼容字段`referenced_external_work_id`。故障脚本只停止并恢复`aacv-stage8-mysql`与`aacv-stage8-neo4j`，不会执行`down -v`。真实来源脚本默认复用已经启用的来源；只有明确增加`-CreateMissingSources`才创建缺失来源，每个来源默认1页、20条，上限仍为5页、500条。

备份脚本只接受固定仓库外目录`E:\AACV_System_Backups`。首次使用`-InitializeBackupRoot`创建目录并限制ACL；`-ApplyRetention`才会删除超出7个每日、4个每周配额的旧SQL及其SHA-256和元数据旁车。恢复脚本只接受该目录中的已校验SQL，并使用`aacv-stage8-recovery-mysql`、`aacv-stage8-recovery-neo4j`、独立命名卷和23306/27474/27687/28080端口；发现非空目标或28080端口冲突立即停止，清理时只停止本次脚本进程树中的监听进程，不自动清空或删除恢复资产。

容量初始化已于2026-09-04实际完成并校验100,000条成果、413,000个图节点和1,000,000条图关系。性能工具在固定并发4下完成三个目录/详情场景各1,000样本和局部图500样本，四项P95均通过，原始证据见`docs/stage8-performance-evidence.json`。随后一次重复测量因Docker Desktop退出而在CSRF前置请求处超时；重启Docker Desktop并直接启动原容器后，命名卷保留、两个容器约11秒恢复健康，后端健康组与CSRF恢复。该非预期退出原因尚未确定，必须继续受控可靠性演练，不能把恢复现象扩张为阶段8通过。

受控Neo4j故障演练实际证明旧运行态的认证图查询45秒仍不返回。公共PowerShell请求封装已修复无`Response`网络异常的严格模式二次错误；后端`application.yml`已为Neo4j Driver连接、连接池获取和事务重试增加5秒默认上限，配置绑定单元测试1项通过。必须先运行`Restart-Stage8Backend.ps1`加载新配置，再重跑故障演练；在两步取得实际通过证据前，不得宣称可靠性门禁通过。

业务备份、隔离恢复和RPO/RTO实测尚未执行；备份静态加密不属于本地阶段8验收项。不得把24小时导出文件视为业务备份，备份仍须限制访问、生成并验证校验和，且凭据不得落盘。当前证据和剩余门禁见[阶段8验收记录](./stage8-acceptance.md)。

## 13. 交接验收清单

### 原电脑

- [ ] 已检查 Git 状态并记录当前分支、提交和未跟踪文件；
- [ ] 已用 `git bundle --branches` 生成并校验 `AACV_System-history.bundle`；
- [ ] 压缩包包含 Git bundle、源码、锁文件、Maven Wrapper、Compose 和全部文档；
- [ ] 压缩包不包含原 `.git`、凭据、本地配置覆盖、依赖目录、构建产物、日志和 Neo4j 4.4 历史镜像；
- [ ] 已生成并单独保存压缩包 SHA-256；
- [ ] 如需业务数据，MySQL 备份已单独加密传输；
- [ ] 未把 Neo4j 卷或临时导出文件误当作权威业务备份。

### 新电脑

- [ ] 压缩包 SHA-256 与原电脑一致；
- [ ] 解压后 Git 状态、分支和提交与交接记录一致；
- [ ] JDK 21、Node.js、Docker Desktop、MySQL80 和 Edge 可用；
- [ ] `npm ci` 成功，未修改 `package-lock.json`；
- [ ] MySQL 数据库和根目录 `.env` 已正确配置；
- [ ] Neo4j、后端和前端能够按顺序启动；
- [ ] 三个健康检查符合预期；
- [ ] 后端、前端、构建、E2E 和 Compose 验证已在新电脑实际执行并记录结果；
- [ ] 已确认阶段 8、真实账号联合运行、备份恢复演练和服务器部署仍未因本次迁移自动完成。
