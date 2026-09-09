# 第一阶段本地整合验收

执行日期：2026-09-09。范围：本地源码及门户整合；没有推送、PR、main 合入、部署、业务库迁移或外部真实采集。

当前状态：relation、extraction、crawler 均为维护中，已启用系统为 0。门户与三套业务系统的验收分开记录。

## 环境与来源

- 仓库：`F:\Program\Java\course_design`；分支 `codex/integrate-systems`。
- Git 2.45.1.windows.1、Node.js 24.14.0、npm 11.9.0、PowerShell 7.6.5。
- 浏览器：本机 Edge；已有 Playwright 1.62.1，不属于门户运行依赖。
- 来源冻结 SHA、树和导入提交见 `import-records.json`；远端 Ye 在实施期间回退，见 `integration-baseline.md`。

## 已执行的第一阶段验证

以下命令除说明外均在仓库根目录执行。Windows 实测命令使用 `npm.cmd`，README 中 `npm` 为相同入口。

| 项目 | 精确命令 / 操作 | 已观察结果 |
| --- | --- | --- |
| 远端核定 | `git ls-remote --heads https://github.com/yzwyzw222/XSGXZSTP-KS.git main feature/Ye feature/Du feature/Luo` | 初次确认四个冻结 SHA；末次 Ye 回退已交叉验证并记录 |
| 来源导入 | `git subtree add --prefix=systems/relation 184f651b223efe9a07ea6d352146e5df34a537da`；Du/Luo 使用 `import-records.json` 对应 SHA 与目录 | 三次退出码 0，保留历史，共 936 个来源文件 |
| 逐次树与祖先 | 每次执行 `git rev-parse '<来源SHA>^{tree}'`、`git rev-parse HEAD:systems/<id>`、`git merge-base --is-ancestor <来源SHA> HEAD` | 三组树完全一致，三个祖先检查退出码 0；详见导入记录 |
| 新门户锁文件 | `npm.cmd --prefix portal install --package-lock-only --ignore-scripts --no-audit --no-fund --offline=false --registry=https://registry.npmjs.org --cache .local/npm-cache` | 退出码 0；仅创建门户锁文件，未升级子系统依赖 |
| 依赖恢复 | `npm.cmd --prefix portal ci --no-audit --no-fund --offline=false --registry=https://registry.npmjs.org --cache .local/npm-cache` | 退出码 0，安装 35 个包 |
| 依赖审计 | `npm.cmd --prefix portal audit --audit-level=high --offline=false --registry=https://registry.npmjs.org --cache .local/npm-cache` | 退出码 0，报告 0 个已知漏洞；仅针对门户锁文件，不代表历史子系统依赖已审计 |
| 自动化隔离 | `npm.cmd --prefix portal run test` | 8 项通过、0 失败、0 跳过；包含 84 个维护 API 方法/路径请求，下游计数保持 0 |
| 独立构建 | `npm.cmd --prefix portal run build` | 退出码 0；生成门户、三张独立维护页和状态快照；JS 66.83 kB（gzip 26.66 kB） |
| 来源复验 | `npm.cmd --prefix portal run check:source` | 三个原始树、当前子树、导入及当前祖先检查通过，子系统无适配差异 |
| 环境检查 | `.\scripts\Test-IntegrationEnvironment.ps1 -Mode Demo` | 18000 可绑定，子系统进程计划为 0；不要求 Java/数据库 |
| 生命周期 | `.\scripts\Test-IntegrationLifecycle.ps1` | 端口占用、重复启动、按系统停止、创建时间不匹配拒绝、数据标记保留、重复停止、开发模式全部通过 |
| 本地启动 | `.\scripts\Start-Integration.ps1 -System all -Mode Demo` | 门户在 127.0.0.1:18000 就绪；只登记 portal 进程，三个系统显示未启动 |
| Nginx 生成 | `npm.cmd --prefix portal run generate:nginx` | 退出码 0；生成忽略目录内配置；单测确认维护模式无上游 |
| 浏览器 | 下方完整命令 | 桌面/390px、普通导航、三个深链接及返回、POST API 503、配置读取错误与重试、关闭 JavaScript 的维护页通过；无页面脚本异常 |
| 原工作区保护 | `git -C F:\Program\Java\AACV_System status --short --branch` | 仍在 feature/Luo，未发现已跟踪改动；没有搬移业务代码或原本机配置 |

浏览器命令（复用本机已有工具，可在其他环境传入该环境已安装的 Playwright `index.mjs`）：

```powershell
node scripts/Test-PortalBrowser.mjs C:/Users/likecandy/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs
```

截图保存于 `.local/browser/portal-desktop.png` 与 `portal-mobile.png`，已逐张检查；不提交截图及浏览器临时数据。两次截图中首次捕获到入场动画早期状态，随后在截图时结束动画并复核最终可见布局。

## 新克隆复现

候选实现提交：`44674f4043f0aa992d61076550c5812a7ef6b1f5`。已在主仓库之外的独立 Git 工作树 `.local/reproduction` 复验；该目录只是位于本仓库忽略目录内，不使用主仓库的 node_modules、缓存、PID 或构建产物。

实际克隆命令：

```powershell
git clone --no-hardlinks --branch codex/integrate-systems F:\Program\Java\course_design F:\Program\Java\course_design\.local\reproduction
```

在 `F:\Program\Java\course_design\.local\reproduction` 依次执行依赖恢复，再执行各项验证：

```powershell
npm.cmd --prefix portal ci --no-audit --no-fund --offline=false --registry=https://registry.npmjs.org --cache .local/npm-cache
npm.cmd --prefix portal run test
npm.cmd --prefix portal run build
npm.cmd --prefix portal run check:source
.\scripts\Test-IntegrationEnvironment.ps1 -Mode Demo
.\scripts\Test-IntegrationLifecycle.ps1
```

以上命令均退出 0：新安装 35 包；8 项测试全过；门户构建完成；三个子树/祖先检查通过；Demo 与 Development 均可启动并停止；只有门户进程被启动，运行数据保留。复现过程中未安装子系统依赖，未配置数据库。完成后复现环境进程已停止。

第一阶段 P01 来源完整性、P02 独立构建启动、P03 全维护页面、P04 API 隔离、P05 配置及总入口、P06 本地候选新克隆复现均已通过。P06 中将来的远端 main 合入后复验尚未执行，因为本轮没有远端交付授权；不能把本地候选验收表述为 main 已合入。

最终文档补充只记录上述已发生结果；候选提交中的可执行代码、门户锁文件及三套来源树保持不变。

## 已遇到的环境失败

| 命令 / 操作 | 实际失败 | 处理与归因 |
| --- | --- | --- |
| 受限环境 `git subtree -h` | `couldn't create signal pipe, Win32 error 5` | Git for Windows 沙箱问题；正常权限环境能输出帮助，实际三次 subtree 成功 |
| `git switch -c codex/integrate-systems 13683199046793b1f16c02d2d58f899e02b2dcc1`（受限） | `.git/index.lock: Permission denied` | .git 只读策略；在用户已授权范围内经工具权限机制创建成功 |
| 默认 npm 锁文件生成 | `ENOTCACHED`、全局日志目录不可写 | 使用命令级显式联网和仓库 `.local/npm-cache` 成功；未改全局配置 |
| 受限环境 `node scripts/check-source.mjs` | `spawnSync git EPERM` | 工具阻止 Node 子进程；正常权限下来源检查通过 |
| PowerShell `Invoke-RestMethod` 访问 GitHub API | `Authentication failed, see inner exception.` | 该网络调用环境失败；改用已配置的 gh 只读 API 成功，不读取或展示凭据 |
| `gh api repos/yzwyzw222/XSGXZSTP-KS/branches/main/protection` | HTTP 404 | 完整保护配置不可核定；保留 PR 约定，不修改保护。可见分支状态检查列表为空 |

这些失败没有被当作测试成功，后续通过结果只针对已明确重跑的对应检查。

## 子系统未执行项

三套子系统只做源码导入且全部维护中，按方案不以它们业务测试通过作为本轮门槛。有效测试原样保留，未删除、跳过或放宽断言。下列命令本轮**未执行**，不声称成功或失败：

| 所属工作目录 | 尚未执行的原有入口 |
| --- | --- |
| `systems/relation/backend` | `.\mvnw.cmd verify`；已有后端测试不是原先只有上下文测试的历史状态 |
| `systems/relation/frontend` | `npm ci`、`npm run build`、`npm run test`；清单声明 Vitest，当前树未找到前端测试文件，不使用允许空测试选项 |
| `systems/extraction` | `.\mvnw.cmd verify`，上下文需要该系统独立基础服务 |
| `systems/extraction/frontend` | `npm ci`、`npm run build`；没有 test/lint 脚本，未虚构命令 |
| `systems/crawler` | `.\mvnw.cmd -f .\backend\pom.xml verify`；需要其独立 MySQL/Neo4j Testcontainers 条件 |
| `systems/crawler/frontend` | `npm ci`、`npm run test`、`npm run build`、`npm run test:e2e` |

三系统真实登录/退出、CSRF、跨系统会话、下载、独立数据库/Neo4j、业务核心流程以及已启用系统停后端恢复均未验收。门户中的 enabled 配置单测是基础配置验证，不是业务模拟或业务完成证明。

原 Luo 记忆的历史业务缺陷和运营未关闭项仍留在原工作区，本轮没有把它们修复或宣称复验。Nginx `nginx -t`、实际 Nginx 服务、Windows PowerShell 5.1、其他浏览器和公网部署均未验证。
