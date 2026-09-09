# 集成基线与已验证项目状态

核对日期：2026-09-09。本文件与 `development.md` 是仓库指定的集成项目记忆，测试证据见 `local-runtime-acceptance.md`；第一阶段历史仍保存在 `integration-acceptance.md`。

## 当前来源与状态

仓库：`https://github.com/yzwyzw222/XSGXZSTP-KS.git`。main 基线 `13683199046793b1f16c02d2d58f899e02b2dcc1`；当前本地分支为 `dev`，跟踪 `origin/dev`。按用户授权，从原集成分支的 `350ab2b` 整理现有工作区，系统导入、统一认证与运行接入、门户界面已分主题提交并快进推送。原 `codex/integrate-systems` 分支保留，未改写历史、合入 main 或创建 PR。

| 系统 | 核定来源 | 完整 SHA | 原始文件数 | 当前接入 |
| --- | --- | --- | --- | --- |
| relation | 已导入的 feature/Ye 完整版 | `184f651b223efe9a07ea6d352146e5df34a537da` | 153 | 已启用 |
| extraction | feature/Du | `4833b228b5d8756a35433add01dce5072635cdb7` | 105 | 已启用 |
| crawler | feature/Luo | `3a0127019052c182962d6630981e0acec2d66d53` | 678 | 已启用 |
| scholar | Li | `f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d` | 136 | 已启用 |

本次执行 `git fetch --no-tags origin`，Du、Luo 未变；发现 Li 完整系统并导入。`feature/Li`、`feature/Ma` 仍是仅有历史文件 `11` 的占位分支，没有可运行的第五或第六套系统。

四个来源共 1072 个原始文件。前三套保留原 subtree 导入提交与来源祖先关系；Li 通过 archive 在新目录导入，保留全部原始文件及已记录适配，随后纳入本地提交 `4f47a3e`。archive 导入没有建立 Li 来源提交的祖先关系，不将其表述为 subtree 合并。原始证据记录在 `import-records.json`，必要适配记录在 `source-adaptations.json`，来源检查覆盖原始文件、新增文件和适配差异。

## Ye 远端回退

第一阶段开始时核定并导入 `184f651…`；同日 16:18 已观察到远端回退。本次 fetch 再次明确显示强制更新：`184f651… -> bf2494f…`。完整回退目标为 `bf2494f77b96622489ed3b3ca5ee8b206c6c513b`，它是已导入版本的祖先。

本轮保留已核定的完整本地源码并完成运行适配，没有以骨架覆盖现有功能。不能将 relation 表述为当前远端 Ye 最新树。后续同步需先核对来源历史，远端回退本身没有被本地修改或撤销。

## 已确认的运行架构

- 门户 Vue/Vite 独立管理，统一登录地址 127.0.0.1:18000/login。四系统保留独立子路径、JVM 和数据库；统一认证沿用 crawler 账号，网关提供根路径 HttpOnly `PORTAL_SESSION`，其余三个后端在 integration profile 下逐请求确认统一身份。原子系统登录入口已退出集成流程。没有统一父 POM、npm workspace 或跨系统数据流水线。
- relation：Java 17 编译目标 / Spring Boot 3.5.16；extraction、crawler：Java 21 / Spring Boot 4.1.1；scholar 实际应用在 `web/backend`，Java 21 / Spring Boot 3.4.4。整合运行使用 JDK 21。
- 四个独立 MySQL 8.0.42 容器和三个独立 Neo4j 5.26 容器属于 `course-integration`。scholar 的图谱通过 SQL/JPA 数据组装，未配置无实际用途的 Neo4j 实例。
- 所有端口仅绑定本机，实际端口与隔离目录见 `development.md`。新库未导入历史学术数据，界面空状态是真实结果；门户装饰统计保留演示标识。
- 明文随机运行凭据仅在忽略目录 `.local/integration-runtime`，目录 ACL 限制当前 Windows 用户。启动参数和版本化文档不包含凭据，未读取原工作区环境文件。

## 本轮适配与修复

前端 Router/API base、返回门户、关系与抽取会话恢复；四系统上下文/Cookie 隔离、真实就绪探针和可独立恢复的启动流程；Li 新入口与原布局适配。extraction 补齐当前 Boot 版本的 Flyway Starter，保留原迁移和 schema validation；恢复错误 API 的 400/404 语义。scholar 修复退出空实现，注册既有 fcose 并处理空图。

用户原有门户视觉改动在实施前已记录差异与文件哈希；本轮仅在其基础上扩展第四张卡片和两列布局，原图片、图标、组件和主题内容保留。未操作 `AACV_System`，未迁移旧数据库。

## 记忆一致性

提交整理时通过 `git status`、`git ls-remote` 与实际推送结果核对分支状态，将本文、开发说明和 README 中的“尚未提交、未推送”更新为 `dev` 的分主题提交与推送状态；历史运行验收保留当时记录，并注明后续提交状态。来源与适配校验再次通过，原始业务代码和已有锁文件内容保持不变。

统一登录改造前，本文和开发说明记录的“各系统独立认证、退出互不影响”与当时源码一致。2026-09-09 本次根据用户确认改用 crawler 统一账号后，已同步入口、会话作用域、权限映射、账号管理位置与认证服务依赖；原接入验收保留为历史证据，当前认证契约以 [统一登录说明](unified-login.md) 为准。

实施前读取本基线、开发说明、第一阶段方案/验收与导入记录，结合 Git 状态和实际源码核对。旧记录的“三套系统全部维护中、runtime 均为空、无子系统适配”已被四系统实际运行与验收取代；原先未纳入的 Li 完整系统现已记录。第一阶段证据保留并明确标记历史。

没有另建 MEMORY.md 或其他记忆体系。外部 LLM、真实外部采集、历史数据迁移、全量业务回归、Nginx 本体与公网部署仍未验证，不把本地启动通过等同于这些能力已验收。
