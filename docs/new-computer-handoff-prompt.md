# 新电脑 Codex 接管提示词

优先从远程仓库克隆 `feature/Luo`；如使用历史压缩包迁移，则先校验 SHA-256 并解压。在 Codex 中打开 `AACV_System` 项目目录后，复制下面的完整提示词开始接管。

```text
你现在接管一个从旧电脑迁移过来的 AACV System 本地开发项目。请在当前项目目录内完成“新电脑开发环境恢复、启动和验证”，完成后停止，不要自动进入新功能开发。

一、权限与范围

1. 本次只允许检查和恢复本地开发环境，包括：核对源码与 Git、检查开发工具、安装项目依赖、配置本机开发数据库、启动本地 Neo4j/后端/前端、执行现有测试和健康检查。
2. 不要实施阶段 8，不要开发新功能，不要重构业务代码，不要部署服务器，不要发布，不要修改远程资源。
3. 不要执行 git commit、git push、git reset --hard、git clean、强制切换、历史重写或其他可能丢失文件的操作。
4. 不要删除或覆盖已有数据库、Docker 卷、配置、凭据或用户文件。若发现同名数据库或已有 .git，先只读检查并报告，不得自行重建。
5. 不要读取、打印或记录真实密码、Token、Cookie、私钥等秘密。普通本地开发凭据只允许由用户本人写入受 `.gitignore` 保护的根目录 `.env`；阶段8脚本仍只允许通过当前 PowerShell 进程的 `Get-Credential` 注入。
6. 不要把历史验收结果当作新电脑的当前测试结果；只有实际运行并观察到的命令才可以报告为通过。

二、必须先读取和核对的资料

1. 查找并读取当前目录适用的 AGENTS.md；若没有，明确报告“未找到仓库内 AGENTS.md”，不要擅自创建新的指令体系。
2. 完整阅读：
   - docs/development-handoff.md
   - README.md
   - docs/development-plan.md
   - docs/requirements-analysis.md
   - docs/system-design.md
   - docs/stage7-acceptance.md
3. 核对 docs/openapi.yaml、docs/authorization-matrix.md、backend/pom.xml、frontend/package.json、frontend/package-lock.json、deploy/compose.yaml、application.yml 和 Flyway 迁移目录。
4. 以当前文件、配置和实际运行结果为准；文档与实现冲突时先查清证据并报告。

三、已知交接基线

1. 阶段0至7已实现；阶段8容量和四项P95已有历史通过记录，故障、备份恢复及真实来源联合验收以docs/stage8-acceptance.md为准。后续优化进度和本轮验证以docs/optimization-acceptance.md为准，不将历史结果作为当前通过证明。
2. 后端基线为 JDK 21、Spring Boot 4.1.1、Maven Wrapper 3.9.16；前端为 Vue 3、TypeScript、Vite，使用 package-lock.json 和 npm ci。
3. MySQL是唯一业务权威数据库，默认数据库名aacv_system。兼容基线8.0.42，历史主机实测8.0.41；当前版本需实际核验，迁移以backend/src/main/resources/db/migration中的连续文件为准。
4. Neo4j 5.26 Community 通过 Docker Compose 运行，只保存可从 MySQL 重建的图投影。
5. 远程仓库为 https://github.com/yzwyzw222/XSGXZSTP-KS.git；当前开发分支为 feature/Luo，并应跟踪 origin/feature/Luo。
6. `.env.example` 应由 Git 跟踪，真实 `.env`、AACV_System-history.bundle、SHA256SUMS.txt 和根目录重复交接提示词不应进入提交。历史压缩包中的 bundle 仅作为远程不可用时的恢复备份。

四、执行顺序

严格按照 PLAN → EXECUTE → TEST → DELIVER 执行。先完成只读检查并用简体中文给出具体实施计划，再继续操作；除非遇到数据库覆盖、凭据、破坏性操作或实质性歧义，否则不需要等待额外批准。

1. 检查当前工作目录、文件结构、Git 状态和现有未提交文件。
2. 如果 .git 已存在，核对 origin、feature/Luo 及上游关系，不要覆盖现有工作区；如果 .git 不存在，优先把远程 feature/Luo 克隆到新的空目录，再与解压目录只读比对。不得在含未跟踪源码的目录中强制检出；只有远程不可用时才按 docs/development-handoff.md 使用历史 bundle 恢复。
3. 检查 git、JDK、Node.js、npm、Docker Desktop Linux Engine、Docker Compose、MySQL80 和 Microsoft Edge。Docker 必须同时有 Client 和 Server。
4. 检查 aacv_system 数据库是否存在：
   - 不存在时，可以按交接文档创建空的 utf8mb4 开发数据库；
   - 已存在时，不得删除、清空或覆盖，先报告状态；
   - 没有明确提供独立加密备份时，不迁移旧电脑业务数据。
5. 如果根目录 `.env` 不存在，复制 `.env.example` 创建它，并提示我在本机编辑器中自行填写。不要读取或回显 `.env` 的值；阶段8脚本需要密码时仍提示我在本机 `Get-Credential` 窗口中输入。
6. 使用现有锁文件执行 npm --prefix .\frontend ci；不要升级依赖，不要改写 package-lock.json。
7. 使用 deploy/compose.yaml 校验并启动 Neo4j 5.26；不要使用历史 Neo4j 4.4 镜像，不要执行 docker compose down -v。
8. 启动后端和前端，检查 8080、5173、7474、7687 端口及三个 Actuator 健康端点。
9. 实际执行并记录：
   - .\mvnw.cmd -f .\backend\pom.xml verify
   - npm --prefix .\frontend run test
   - npm --prefix .\frontend run build
   - npm --prefix .\frontend run test:e2e
   - docker compose --env-file .\.env -f .\deploy\compose.yaml config --quiet
10. 测试失败时区分：当前修改引入、原有失败、缺少依赖、Docker/权限限制、外部网络或配置问题。不要删除、跳过或弱化测试。
11. 完成登录页、空数据状态、成果目录、统计、运行监控和权限入口的最小人工检查。路由模拟 Playwright 不得冒充真实账号和真实持久化数据库联合验收。
12. 最后检查完整状态，确保没有遗留调试文件、临时压缩包、真实凭据、依赖升级或无关修改，然后停止，等待我的下一条开发任务。

五、交付要求

最终必须使用简体中文，并包含以下栏目：

## 任务总结
说明新电脑恢复到什么程度，以及是否满足本地继续开发条件。

## 变更文件
只列实际修改文件；没有修改则写“无文件修改”。

## 验证结果
列出实际执行的完整命令和观察结果，区分通过、失败、跳过与受阻。

## 项目记忆
说明读取了哪些项目记忆或设计文档、是否更新、是否发现与当前代码不一致。

## 风险、限制与假设
明确未验证的真实账号、业务数据迁移、阶段 8、备份恢复和部署边界。

## 用户需执行的操作
列出仍需我手工完成的凭据输入、软件安装、Docker 启动或其他步骤。不得要求我在聊天中发送密码。
```

如果新电脑尚未解压项目，应先按照[开发环境迁移交接手册](./development-handoff.md)第 6 节完成压缩包校验与解压，再使用上述提示词。
