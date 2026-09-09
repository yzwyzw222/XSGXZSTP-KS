# 学术系统统一门户（XSGXZSTP-KS）

四套子系统已完成本地接入：统一登录入口为 http://127.0.0.1:18000/login 。使用爬虫系统账号登录一次，即可进入四个子系统；从任一入口退出会同时结束本次登录在四个系统的访问。门户和四个 Java 后端使用统一启停脚本，业务数据库保持独立。当前集成版本维护在 `dev`，系统导入、统一认证与运行接入、门户界面已分主题提交并推送至 `origin/dev`。

| 系统 | 来源 | 目录 | 入口 |
| --- | --- | --- | --- |
| 学术关系知识图谱构建平台 | `feature/Ye` 的已核定完整版 | `systems/relation` | `/relation/` |
| 学术多源实体抽取与学术知识图谱构建 | `feature/Du` | `systems/extraction` | `/extraction/` |
| 学术成果爬虫及可视化系统 | `feature/Luo` | `systems/crawler` | `/crawler/` |
| 学术成果知识图谱构建平台 | `Li` | `systems/scholar` | `/scholar/` |

本次 `git fetch --no-tags origin` 已确认：Du、Luo 与现有导入版本一致；`Li` 包含第四套系统；`feature/Li` 和 `feature/Ma` 仅有占位文件。Ye 远端已回退到旧骨架 `bf2494f…`，保留本地已有完整版 `184f651…`，没有用旧骨架覆盖现有功能。完整来源与适配证据见 [集成基线](docs/integration-baseline.md)。

## 本地启动

需要 Windows、PowerShell 7、Java 21、Node.js/npm 与运行中的 Docker Desktop Linux Engine。本轮使用 Node 24.14.0；Li 的两个间接开发依赖发出了更高 Node 版本要求的警告，推荐后续使用 Node 24.15 或更高兼容版本。本轮全部前端构建已实际通过，没有升级任何前端锁文件。

首次准备依次执行：

```powershell
.\scripts\Initialize-Integration.ps1
.\scripts\Build-Integration.ps1 -Restore
.\scripts\Start-Integration.ps1 -System all -Mode Demo
```

`Initialize-Integration.ps1` 仅创建 `course-integration` Docker Compose 项目：四个 MySQL 8.0.42、三个 Neo4j 5.26 实例及独立卷，端口只绑定 127.0.0.1。使用新库，不连接原业务库，不迁移旧数据。重复初始化保留已有随机凭据和数据卷。

打开 http://127.0.0.1:18000/ ，登录后从四张卡片进入系统。统一管理员用户名为 `admin`，沿用爬虫系统密码；本机初始化密码在 `.local/integration-runtime/crawler/credentials.json` 的 `admin` 字段，可在自己的本地编辑器中查看，无需发送到聊天。该目录只允许当前 Windows 用户访问，并被 Git 忽略。其他账号在门户账号菜单的“管理平台账号”中维护。统一认证与权限映射见 [统一登录说明](docs/unified-login.md)。

数据库目前没有导入历史论文。子系统中的空列表和空图谱是新环境的实际状态；门户周边统计仍是明确标注的演示数据。外部采集及 LLM 实体抽取需要相应服务配置，不属于本轮已验证的数据采集结果。

## 启停和重新构建

```powershell
.\scripts\Stop-Integration.ps1 -System all
.\scripts\Build-Integration.ps1
.\scripts\Start-Integration.ps1 -System all -Mode Demo
```

构建前先停止后端，避免 Windows 锁定运行中的 jar。普通停止仅结束当前仓库已登记、身份匹配的进程，保留数据库容器及数据。若暂时不用数据库，可执行以下非删除操作：

```powershell
docker compose -f .local/integration-runtime/compose.json stop
```

再次运行初始化脚本即可启动同一组数据库。不要使用 `down -v` 清理业务卷。

单个系统可以独立停止并恢复，其他系统与门户保持运行：

```powershell
.\scripts\Stop-Integration.ps1 -System relation
.\scripts\Start-Integration.ps1 -System relation -Mode Demo
```

开发模式使用同一个门户入口，并启动四个独立 Vite 服务：先停止所有组件，再执行 `.\scripts\Start-Integration.ps1 -System all -Mode Development`。切换模式不会修改数据库。

## 验证与维护

```powershell
npm.cmd --prefix portal run test
npm.cmd --prefix portal run check:source
node scripts/Test-IntegratedSystems.mjs
node scripts/Test-PortalBrowser.mjs systems/crawler/frontend/node_modules/playwright/index.mjs
node scripts/Test-SystemsBrowser.mjs
node scripts/Test-UnifiedLoginBrowser.mjs
.\scripts\Test-IntegrationRecovery.ps1
```

浏览器用例使用本机 Edge 和 crawler 已有的 Playwright。完整命令、结果、失败重跑及未验证范围见 [本地运行验收](docs/local-runtime-acceptance.md)。启停规则与端口配置见 [开发说明](docs/development.md)。历史门户维护模式验收保存在 [第一阶段记录](docs/integration-acceptance.md)。
