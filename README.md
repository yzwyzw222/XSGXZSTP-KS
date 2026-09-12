# 学术成果信息采集及可视化系统（XSGXZSTP-KS）

本项目用于导入知网学者信息表、检索学术成果并展示学术关系、成果与背景图谱。自 2026-09-12 起，项目只保留成果系统；学术关系知识图谱构建平台及统一门户页面、系统选择和 iframe 工作区已删除。

打开 [http://127.0.0.1:18000/](http://127.0.0.1:18000/) 直接进入成果系统，未登录时显示系统自己的登录页。既有 `/crawler/` 地址、端口、数据库、账号和权限保持兼容。项目内部的“学术关系图谱”仍是成果系统的功能，与本次删除的独立 relation 平台不同。

主要功能包括 XLSX/XLS/CSV 作者导入、论文和专利解析、成果检索、实体编目、统计分析、三类学术图谱及 CSV/JSON 导出。指导硕论、指导博论编目显示真实指导关系对应的导师姓名，支持多人指导和合并记录去重。账号管理与日志管理位于系统导航内，顶部保留全屏及退出操作。

作者导入页在成功结果和最近导入记录中显示作者内部标识，不再提供 ORCID 补全面板、试匹配或人工绑定入口。ORCID 后台获取及其接口已移除，已有编号和历史数据保留，停用说明见 [ORCID 说明](docs/author-orcid.md)。

**初始数据状态：**新环境没有预置用户或业务成果。首次启动创建表结构、角色和字典；业务数据通过作者导入页面上传，管理员按下文手动创建。删除旧平台不会删除数据库、容器、数据卷或运行凭据。

## 技术栈与结构

前端使用 Vue 3、TypeScript、Vite 8、Element Plus、Pinia、ECharts 和 Cytoscape.js；后端使用 Java 21、Spring Boot 4.1.1、MyBatis、Spring Security / Session JDBC、Quartz、Flyway、MySQL 和 Neo4j。依赖版本以现有 POM、package.json 及锁文件为准。

- `systems/crawler/backend/`：业务后端、账号权限及数据库迁移。
- `systems/crawler/frontend/`：唯一业务前端，包含登录及管理页面。
- `scripts/server.mjs`：本地访问网关，复用前端已有 Vite 依赖；没有独立门户工程。
- `deploy/systems.json`：唯一系统运行配置。
- `scripts/`：构建、启停、初始化和验证。
- `docs/integration-baseline.md`、`docs/development.md`：指定项目记忆。
- `.local/`：Git 忽略的本机配置、日志、构建与归档材料。

## clone 或拉取分支后的首次启动

以下命令均在仓库根目录的 **PowerShell 7（pwsh）** 中执行。系统 README 中的独立调试流程使用不同端口，运行整个项目时以本文为准。

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

无需另装 Maven，构建脚本使用成果系统已有 Maven Wrapper。首次恢复依赖、下载 Maven 或 Docker 镜像需要网络；本地会运行一个 JVM、一个 MySQL 和一个 Neo4j，请为 Docker 和应用预留资源。

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

脚本创建 `course-integration` Docker Compose 项目，使用 MySQL 8.0.42 和 Neo4j 5.26 Community，成果系统使用既有数据库、应用账号和两个数据卷，所有端口仅绑定 `127.0.0.1`。

本机配置写入 `.local/integration-runtime/<系统>/`：`credentials.json` 保存随机运行凭据，`application.properties` 保存后端配置；该目录只允许当前 Windows 用户访问并被 Git 忽略。初始化脚本只准备基础服务，**不会向新数据库添加应用用户**；表结构由下一步首次启动后端时创建。

重复初始化会保留已有密码、`application.properties` 和数据卷。如果另一个工作区已占用同名 Compose 项目，脚本会拒绝复用，需要先确定应使用哪个工作区。

### 4. 恢复依赖、构建并启动

```powershell
.\scripts\Build-Integration.ps1 -Restore
.\scripts\Start-Integration.ps1 -System all -Mode Demo
```

`-Restore` 按成果系统前端锁文件执行 `npm ci`，随后构建一个前端并打包一个后端。后端打包使用 `-DskipTests`，构建成功不代表单元测试已经通过。

启动完成后，网关和成果系统后端应显示就绪。此时可以打开 [系统登录页](http://127.0.0.1:18000/login)，但新环境必须先完成下一步手动建号才能登录。

### 5. 在 crawler 数据库手动创建统一管理员

统一管理员用户名为 **`admin`**，密码沿用 crawler 系统。已有本机初始化密码位于 `.local/integration-runtime/crawler/credentials.json` 的 **`admin`** 字段；全新 clone 初始化也会在该字段生成仅供本机使用的密码，**生成密码文件不等于创建数据库账号**。请在自己的本地编辑器中查看，勿提交或分享该文件。

系统登录只读取 `course_crawler` 的账号库。已有可登录的 `admin` 可直接沿用；如果之前在账号管理中修改过密码，以数据库保存的新密码为准，凭据文件不会自动更新。

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

确认结果包含 `admin / ACTIVE / ADMIN` 后，即可使用原始密码登录系统，无需重启服务。后续在“账号管理”（`/crawler/users`）维护用户，“日志管理”（`/crawler/logs`）查看操作和登录审计。日志页右上角搜索账号，时间、事件类型和结果从表头筛选；请求日志功能已移除，旧请求日志及管理书签会转入对应业务页，历史日志文件保留原状。

### 6. 验证访问与开始使用

登录后打开成果目录、实体编目和账号管理，确认能读取空列表并退出。需要业务数据时使用作者导入；系统不会自动导入样例。

常用检查：

```powershell
node --test --test-isolation=none scripts/tests/*.test.mjs
node scripts/check-source.mjs
.\scripts\Test-IntegrationInitialization.ps1
node scripts/Test-IntegratedSystems.mjs
```

前三项不需要真实业务账号；初始化测试在临时目录中模拟 Docker 调用。最后一项需要已启动的完整系统，以及数据库中的 `admin` 密码与 crawler 凭据文件的 `admin` 字段一致，它会验证登录、真实接口与退出后的会话失效。

来源检查依赖 Git 历史和来源对象，获取代码时不要使用浅克隆、ZIP 下载或 `--single-branch`。

## 日常开发、更新与启停

完成环境准备和构建后，可直接双击仓库根目录的 `start.bat` 启动整个项目，双击 `stop.bat` 停止应用。启动完成后访问 [成果系统](http://127.0.0.1:18000/)。两个脚本默认保留窗口以便查看结果；命令行调用可使用 `--no-pause`，查看帮助使用 `--help`。

```powershell
.\start.bat --no-pause
.\stop.bat --no-pause
```

`start.bat` 优先使用已有的 `.local/Start-LocalProject.ps1`，适配本机 MySQL80，并按提示安全输入数据库密码；其他工作区调用 `scripts/Start-Integration.ps1 -System all -Mode Demo`。两种入口均使用现有构建产物，不自动安装依赖或重建。脚本查找 PATH、常规安装目录及当前用户已有的 Codex PowerShell 7 运行时；缺失时给出提示。`stop.bat` 通过现有进程记录与归属校验停止网关和成果系统后端，保留数据库、容器、卷和运行数据。

更新代码或重建后端前先停止应用，以免 Windows 锁定正在运行的 jar：

```powershell
.\scripts\Stop-Integration.ps1 -System all
.\scripts\Build-Integration.ps1
.\scripts\Start-Integration.ps1 -System all -Mode Demo
```

首次拉取此集成分支、缺少依赖或锁文件变化时使用 `Build-Integration.ps1 -Restore`。`Demo` 提供构建后的页面；前端开发使用下面的模式，一个业务 Vite 服务提供 HMR，访问仍通过网关的 18000 端口：

```powershell
.\scripts\Stop-Integration.ps1 -System all
.\scripts\Start-Integration.ps1 -System all -Mode Development
```

Java 后端仍运行 jar，后端代码修改后需要停止、重新构建并启动。两个模式互斥。使用 `-System crawler` 单独启停业务服务；`-System portal` 是保留的网关进程兼容参数，不再启动门户页面。

**已有旧版运行配置的开发者：**初始化不会覆盖你的配置。若 `.local/integration-runtime/crawler/application.properties` 仍为 `aacv.bootstrap-admin.enabled=true`，将其改为 `false` 后重启 crawler，使空用户表也不会自动建号。此更新不会删除已有账号或重置密码。

应用停止不影响数据库卷。暂时停用数据库可执行：

```powershell
docker compose -f .local/integration-runtime/compose.json stop
```

下次先运行 `Initialize-Integration.ps1` 恢复同一组基础服务，再启动应用。不要执行 `down -v`，它会删除业务数据卷。

## 端口、排错与进一步阅读

| 系统 | 开发前端 | 后端 | MySQL 端口 / 库名 | Neo4j HTTP / Bolt |
| --- | --- | --- | --- | --- |
| 系统网关 | 18000 | 18000 | — | — |
| crawler | 5176 | 18083 | 23363 / `course_crawler` | 27473 / 27683 |

| 现象 | 检查方法 |
| --- | --- |
| 无法访问 Docker | 确认 Docker Desktop 已启动 Linux Engine，`docker info` 和 `docker compose version` 可用 |
| 构建失败 | 查看 `.local/integration-build/`，核对 JDK、Node 版本、网络和依赖恢复结果 |
| 端口占用或组件重复启动 | 按脚本提示确认归属；使用 `Stop-Integration.ps1` 停止当前仓库登记的应用，再启动 |
| 后端未就绪或返回 503 | 查看 `.local/integration/*.out.log`、`*.err.log` 和 Compose 容器状态；登录依赖 crawler 与网关可用 |
| admin 无法登录 | 核对 `course_crawler.sys_user` 的用户名、`ACTIVE` 状态、带 `{bcrypt}` 前缀的哈希与 `ADMIN` 角色；修改凭据文件本身不会更新数据库密码 |
| 论文列表或图谱为空 | 新库无预置业务数据；确认已导入，并检查图投影同步状态 |

更多说明见 [开发与运行配置](docs/development.md)、[统一登录与权限映射](docs/unified-login.md)、[集成基线与来源约束](docs/integration-baseline.md)、[本地运行验收记录](docs/local-runtime-acceptance.md)。验收记录描述对应阶段实际执行的检查，不代表每次拉取后的自动验证结果。

当前访问网关面向本机开发与演示。公网部署、HTTPS、Nginx 运行不属于本轮验证范围。单系统改造、归档位置及最新验证见 [单系统调整记录](docs/single-system.md)。
