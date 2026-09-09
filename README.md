# 学术系统统一门户（XSGXZSTP-KS）

本分支完成第一阶段的本地源码整合与统一门户。三个业务系统当前均为**维护中**；本地门户可独立运行。这里的“源码与门户整合”不代表三个系统业务全部可用。

当前集成分支为 `codex/integrate-systems`，尚未推送或合入远端 main。原 `AACV_System` 工作区、业务库、凭据和运行数据不参与迁移。

## 系统与目录

| 系统 | 维护来源 | 本仓库目录 | 固定入口 | 状态 |
| --- | --- | --- | --- | --- |
| 学术关系知识图谱构建平台 | `feature/Ye` | `systems/relation` | `/relation/` | 维护中 |
| 学术多源实体抽取与学术知识图谱构建 | `feature/Du` | `systems/extraction` | `/extraction/` | 维护中 |
| 学术成果爬虫及可视化系统 | `feature/Luo` | `systems/crawler` | `/crawler/` | 维护中 |

`portal/` 独立管理 Vue/Vite 清单与锁文件；`scripts/` 提供检查和启停；`deploy/systems.json` 是唯一接入状态配置；`docs/` 跟踪集成基线、开发说明和验收记录。各系统的 Maven Wrapper、内部目录和前端锁文件完整保留，不建立统一依赖树。历史文件 `11` 原样保留。

## 本地启动

需要 Node.js **22.12.0 或更新版本**、npm 和 PowerShell 7。本轮环境为 Node.js 24.14.0、npm 11.9.0、PowerShell 7.6.5。门户无需 Java、MySQL、Neo4j 或任何子系统构建产物。Windows PowerShell 5.1 未纳入本轮兼容验收。

在仓库根目录依次执行：

```powershell
npm --prefix portal ci
npm --prefix portal run build
.\scripts\Test-IntegrationEnvironment.ps1 -Mode Demo
.\scripts\Start-Integration.ps1 -System all -Mode Demo
```

打开 <http://127.0.0.1:18000/>。`all` 只编排已启用系统；当前仅启动门户。维护页由门户直接响应，访问三个系统路径返回带“返回门户”链接的 HTTP 503 页面；其 `/api` 及 `/api/v1/...` 返回 JSON 503，绝不转发。

停止本仓库进程：

```powershell
.\scripts\Stop-Integration.ps1 -System all
```

停止仅使用本仓库 `.local/integration/processes.json` 中的归属记录，并核对目录、PID、创建时间、可执行文件和命令行。不按进程名或端口批量结束，不操作 MySQL、容器、卷或业务数据。重复启动明确失败，不自动换端口。运行日志位于 `.local/integration`，不进入 Git。

只开发门户也可以在前台运行：

```powershell
npm --prefix portal run dev
```

此时用当前终端的 Ctrl+C 停止。直接 `npm` 启动不写统一进程记录，不能与总入口同时占用 18000。

## 验证

停止门户后依次执行：

```powershell
npm --prefix portal run test
npm --prefix portal run check:source
.\scripts\Test-IntegrationEnvironment.ps1
.\scripts\Test-IntegrationLifecycle.ps1
```

来源检查当前要求三个子树与首次导入完全一致。后续首次适配时，应保留原始树证据，增加经过审阅的适配差异验证，而不是删除来源完整性或祖先检查。

完整测试结果、未执行业务用例和可选浏览器检查见 [验收记录](docs/integration-acceptance.md)。当前没有启用系统；不要将门户测试当作子系统业务验收。

## 从本地候选提交复现

远端尚不存在本轮集成分支。可在另一个**不存在或空的**目标目录独立克隆当前本地分支，再执行上面的安装、构建和启停命令：

```powershell
git clone --no-hardlinks --branch codex/integrate-systems F:\Program\Java\course_design F:\Program\Java\course_design_review
```

不要覆盖已有目录。新克隆不包含 `.local`、凭据、缓存或原业务数据。根级 Markdown 不被忽略，因此所需说明会随克隆交付。

## 后续维护与合入

先修复单个子系统，再通过对应 subtree 同步、完成路径及认证隔离、真实接入验收，最后更新 `deploy/systems.json`。完整方法见 [开发说明](docs/development.md)。仅把状态改为 `enabled` 会因缺少运行配置和验收记录而失败。

**2026-09-09 16:18（UTC+8）复查发现 Ye 远端回到旧提交。** 本地保留实施开始时核定的 `184f651…`，不自动退回骨架。后续同步前必须核对差异，详见 [基线与远端变化](docs/integration-baseline.md)。

main 保留 PR 合入约定；本轮不推送、不创建 PR、不修改保护。后续获授权后再提交 PR，并使用保留来源祖先关系的 merge commit。不要 squash/rebase 整个集成历史，不要普通合并旧布局的 `feature/*` 分支。

