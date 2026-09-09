# 集成基线与已验证项目状态

核对日期：2026-09-09。此文件与 `development.md` 共同承担方案指定的集成项目记忆，只记录已核实的目录、状态和约定；具体测试证据见 `integration-acceptance.md`。

## 来源冻结点

仓库：`https://github.com/yzwyzw222/XSGXZSTP-KS.git`。

main 基线：`13683199046793b1f16c02d2d58f899e02b2dcc1`，初始仅跟踪 `README.md` 和 `11`。集成分支：`codex/integrate-systems`。

| 系统目录 | 最后同步来源 | 核定 SHA | 原始跟踪文件数 | 当前状态 |
| --- | --- | --- | --- | --- |
| `systems/relation` | `feature/Ye` | `184f651b223efe9a07ea6d352146e5df34a537da` | 153 | 维护中 |
| `systems/extraction` | `feature/Du` | `4833b228b5d8756a35433add01dce5072635cdb7` | 105 | 维护中 |
| `systems/crawler` | `feature/Luo` | `3a0127019052c182962d6630981e0acec2d66d53` | 678 | 维护中 |

共 936 个文件完整导入，无子系统适配修改。各次导入提交与树对象保存在 [`import-records.json`](import-records.json)；来源根树等于导入子树，并且来源提交是导入提交及当前 HEAD 的祖先。没有 squash、普通根布局 merge、历史改写、推送或远端资源修改。

## 实施期间的远端变化

首次 `git ls-remote`、独立克隆结果及 GitHub compare API 均指向上述 Ye `184f651…`，因此采用该提交导入。旧方案中的 `bf2494f77b96622489ed3b3ca5ee8b206c6c513b` 只有启动骨架；本轮核定提交包含 96 个后端 Java 源文件及 10 个后端测试类。

2026-09-09 16:18:04（UTC+8）再次执行 `git ls-remote --heads`，并用 `gh api repos/yzwyzw222/XSGXZSTP-KS/branches/feature%2FYe` 交叉验证：远端 Ye 已回到 `bf2494f…`。`git merge-base --is-ancestor bf2494f… 184f651…` 返回 0，确认是向已导入历史的祖先回退，原因未知。本轮不自动删除已核定源码或重写导入历史。

**后续同步 Ye 前先核对分支负责人确认的来源历史。** 当前冻结 SHA 不再等于远端 Ye 的末次观察值；不能直接声称这是交付时远端 Ye 最新树，也不能使用普通 subtree 增量命令假装这一回退没有发生。Du、Luo、main 的末次观察值仍与冻结点一致。

## 已核对源码与限制

| 系统 | 当前源码证据 | 保持维护的原因 |
| --- | --- | --- |
| relation | 后端接口、服务、安全配置与测试已增加；Java 17 / Spring Boot 3.5.16，Vue 3.5.42 / Vite 8.2.2；路由仍为 `createWebHistory()`，开发端口仍为 5173 | 未完成子路径、Session/CSRF、存储隔离及真实业务验收；另有远端回退差异 |
| extraction | 根目录 Maven 工程、53 个后端 Java 源文件、1 个上下文测试；Java 21 / Spring Boot 4.1.1；前端 Vite 6，清单没有 test/lint 脚本 | 仍使用原始根路径与开发端口、示例账号配置；未建立独立接入环境 |
| crawler | backend/frontend/deploy/tools 布局、368 个后端 Java 源文件；Java 21 / Spring Boot 4.1.1，Vite 8.2.2；保留 Vitest/Playwright 与后端测试、`docs/openapi.yaml` | 新目录下真实账号、隔离库、图数据库及路径/认证适配尚未验收 |

本轮未读取任何原 `.env`，未连接原业务库，未运行子系统启动、数据库初始化或迁移。来源中可识别的示例配置原样保留；这些默认值不构成集成环境凭据，不得以其直接启用系统。没有把原本地被忽略的交接/截图/运行目录搬入仓库。

## 仓库规则

所有已导入来源树与 main 中均未发现额外 AGENTS.md 或 `.github/workflows`。适用工程约束来自本任务提供的 AGENTS.md 指令。main README 的旧分支命名及根布局普通 merge 示例已在集成 README 改为当前映射及 subtree 流程。

GitHub 分支 API 返回 main `protected: true`，`required_status_checks.checks` 和 `contexts` 为空；规则查询返回空列表，仓库允许 merge commit。完整 `/branches/main/protection` 接口返回 HTTP 404，无法核定全部管理配置，不能解读为 main 没有保护。没有关闭、弱化或绕过任何检查。后续真正创建 PR 前仍需重新核对可见检查与保护。

## 项目记忆同步

实施前读取原方案、原 `docs/development-handoff.md`、`docs/known-limitations.md`、`docs/system-design.md` 相关章节，以及 main README、Ye 最新设计与实际源码。原工作区现状记忆保持原样。

新克隆 main 没有独立项目记忆文件。按既定方案新增并跟踪本集成基线、开发说明、实施边界和验收记录，没有另建 MEMORY.md 或其他记忆体系。

发现并解决的记忆差异：旧方案 Ye 骨架结论由最新核定源码更新；main 旧分支命名由真实分支与目录替换；Nginx 预期与本机环境不一致，明确记录 Vite 本地演示适配；远端 Ye 的新回退记录为待核对事实，没有未经确认改变本地源码。
