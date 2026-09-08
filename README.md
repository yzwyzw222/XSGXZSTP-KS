# AACV System

**学术成果爬虫与可视化系统**，面向科研用户、数据运营人员和管理员，支持从 OpenAlex、Crossref 采集学术成果，完成规范化入库、数据治理、成果检索、统计分析和知识图谱浏览。

项目采用前后端分离架构：MySQL 保存业务数据，Neo4j 保存可重建的图投影，Vue 前端提供统一工作台。本文以 **Windows 本地开发环境**为启动基线。

> 当前完整实现位于 **feature/Luo** 分支。远端默认分支是 main，请按下方命令指定分支克隆。

## 项目功能

界面按“研究工作、数据管理、管理工具”组织，菜单和操作按账号权限开放。

| 模块 | 功能 |
| --- | --- |
| 工作台 | 查看当前筛选范围的成果、作者、机构和来源数量，以及年度发表趋势；支持图表与数据切换。 |
| 成果目录 | 按题名、作者、机构、年份、类型、来源、载体和主题组合查询；查看署名、引用、字段来源、人工修正和双源学术指标。 |
| 实体编目 | 查询作者、机构、期刊和主题；查看关联成果，以及可用的机构名称来源证据。 |
| 知识图谱 | 自动加载作者与作品的受限概览；支持缩放、拖动、节点详情、两跳子图、合作作品、节点/关系表格、高级查询、路径分析和本机常用查询。 |
| 图谱类型管理 | 维护固定实体类型和关系类型的显示名称、颜色、尺寸及审核状态。 |
| 统计分析 | 查看发表趋势、字段覆盖率、成果类型、来源、机构、主题分布及作者/机构合作排行。 |
| 数据源与采集任务 | 配置和探测 OpenAlex、Crossref；创建一次性采集、固定范围每日复查，查看运行与失败记录，执行暂停、恢复、取消和有限重试。 |
| 数据治理与质量 | 审核重复候选、合并与受控撤销、人工修正字段；查看质量指标并定位问题样本。 |
| 数据导出 | 按成果目录筛选创建 CSV/JSON 异步导出，查询状态并下载结果。 |
| 账号与日志 | 创建账号、维护资料与角色、启停账号、重置密码；查看账号分布、操作日志和登录日志。 |

内置三类角色：系统管理员 `ADMIN`、数据运营人员 `DATA_OPERATOR`、科研用户 `RESEARCHER`。后端统一执行权限和 CSRF 校验；系统没有公开注册入口或预设的通用登录密码。

### 当前能力边界

- 每个采集任务最多 **5 页、500 条**。每日计划复查既定范围，不代表全量采集或自动推进全库水位。
- 图谱概览默认最多 **300 个节点**；当前结果中的筛选、合作证据和统计不等于全库统计。类型配置管理不等于直接新增、删除业务节点或关系。
- MySQL 是业务数据的权威来源，Neo4j 经事务 Outbox 异步投影，刚入库的数据可能稍后才出现在图谱中。
- 单次导出最多 **10,000 条**。不同来源的被引量分别展示，不相加；缺失指标保留未知状态。
- 研究界面保留操作/登录日志，旧运行监控入口转到日志页。后端运维和告警接口仍保留。
- 默认配置面向本机开发，未提供可直接用于公网的 HTTPS、反向代理和生产部署编排。

## 技术栈

实际依赖以 [后端 POM](backend/pom.xml)、[前端依赖清单](frontend/package.json) 和 [前端锁文件](frontend/package-lock.json) 为准。

| 层次 | 主要技术 | 用途 |
| --- | --- | --- |
| 后端基础 | Java 21、Spring Boot 4.1.1、Maven Wrapper 3.9.16 | REST API、应用配置和构建 |
| 数据访问 | MyBatis Spring Boot 4.1.0、MySQL、Flyway | 业务持久化、SQL 映射和版本化迁移 |
| 图数据 | Neo4j 5.26 Community、Spring Data Neo4j | 图投影、子图和路径查询 |
| 任务与会话 | Spring Batch、Quartz、Spring Session JDBC | 批量采集、持久化调度和服务端会话 |
| 安全与运行状态 | Spring Security、Bean Validation、Actuator | 权限、CSRF、输入校验和健康检查 |
| 前端基础 | Vue 3.5.42、TypeScript 6.0.2、Vite 8.2.2、Vue Router 5.3.0 | 单页应用、类型检查和开发服务 |
| 界面与状态 | Element Plus 2.14.5、Tailwind CSS 4.3.3、Pinia 3.0.4、Axios 1.20.0 | 业务组件、样式、会话状态和 HTTP 请求 |
| 图表与图谱 | ECharts 6.1.0、vis-network 9.1.9 / vis-data 7.1.9、Cytoscape.js 3.34.2 | 统计图表、概览画布、高级查询与路径画布 |
| 校验与工具 | Zod、vee-validate、VueUse、Lucide | 表单校验、响应式工具和图标 |
| 自动化验证 | Spring Boot Test、Testcontainers、Vitest、Vue Test Utils、Playwright | 后端集成、前端单元和浏览器测试 |

## 项目结构

~~~text
AACV_System/
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/aacv/system/   # 按业务模块组织的后端代码
│     │  └─ resources/
│     │     ├─ application.yml     # 应用配置，引用本地环境变量
│     │     ├─ db/migration/       # Flyway V1 至 V15
│     │     ├─ mapper/             # MyBatis XML 映射
│     │     └─ neo4j/schema/       # 图数据库约束与索引
│     └─ test/                    # 后端测试与夹具
├─ frontend/
│  ├─ package.json
│  ├─ package-lock.json
│  ├─ vite.config.ts
│  ├─ public/                     # 图标等静态资源
│  ├─ src/
│  │  ├─ components/              # 公共与业务组件
│  │  ├─ views/                   # 业务页面
│  │  ├─ layouts/                 # 侧栏、顶栏和页面外壳
│  │  ├─ router/                  # 路由及权限守卫
│  │  ├─ config/                  # 导航等前端配置
│  │  ├─ stores/                  # Pinia 状态
│  │  ├─ services/                # HTTP 和业务接口
│  │  ├─ composables/             # 可复用的组合式逻辑
│  │  ├─ styles/                  # 主题令牌与全局样式
│  │  └─ types/、utils/           # 类型定义与工具函数
│  └─ e2e/                        # Playwright 浏览器用例
├─ deploy/
│  ├─ compose.yaml                # 日常开发：仅包含 Neo4j
│  └─ compose.stage8*.yaml         # 隔离容量/恢复验证，不用于日常启动
├─ tools/
│  ├─ development/                # 环境预检、启停及可选样例数据
│  └─ stage8/                     # 隔离环境的容量、备份及恢复验证工具
├─ docs/openapi.yaml              # 随仓库提供的接口契约
├─ .mvn/wrapper/                  # Maven 下载与版本配置
├─ .env.example                  # 配置模板，无真实凭据
├─ mvnw / mvnw.cmd               # Maven 入口
├─ start.bat / stop.bat          # Windows 一键启停
└─ README.md
~~~

后端主要模块包括：`identity`（账号与权限）、`source`（外部来源）、`crawl`（采集调度）、`ingestion`（解析入库）、`catalog`（成果目录）、`governance`（治理）、`quality`（质量）、`graph`（图投影与查询）、`analytics`（统计）、`export`（导出）、`operations`（日志与运维）。模块内通常按 `api → application → domain / port → infrastructure` 分层。

克隆不会包含真实 `.env`、MySQL/Neo4j 业务数据、`node_modules`、构建产物或本地验收资料。接口字段可查阅 [OpenAPI](docs/openapi.yaml)。

## 从克隆到启动

### 1. 准备环境

| 工具 | 本文使用的环境与要求 |
| --- | --- |
| Git | 可访问本仓库，用于克隆源码 |
| Windows + PowerShell | 一键启停脚本使用 Windows PowerShell 5.1 |
| JDK | JDK 21；`JAVA_HOME` 和 `PATH` 应指向同一安装 |
| Node.js + npm | 使用 Node.js 24.x；当前本机验证版本为 24.14.0。其他版本须满足锁文件中所有工具的 engines 要求 |
| MySQL Server | 项目兼容基线为 MySQL 8.0.42，准备本机 3306 端口及空业务库 |
| Docker Desktop | 已启动，并使用 Linux containers / Linux Engine；Docker Compose 可用 |
| MySQL 客户端 | 用于建库；可选样例数据脚本还要求 `mysql.exe` 在 PATH 中 |

Maven 由仓库 Wrapper 提供，无需另外安装。首次运行会下载 Maven 和 Java 依赖；`npm ci` 和首次启动 Neo4j 也需要访问相应包仓库或镜像仓库。一键启动不会替你安装软件、创建 MySQL 数据库或启动 Docker Desktop。

在 PowerShell 中核对工具：

~~~powershell
git --version
java --version
javac --version
node --version
npm.cmd --version
docker compose version
docker info --format '{{.OSType}}'
~~~

最后一项应输出 `linux`。如果修改过 `JAVA_HOME` 或 `PATH`，请重新打开终端。

### 2. 克隆当前完整分支

在你自己的开发目录执行：

~~~powershell
git clone --branch feature/Luo --single-branch https://github.com/yzwyzw222/XSGXZSTP-KS.git AACV_System
cd AACV_System
~~~

后续命令均从这个项目根目录执行。优先使用较短的本地目录；后端启动脚本要求生成的 `.local/java-sockets` 路径不超过 80 个 UTF-8 字节，过长会明确报错。

### 3. 创建 MySQL 数据库与应用账号

先启动本机 MySQL 服务，使用有建库、建账号权限的管理账号连接。例如在 PowerShell 中执行下列命令，按提示输入密码，不把密码写在命令行：

~~~powershell
mysql --host=127.0.0.1 --port=3306 --user=root --password
~~~

在 MySQL 会话中，为全新开发环境执行：

~~~sql
CREATE DATABASE aacv_system
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'aacv'@'127.0.0.1'
  IDENTIFIED BY '<YOUR_LOCAL_DB_PASSWORD>';

GRANT ALL PRIVILEGES ON aacv_system.* TO 'aacv'@'127.0.0.1';
~~~

**`<YOUR_LOCAL_DB_PASSWORD>` 只是占位符，必须在本机替换为你自己的密码。** 应用账号只授权给本项目数据库；首次启动需要执行 Flyway 建表和迁移，因此需要该库的 DDL 与数据读写权限。也可通过本机数据库管理工具创建账号、设置密码并授权，不要把填写了真实密码的 SQL 保存到仓库。

如果数据库或账号已经存在，请核对现有配置，不要为了启动项目删除已有数据库。代码内迁移负责建表，不负责替你创建 MySQL 数据库。

### 4. 配置根目录 .env

~~~powershell
if (-not (Test-Path -LiteralPath .\.env)) {
    Copy-Item -LiteralPath .\.env.example -Destination .\.env
}
notepad .\.env
~~~

按照 [.env.example](.env.example) 填写，保持 `KEY=value` 格式，不添加 `export` 前缀或额外引号。它同时被 Spring Boot Properties 和 Docker Compose 读取，特殊字符须兼容两种解析规则。

| 配置项 | 如何填写 |
| --- | --- |
| `AACV_DB_URL` | 默认连接本机 3306 的 `aacv_system`；只有数据库地址、端口或库名不同时才修改 |
| `AACV_DB_USERNAME` / `AACV_DB_PASSWORD` | 上一步创建的应用数据库账号与密码 |
| `NEO4J_USERNAME` / `NEO4J_PASSWORD` | Compose 初始化 Neo4j 使用的账号密码；新环境沿用默认用户名 `neo4j` |
| `AACV_NEO4J_USERNAME` / `AACV_NEO4J_PASSWORD` | 必须与上述 Neo4j 凭据一致 |
| `AACV_NEO4J_URI` | 日常开发保留 `bolt://127.0.0.1:7687` |
| `AACV_SERVER_ADDRESS` / `AACV_SERVER_PORT` | 日常开发保留 `127.0.0.1` / `8080`，与前端代理保持一致 |
| `AACV_GRAPH_OUTBOX_ENABLED` | 保留 `true`，启用图数据异步投影 |
| `OPENALEX_API_KEY` / `CROSSREF_CONTACT_EMAIL` | 按来源接入需要填写；可先留空，OpenAlex 的匿名额度和外部服务限制仍然适用 |

`AACV_STAGE8_*` 是隔离容量与恢复环境的配置，日常启动无需填写。数据库和 Neo4j 必填项中的 `change-me-before-use` 都需要替换。`.env` 已被 Git 忽略，不能提交真实凭据。

### 5. 为首次启动配置管理员

新数据库没有可直接登录的默认账号。在 `.env` 中：

1. 将 `AACV_BOOTSTRAP_ADMIN_ENABLED` 改为 `true`。
2. 将 `AACV_BOOTSTRAP_ADMIN_USERNAME` 设为你要使用的管理员账号，可沿用模板中的 `aacv-admin`。
3. 填写 `AACV_BOOTSTRAP_ADMIN_PASSWORD`；长度为 12–128 位，不能全为空白，也不能包含控制字符。

这个密码是**应用登录密码**，与 MySQL、Neo4j 的密码分别配置。引导只在 `sys_user` 为空时创建管理员；用户表非空时会跳过，不能靠它重置已有账号密码。

### 6. 安装前端依赖并启动

~~~powershell
npm.cmd --prefix .\frontend ci
.\start.bat --check
.\start.bat
~~~

先处理预检中的失败项，再执行启动。预检检查工具、配置文件是否存在和端口，但不读取或验证数据库密码。

启动入口依次启动 Neo4j，打开后端日志窗口，再打开前端日志窗口。**“已提交启动命令”不等于服务已经就绪**；首次下载依赖和执行数据库迁移可能需要更久，请查看各窗口的实际结果。

后端首次成功启动会执行 [Flyway 迁移](backend/src/main/resources/db/migration)（当前 V1–V15），创建业务表及 Session、Batch、Quartz 等基础表，并在启用引导且用户表为空时创建管理员。

### 7. 确认服务与首次登录

| 入口 | 默认地址 |
| --- | --- |
| 应用登录页 | [http://127.0.0.1:5173/login](http://127.0.0.1:5173/login) |
| 前端备用地址 | [http://127.0.0.1:15173/login](http://127.0.0.1:15173/login)，仅脚本实际选择该端口时使用 |
| 后端存活检查 | [http://127.0.0.1:8080/actuator/health/liveness](http://127.0.0.1:8080/actuator/health/liveness) |
| 后端与 MySQL 就绪检查 | [http://127.0.0.1:8080/actuator/health/readiness](http://127.0.0.1:8080/actuator/health/readiness) |
| Neo4j 与图模式健康检查 | [http://127.0.0.1:8080/actuator/health/graph](http://127.0.0.1:8080/actuator/health/graph) |
| Neo4j Browser | [http://127.0.0.1:7474](http://127.0.0.1:7474)，使用 Neo4j 账号登录 |

健康检查正常时返回 `status: UP`。后端存活不代表数据库和图谱均就绪，应分别核对上述三个健康组。

当 Windows 拒绝绑定 5173 时，启动脚本会检查并使用 15173；如果 5173 已被其他进程占用，脚本会停止，不会自动抢占。**访问启动终端打印的实际地址**，不要交替使用 `localhost` 和 `127.0.0.1`。

确认后端日志出现“初始管理员引导完成”后：

1. 在后端窗口按 `Ctrl+C` 停止后端。
2. 将 `.env` 中 `AACV_BOOTSTRAP_ADMIN_ENABLED` 恢复为 `false`，并清空 `AACV_BOOTSTRAP_ADMIN_PASSWORD`。
3. 使用下一节的后端命令重新启动；已启动的前端和 Neo4j 可以保留。
4. 使用刚创建的应用管理员账号登录前端。

### 8. 首次使用与可选样例

空数据库中的目录、统计和图谱显示空态是正常的。可以先在“数据源”配置并探测来源，再到“采集任务”创建小范围任务；数据规范入库后，成果目录和统计读取 MySQL，图谱等待 Outbox 完成投影。

只想先查看页面效果时，可在迁移完成、管理员已创建后向**本机开发库**写入仓库自带的合成样例：

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\development\Initialize-RenderingSampleData.ps1
~~~

该操作会写入样例数据，并提示输入数据库凭据；不是应用登录密码。脚本仅接受本机名为 `aacv_system` 的数据库，不创建登录账号、不访问外部采集来源。数据库用户名不是默认 `aacv` 时，通过 `-DatabaseUsername` 指定。日常使用不需要执行 `tools/stage8/` 的容量、故障或恢复脚本。

## 分别启动与停止

需要在已有前端、Neo4j 运行时重启后端，或希望分别查看启动错误，可使用下面的入口。**每个前台服务使用独立终端；不要与已运行的一键启动实例重复启动。**

~~~powershell
# 启动 Neo4j，命令执行后返回终端
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\development\Start-Development.ps1 -Component Neo4j
~~~

~~~powershell
# 启动后端，当前终端持续输出日志
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\development\Start-Development.ps1 -Component Backend
~~~

~~~powershell
# 启动前端，自动检查默认或备用端口
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\development\Start-Development.ps1 -Component Frontend
~~~

后端脚本会为应用 JVM 设置项目内的短 Socket 目录，处理 Windows 下 Neo4j Driver 可能出现的 AF_UNIX 路径问题。前端将 `/api` 和 `/actuator` 代理到本机 8080；只启动前端时，登录和业务请求仍需要真实后端。

停止项目：

~~~powershell
.\stop.bat --check
.\stop.bat --no-pause
~~~

第一条只检查停止目标，第二条执行停止。脚本会按项目路径核对前后端进程，并停止本项目 Neo4j，保留 MySQL 服务、Docker Desktop 和数据卷。进程终止可能中断任务；需要等待任务结束时，先在原终端使用 `Ctrl+C`。手动停止前后端后，也可单独停止 Neo4j：

~~~powershell
docker compose --env-file .\.env -f .\deploy\compose.yaml stop neo4j
~~~

## 开发与验证命令

以下命令用于开发验证，不是首次启动的前置步骤：

~~~powershell
# 前端类型检查与生产构建，产物位于 frontend/dist
npm.cmd --prefix .\frontend run build

# 前端单元测试
npm.cmd --prefix .\frontend test

# 浏览器测试：需要本机 Edge 和空闲的 4173 端口
npm.cmd --prefix .\frontend run test:e2e

# 后端测试与打包：集成测试需要可用的 Docker Linux Engine
.\mvnw.cmd -f .\backend\pom.xml verify
~~~

Playwright 使用模拟接口，不能替代真实数据库联调。当前分支部分前端测试仍为旧界面断言，最新测试调整尚未同步到仓库，克隆后的测试结果应以实际运行输出为准；不应把本地验收数量当作该分支的通过承诺。项目没有 `lint` 脚本。

## 常见启动问题

| 现象 | 排查方向 |
| --- | --- |
| 克隆后缺少 backend、frontend 等目录 | 确认使用的是 `feature/Luo`，而不是仅有初始化内容的默认分支。 |
| 找不到 java、版本错误或 Maven 编译失败 | 安装 JDK 21；同时检查 `java --version`、`javac --version` 和 `JAVA_HOME`，重开终端后再试。 |
| npm 安装或原生依赖加载失败 | 核对 Node.js 版本，优先使用上述已验证环境；用 `npm ci` 按锁文件恢复，不复用另一台电脑的 `node_modules`。 |
| Docker 检查失败或 Neo4j 无法启动 | 确认 Docker Desktop 已运行、当前使用 Linux Engine，并检查镜像下载是否成功。 |
| `Unknown database` / `Access denied` | 核对库是否创建、MySQL 是否运行，以及账号 Host、库权限与 `AACV_DB_*` 是否一致。 |
| 找不到配置项或 Neo4j 认证失败 | 确认配置文件名是根目录 `.env`，不是 `.env.txt`；检查必填项及两组 Neo4j 凭据。既有数据卷不会因为修改 `.env` 自动更改数据库密码。 |
| 8080 或 5173 已占用 | 检查是否已经启动项目；使用原有窗口，不要反复执行 `start.bat`。5173 被系统保留时，以脚本给出的 15173 地址为准。 |
| 登录页可见，但登录接口失败 | 查看后端日志和 readiness 检查，确认后端监听 8080；Vite 页面可打开不代表后端就绪。 |
| 不知道管理员密码 | 使用首次引导时自己填写的应用密码；用户表非空时引导不会重置密码，应由现有管理员处理。 |
| 成果目录有数据但图谱为空或报错 | 检查 graph 健康组、Neo4j、Outbox 配置及后端投影日志；图谱是异步投影，不能用页面刷新替代投影处理。 |
| 构建报告 chunk 超过 500 kB | 当前 ECharts 和图谱引擎存在已知大分块提示；区分警告与真正的构建错误。 |

本仓库提供的是本地开发流程。数据库、外部来源权限、个人凭据及生产环境配置需要在使用者自己的环境中准备。
