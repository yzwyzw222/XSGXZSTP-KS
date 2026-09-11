# 学术系统统一门户（XSGXZSTP-KS）

本项目将两套学术应用接入同一个门户，提供知网学者信息表解析、成果检索、关系分析与知识图谱可视化等功能。用户在统一入口登录后，可以进入各子系统开展检索、分析与数据管理，并从任一入口统一退出。

当前集成分支为 `dev`，本地入口为 [http://127.0.0.1:18000/](http://127.0.0.1:18000/)。门户统一入口和身份，各子系统保留自己的前后端工程与业务数据库。系统间尚未建立自动数据同步或跨系统联合检索。

## 项目功能与子系统

| 子系统 | 主要功能 | 入口与代码目录 |
| --- | --- | --- |
| 学术关系知识图谱构建平台（relation） | 论文、作者、机构与关键词查询；合作网络、机构关系、引用影响、研究领域与时间线分析；数据导入及实体抽取 | `/relation/`；`systems/relation/` |
| 学术成果信息采集及可视化系统（crawler） | XLSX/XLS/CSV 作者导入；论文、专利、机构、硕博指导与摘要解析；成果检索、统计、[学术关系/成果/背景图谱](docs/academic-graphs.md)及 CSV/JSON 导出；统一账号与审计 | `/crawler/`；`systems/crawler/` |

2026-09-10 起仅保留 relation 和 crawler；extraction、scholar 已从源码、入口和集成脚本移除。信息采集系统继续使用 `/crawler/`、`systems/crawler/` 和原数据库名，以兼容现有数据与接口。既有数据库卷不会随源码删除。信息采集首页现为日常工作台，原可视化大屏位于 `/crawler/dashboard`；2026-09-11 起通过“作者导入”解析学者资料，数据源、采集任务、数据治理和质量指标四个模块已移除，详见 [作者导入说明](docs/author-import.md)。此前体验改动保留为 [历史记录](docs/crawler-ux-improvements.md)。

门户提供两个系统入口、入口搜索、统一登录、账号菜单与返回导航。入口搜索只筛选系统名称和功能；门户的趋势、研究方向等统计面板标注为演示数据，不能作为实际业务统计使用。

**初始数据状态：**新环境没有预置用户、历史论文或演示业务数据。首次启动会创建各系统表结构，以及角色、字典等运行所需的基础记录；这些基础记录不代表已存在可登录账号。业务页面显示空列表或空图谱属于正常状态，后续可在作者导入模块上传信息表；relation 的模型功能仍需该系统的服务配置。

## 技术栈

| 部分 | 前端 | 后端与存储 |
| --- | --- | --- |
| 统一门户 | Vue 3.5、Vite 8 | Node.js 网关；统一 Session 转发、子路径代理与本地启停脚本 |
| relation | Vue 3、TypeScript、Vite 8、Element Plus、ECharts、Cytoscape.js | Java 17 编译目标、Spring Boot 3.5.16、Spring Security、Spring Data JPA、MySQL、Neo4j |
| crawler | Vue 3、TypeScript、Vite 8、Element Plus、Tailwind CSS、Pinia、ECharts、Cytoscape.js | Java 21、Spring Boot 4.1.1、MyBatis、Spring Security / Session JDBC、Spring Batch、Quartz、Flyway、MySQL、Neo4j |

统一运行使用 **JDK 21**。各系统独立维护 Maven Wrapper、POM、`package.json` 和锁文件，具体依赖版本以这些文件为准。没有统一父 POM 或 npm workspace。

## 项目结构

```text
.
├── portal/                       # 统一门户、登录页与 Node/Vite 网关
│   ├── src/                      # 门户组件、主题、会话与入口配置
│   ├── public/                   # 本地图片和图标
│   └── server.mjs                # Demo / Development 入口
├── systems/
│   ├── relation/
│   │   ├── backend/              # 关系分析后端
│   │   └── frontend/             # 关系分析前端
│   └── crawler/
│       ├── backend/              # 作者导入、图谱、统一账号与权限后端
│       └── frontend/             # 学者资料与图谱可视化前端
├── deploy/systems.json           # 系统入口、运行目录、端口与健康检查配置
├── scripts/                     # 初始化、构建、启停与验证脚本
├── docs/                        # 开发说明、认证契约、来源记录与验收文档
└── .local/                      # 本机生成的配置、凭据、日志与产物，Git 忽略
```

## clone 或拉取分支后的首次启动

以下命令均在仓库根目录的 **PowerShell 7（pwsh）** 中执行。子系统 README 中的独立启动流程使用不同端口和配置，运行整个集成项目时以本文为准。

### 1. 准备环境

| 工具 | 要求 |
| --- | --- |
| 操作系统 | Windows；当前统一脚本依赖 Windows ACL、CIM 与 `.cmd` 命令 |
| PowerShell | 7 或更高版本 |
| Java | JDK 21，`java`、`jar` 可在终端调用 |
| Node.js / npm | Node 24.15.0 或更高的 24.x 版本；当前锁文件中部分工具依赖要求该最低版本 |
| Git | 用于获取代码和来源校验 |
| Docker Desktop | 已启动 Linux Engine，能运行 Docker Compose V2 |
| 数据库客户端 | 可连接 MySQL 8 的本地客户端，用于首次手动创建管理员 |

无需另装 Maven，构建脚本使用各项目已有 Maven Wrapper。首次恢复依赖、下载 Maven 或 Docker 镜像需要网络；本地会运行两个 JVM、两个 MySQL 和两个 Neo4j，请为 Docker 和应用预留资源。

```powershell
$PSVersionTable.PSVersion
java -version
node --version
npm.cmd --version
git --version
docker info --format '{{.OSType}}'
docker compose version
```

Docker 的系统类型应为 `linux`。

### 2. 获取 dev 分支

首次克隆：

```powershell
git clone --branch dev https://github.com/yzwyzw222/XSGXZSTP-KS.git
Set-Location XSGXZSTP-KS
```

已有仓库的开发者，先确认 `git status` 中的工作已经妥善保存，再获取并切换分支：

```powershell
git status
git fetch origin
git switch dev
git pull --ff-only origin dev
```

若本地还没有 `dev`，将 `git switch dev` 替换为 `git switch --track origin/dev`。若快进拉取失败，先处理分支分歧，不要强制覆盖已有工作。`.local/`、数据库卷、`node_modules/`、`target/` 和 `dist/` 不随源码共享，其他开发者必须完成下面的准备步骤。

### 3. 初始化本机数据库与运行配置

```powershell
.\scripts\Initialize-Integration.ps1
```

脚本创建 `course-integration` Docker Compose 项目，使用 MySQL 8.0.42 和 Neo4j 5.26 Community，每个子系统使用独立数据库、应用账号和数据卷，所有端口仅绑定 `127.0.0.1`。

本机配置写入 `.local/integration-runtime/<系统>/`：`credentials.json` 保存随机运行凭据，`application.properties` 保存后端配置；该目录只允许当前 Windows 用户访问并被 Git 忽略。初始化脚本只准备基础服务，**不会向新数据库添加应用用户**；表结构由下一步首次启动后端时创建。

重复初始化会保留已有密码、`application.properties` 和数据卷。如果另一个工作区已占用同名 Compose 项目，脚本会拒绝复用，需要先确定应使用哪个工作区。

### 4. 恢复依赖、构建并启动

```powershell
.\scripts\Build-Integration.ps1 -Restore
.\scripts\Start-Integration.ps1 -System all -Mode Demo
```

`-Restore` 按三个前端工程的锁文件执行 `npm ci`，随后构建前端并打包两个后端。后端打包使用 `-DskipTests`，构建成功不代表单元测试已经通过。

启动完成后，门户和两个后端应显示就绪。此时可以打开 [统一登录页](http://127.0.0.1:18000/login)，但新环境必须先完成下一步手动建号才能登录。

### 5. 在 crawler 数据库手动创建统一管理员

统一管理员用户名为 **`admin`**，密码沿用 crawler 系统。已有本机初始化密码位于 `.local/integration-runtime/crawler/credentials.json` 的 **`admin`** 字段；全新 clone 初始化也会在该字段生成仅供本机使用的密码，**生成密码文件不等于创建数据库账号**。请在自己的本地编辑器中查看，勿提交或分享该文件。

两个系统的统一登录只读取 crawler 的账号库。只需在 `course_crawler` 创建账号，不需要向另一个系统复制用户或密码。已有可登录的 `admin` 可直接沿用；如果之前在账号管理中修改过密码，以数据库保存的新密码为准，凭据文件不会自动更新。

**连接数据库：**

| 连接项 | 值 |
| --- | --- |
| 主机 / 端口 | `127.0.0.1:23363` |
| 数据库 | `course_crawler` |
| 数据库用户名 | `course_crawler` |
| 数据库密码 | 上述 `credentials.json` 的 `database` 字段 |
| 应用管理员用户名 / 密码来源 | `admin` / 上述文件的 `admin` 字段 |

数据库连接密码与网页登录密码是两个不同字段。

**在本地生成密码哈希：**`sys_user.password_hash` 使用 Spring Security 的 `DelegatingPasswordEncoder`，必须存储包含 `{bcrypt}` 前缀的完整哈希，不能直接填写明文密码或无前缀的 BCrypt 值。下面复用已构建 crawler jar 中的库，不下载新依赖：

```powershell
$crawlerJar = (Resolve-Path .\systems\crawler\backend\target\system-0.0.1-SNAPSHOT.jar).Path
$hashDirectory = Join-Path (Get-Location).Path '.local/password-tools'
$hashLibraries = @(jar tf $crawlerJar | Where-Object { $_ -match '^BOOT-INF/lib/(spring-security-crypto|spring-core|commons-logging)-.*\.jar$' })
if ($LASTEXITCODE -ne 0 -or $hashLibraries.Count -ne 3) { throw '请先成功构建 crawler 后端。' }
New-Item -ItemType Directory -Path $hashDirectory -Force | Out-Null
Push-Location $hashDirectory
try {
    jar xf $crawlerJar @hashLibraries
    if ($LASTEXITCODE -ne 0) { throw '密码编码依赖解压失败。' }
    $hashClasspath = ($hashLibraries | ForEach-Object { Join-Path $hashDirectory $_ }) -join [IO.Path]::PathSeparator
    @'
import java.nio.CharBuffer;
import java.util.Arrays;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

public class HashAdminPassword {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("请在交互终端运行此工具");
        char[] password = console.readPassword("管理员密码: ");
        if (password == null) throw new IllegalStateException("已取消输入");
        try {
            var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
            var hash = encoder.encode(CharBuffer.wrap(password));
            if (!encoder.matches(CharBuffer.wrap(password), hash)) {
                throw new IllegalStateException("密码编码验证失败");
            }
            System.out.println(hash);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
'@ | Set-Content -LiteralPath HashAdminPassword.java -Encoding utf8
    java --class-path $hashClasspath HashAdminPassword.java
    if ($LASTEXITCODE -ne 0) { throw '密码哈希生成失败。' }
} finally {
    Pop-Location
}
```

请在支持交互输入的本机终端中执行完整代码块，出现密码提示时输入本机 `admin` 字段的值。密码不回显，不写入源码或命令历史；编码成功后程序自动退出。将输出的完整哈希复制到本地数据库客户端，哈希同样不要提交或分享。辅助源码与依赖保存在 Git 忽略的 `.local/password-tools/`，不包含输入的密码。

**手动执行建号 SQL：**先查询账号与基础角色，确认没有现成的 `admin`，并且 `ADMIN` 角色存在。

```sql
USE course_crawler;
SELECT id, username, status FROM sys_user WHERE username = 'admin';
SELECT id, role_code FROM sys_role WHERE role_code = 'ADMIN';
```

仅在账号不存在时执行下面的事务；执行前，将 `替换为本地生成的完整哈希` 替换为刚生成的 `{bcrypt}...`。若 SQL 报错，执行 `ROLLBACK;` 并排查原因，不要继续提交事务或覆盖已有账号。

```sql
START TRANSACTION;

INSERT INTO sys_user (username, password_hash, status)
SELECT 'admin', '替换为本地生成的完整哈希', 'ACTIVE'
FROM sys_role
WHERE role_code = 'ADMIN';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
CROSS JOIN sys_role r
WHERE u.username = 'admin' AND r.role_code = 'ADMIN';

COMMIT;

SELECT u.username, u.status, r.role_code
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
WHERE u.username = 'admin';
```

确认结果包含 `admin / ACTIVE / ADMIN` 后，即可使用原始密码登录门户，无需重启服务。后续用户可通过门户顶部“用户管理”（`/management/users`）维护，这些账号对两个子系统共同生效。“日志管理”统一查询整个项目的请求及原有采集后台审计；“全屏”作用于门户和其内的全部子系统。各子系统左上角返回统一门户，切换时保持全屏。详见 [平台管理说明](docs/platform-management.md)。

### 6. 验证访问与开始使用

登录后依次打开两个系统，确认能读取空列表、返回门户，并能统一退出。需要业务数据时，在相应系统导入；relation 的模型功能需配置该系统的外部服务，初始化不会自动发起付费模型请求或数据采集。

常用检查：

```powershell
npm.cmd --prefix portal run test
npm.cmd --prefix portal run check:source
.\scripts\Test-IntegrationInitialization.ps1
node scripts/Test-IntegratedSystems.mjs
```

前三项不需要真实业务账号；初始化测试在临时目录中模拟 Docker 调用。最后一项需要已启动的完整系统，以及数据库中的 `admin` 密码与 crawler 凭据文件的 `admin` 字段一致，它会验证登录、真实接口与退出后的会话失效。

来源检查依赖 Git 历史和来源对象，获取代码时不要使用浅克隆、ZIP 下载或 `--single-branch`。

## 日常开发、更新与启停

完成环境准备和构建后，可直接双击仓库根目录的 `start.bat` 启动整个项目，双击 `stop.bat` 停止应用。启动完成后访问 [统一门户](http://127.0.0.1:18000/)。两个脚本默认保留窗口以便查看结果；命令行调用可使用 `--no-pause`，查看帮助使用 `--help`。

```powershell
.\start.bat --no-pause
.\stop.bat --no-pause
```

`start.bat` 优先使用已有的 `.local/Start-LocalProject.ps1`，适配本机 MySQL80，并按提示安全输入数据库密码；其他工作区调用 `scripts/Start-Integration.ps1 -System all -Mode Demo`。两种入口均使用现有构建产物，不自动安装依赖或重建。脚本查找 PATH、常规安装目录及当前用户已有的 Codex PowerShell 7 运行时；缺失时给出提示。`stop.bat` 通过现有进程记录与归属校验停止门户和两个后端，保留数据库、容器、卷和运行数据。

更新代码或重建后端前先停止应用，以免 Windows 锁定正在运行的 jar：

```powershell
.\scripts\Stop-Integration.ps1 -System all
.\scripts\Build-Integration.ps1
.\scripts\Start-Integration.ps1 -System all -Mode Demo
```

首次拉取此集成分支、缺少依赖或锁文件变化时使用 `Build-Integration.ps1 -Restore`。`Demo` 提供构建后的页面；前端开发使用下面的模式，两个 Vite 服务提供 HMR，访问仍通过门户的 18000 端口：

```powershell
.\scripts\Stop-Integration.ps1 -System all
.\scripts\Start-Integration.ps1 -System all -Mode Development
```

Java 后端仍运行 jar，后端代码修改后需要停止、重新构建并启动。两个模式互斥。单个子系统可以用 `-System relation` 或 `-System crawler` 启停；crawler 停止期间其他系统无法确认统一身份。

**已有旧版运行配置的开发者：**初始化不会覆盖你的配置。若 `.local/integration-runtime/crawler/application.properties` 仍为 `aacv.bootstrap-admin.enabled=true`，将其改为 `false` 后重启 crawler，使空用户表也不会自动建号。relation 的旧 `integration.admin-password` 字段不再单独触发引导，新版本默认停用它们的管理员引导。此更新不会删除已有账号或重置密码。

应用停止不影响数据库卷。暂时停用数据库可执行：

```powershell
docker compose -f .local/integration-runtime/compose.json stop
```

下次先运行 `Initialize-Integration.ps1` 恢复同一组基础服务，再启动应用。不要执行 `down -v`，它会删除业务数据卷。

## 端口、排错与进一步阅读

| 系统 | 开发前端 | 后端 | MySQL 端口 / 库名 | Neo4j HTTP / Bolt |
| --- | --- | --- | --- | --- |
| 门户 | 18000 | 18000 | — | — |
| relation | 5174 | 18081 | 23361 / `course_relation` | 27471 / 27681 |
| crawler | 5176 | 18083 | 23363 / `course_crawler` | 27473 / 27683 |

| 现象 | 检查方法 |
| --- | --- |
| 无法访问 Docker | 确认 Docker Desktop 已启动 Linux Engine，`docker info` 和 `docker compose version` 可用 |
| 构建失败 | 查看 `.local/integration-build/`，核对 JDK、Node 版本、网络和依赖恢复结果 |
| 端口占用或组件重复启动 | 按脚本提示确认归属；使用 `Stop-Integration.ps1` 停止当前仓库登记的应用，再启动 |
| 后端未就绪或返回 503 | 查看 `.local/integration/*.out.log`、`*.err.log` 和 Compose 容器状态；统一登录还依赖 crawler 与门户可用 |
| admin 无法登录 | 核对 `course_crawler.sys_user` 的用户名、`ACTIVE` 状态、带 `{bcrypt}` 前缀的哈希与 `ADMIN` 角色；修改凭据文件本身不会更新数据库密码 |
| 论文列表或图谱为空 | 新库无预置业务数据；确认已在当前子系统导入，并检查图投影同步状态 |

更多说明见 [开发与运行配置](docs/development.md)、[统一登录与权限映射](docs/unified-login.md)、[集成基线与来源约束](docs/integration-baseline.md)、[本地运行验收记录](docs/local-runtime-acceptance.md)。验收记录描述对应阶段实际执行的检查，不代表每次拉取后的自动验证结果。

当前统一入口面向本机开发与演示，使用 Node/Vite 网关；公网部署、HTTPS 和生产运维不属于这套启动流程的验收范围。`deploy/nginx/` 中的旧模板尚未接入统一认证，不能直接替代当前门户。
