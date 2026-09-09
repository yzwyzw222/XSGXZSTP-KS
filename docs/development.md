# 本地开发与后续接入

## 构建和入口

所有总入口命令在仓库根目录执行，PowerShell 7、Node.js >=22.12.0。首次使用 `npm --prefix portal ci`，不要在根目录或三个子系统之间共享 `node_modules`。门户依赖只有 Vue、Vite 和 Vue 插件，全部以独立锁文件固定。

```powershell
npm --prefix portal run build
.\scripts\Start-Integration.ps1 -System all -Mode Demo
.\scripts\Stop-Integration.ps1 -System all
.\scripts\Start-Integration.ps1 -System portal -Mode Development
.\scripts\Stop-Integration.ps1 -System all
```

`Demo` 为本机 Vite preview，`Development` 为门户 Vite 开发服务。直接使用 `npm --prefix portal run dev` / `npm --prefix portal run preview` 时保持终端打开，用 Ctrl+C 停止；总停止脚本不操作未登记的手工进程。修改接入配置后必须重启；运行中发现配置无效或摘要改变会返回配置错误，业务路径关闭转发。

显式选择维护中的系统仍只启动门户并说明“维护中”。单个已启用进程失败时保留门户及已启动组件，报告组件名和日志目录；日志查看和故障处理由维护者完成。当前没有子系统运行适配器，三个 `runtime` 均为 null。

## 端口与数据规划

下列子系统值是第二阶段规划，**并非已经配置到原业务代码**。门户 18000 已完成实际监听和重复启动验证。

| 系统 | 开发前端 | 后端 | Session Cookie / Path | MySQL | Neo4j HTTP / Bolt |
| --- | --- | --- | --- | --- | --- |
| relation | 5174 | 18081 | `RELATION_SESSION` / `/relation` | `course_relation` | 27471 / 27681 |
| extraction | 5175 | 18082 | `EXTRACTION_SESSION` / `/extraction` | `course_extraction` | 27472 / 27682 |
| crawler | 5176 | 18083 | `CRAWLER_SESSION` / `/crawler` | `course_crawler` | 27473 / 27683 |

启动前显式核对端口、Windows 排除端口段、独立库账号、Neo4j 版本及独立卷。不得停用占用端口的未知进程或自动切换随机端口。数据目录、日志、缓存、上传、导出、PID 必须位于各自系统的隔离目录；共享 MySQL 服务不等于共享数据库或账号。当前总入口不创建/停止数据库或容器，不复制原凭据。

## 接入配置契约

配置唯一来源为 `deploy/systems.json`，支持 `maintenance` 和 `enabled`。系统 ID 与路径固定；三个条目必须全部存在。非法版本、状态、重复 ID、冲突端口、缺失信息明确失败，不默认开放。

对某系统启用前，先在 `systems/<id>` 内完成适配与真实验收，再增加 `runtime`：

| 字段 | 含义 |
| --- | --- |
| `acceptance` | 已跟踪的 `docs/<id>-acceptance.md` 路径，记录实际命令、结果与限制 |
| `acceptanceSha` | 本次已验收的完整来源 SHA，必须等于 `sourceSha` |
| `dist` | 固定 `systems/<id>/frontend/dist` |
| `backendPort`、`frontendPort` | 独立且不与门户/其他已启用系统冲突的端口 |
| `readinessPath` | 后端真实实现的只读就绪端点；必须检查必要基础服务，不能用登录页面代替 |
| `backend`、`frontend` | 各含 `executable`（node 或 java）、字符串数组 `args`、仓库相对 `cwd` |

`backend` 在两种模式启动，`frontend` 仅开发模式启动。它们必须是前台、直接运行的单个 node/java 进程，不能经 cmd、npm.cmd、Maven Wrapper 或后台启动脚本再创建失去归属记录的进程。Java 应先用所属 Maven Wrapper 打包，再用独立配置直接执行 jar；前端通过所属 Vite JS 入口直接执行。进程参数不得包含密码、令牌或其他凭据。数据库准备和独立 Neo4j 生命周期适配须在单系统接入时补全并验收，不能仅填写此表就声称可运行。

从仓库根目录可审阅实际启动计划：

```powershell
node scripts/config.mjs all Demo
node scripts/config.mjs relation Development
```

配置中的验收文件和 SHA 一致只是机械门槛，不能替代人工审阅及真实业务验收。当前启用代理单测只验证网关参数，不代表任何业务系统接入通过。

## 启用前的验收要求

1. 设置 Vite `base`、Vue Router history base；集中调整 API 前缀、静态资源、下载、登录/退出重定向与返回门户链接。
2. 保留认证及 CSRF：核对真实 Session 实现、Cookie 名称/Path、退出清除行为、Token Cookie/请求 Header、允许来源。不得禁用 CSRF 或放宽权限来消除错误。
3. 浏览器 localStorage/sessionStorage 键使用系统前缀，验证多系统会话互不干扰。同源子路径并非互不信任应用之间的安全隔离边界。
4. 使用独立 MySQL 库和账号、独立 Neo4j 实例/卷、运行目录；按原有迁移体系初始化全新隔离环境，不接入原业务库试验。
5. 保留查询参数、方法、响应状态与 Cookie。开发模式代理 HMR WebSocket；API 先于静态资源与 SPA 回退，后端失败返回明确不可用。
6. 运行所属系统已有测试、构建与真实核心流程；检查深链接刷新、下载、错误 API、未授权/CSRF 拒绝、停后端降级、恢复、重复启停及跨系统登录。
7. 记录全部未通过项，完成后更新验收记录、来源 SHA、接入配置与此处的实际状态。

## 后续 subtree 同步

维护者继续在原 `feature/Ye`、`feature/Du`、`feature/Luo` 分支使用各自原布局。集成负责人在获授权的本地更新分支中 fetch，核定新 SHA 并比较 `docs/integration-baseline.md` 的最后同步点。

先执行祖先检查，确认来源历史没有回退或改写。当前 Ye 已观察到回退，必须先处理该差异；下例只有在历史前提成立时才能执行：

```powershell
git fetch origin
git merge-base --is-ancestor <上次来源SHA> <本次核定SHA>
git subtree merge --prefix=systems/relation <本次核定SHA>
```

占位符必须替换为已核定值，Du/Luo 同理使用各自目录。不带 `--squash`，不普通 `git merge feature/Ye`，不整目录覆盖。审阅冲突并保留集成侧路径、端口、认证与隔离适配。更新来源记录及对应系统测试，回归门户。若来源改用新的 main 目录布局，则重新确定工作方式，不能继续按旧根树 subtree 导入。

当前 `check:source` 验证首次导入后的原样树。第一次接入适配时，扩展为“原始导入树 + 来源祖先 + 可审阅适配 diff”，并记录新增同步提交；不要删除历史验证来使测试通过。

## 常见故障

- `ENOTCACHED`：本机 npm 被设为离线。明确允许联网后，可运行 `npm --prefix portal ci --offline=false --registry=https://registry.npmjs.org --cache .local/npm-cache`，不修改全局 npm 配置。
- `EPERM`、Git `signal pipe` 或 `.git/index.lock` 权限错误：是工具沙箱限制。本轮经授权在正常权限环境执行相关 Git/Node 操作；不能解释为源码冲突或关闭测试。
- 端口已占用：停止本仓库已登记组件，或检查其他项目；不结束未知进程。
- 进程记录身份不符：停止脚本拒绝操作并保留记录。先由维护者核对真实进程，不直接删除记录后按 PID 强杀。
- 配置变化后 503：检查版本化配置、重新构建并停止/启动门户；不能把未知配置视为 enabled。
- 某后端就绪超时：查看对应 `.local/integration/<id>.err.log`，仅处理所属组件；门户保持可用。

## 官方接口依据

网关利用 [Vite configureServer / configurePreviewServer](https://vite.dev/guide/api-plugin.html) 在默认代理与回退前挂载；代理参数遵循 [Vite server.proxy](https://vite.dev/config/server-options.html#server-proxy)。子路径部署分别核对 [Vite base](https://vite.dev/guide/build.html#public-base-path) 与 [Vue Router history](https://router.vuejs.org/guide/essentials/history-mode.html)。Nginx 配置生成需结合 [location / alias / try_files 文档](https://nginx.org/en/docs/http/ngx_http_core_module.html) 在实际安装版本运行 `nginx -t`。
