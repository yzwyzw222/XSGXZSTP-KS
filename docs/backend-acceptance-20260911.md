# Docker 存储修复与后端验收（2026-09-11）

## 范围与当前状态

本次恢复 Windows Docker Desktop 的损坏元数据和关联镜像存储，并对当前 relation、crawler 后端进行验收。保留实施前全部未提交改动；不提交或推送，不替换正在运行的业务后端，不重置本机 MySQL 项目库。

Docker 引擎及两个项目图库已恢复，后端验收完成。crawler 默认完整套件的 264 项测试全部执行，修复全部失败项并定向复测通过，另通过 2 项查询/恢复专项；relation 的 81 项测试与打包通过。两套 Java 后端最终覆盖 347 项不同测试，另通过 18 项统一网关测试和 2 项来源适配测试。保留首轮失败记录，不将最终覆盖结果表述为所有用例在同一次命令中一次通过。

## 存储恢复证据

- Docker Desktop 为 4.72.0，恢复后的 Engine 为 29.4.2，containerd 为 2.2.3。WSL 数据根仍为 `E:\docker\data`，系统盘注册目录为 `E:\docker\data\main`。
- 原始快照数据库的第 13 页全为零；原 containerd 主索引和 Docker 卷索引通过官方 bbolt 检查。根据主索引、独立父子索引和磁盘目录用量，只重建副本中的该页。恢复了快照 ID 22 与父快照 21 的关系；36 条快照记录、32 条父子链接均经验证，其余记录保持原值。坏页原时间戳不可读取，恢复时间戳取自主索引中对应快照的记录。
- 只读 ext4 检查发现额外的扩展属性和目录结构损坏，因此没有直接采用只修复数据库文件的方案。先复制整盘并校验 SHA256，再在整盘副本中回放日志和执行文件系统修复；原运行盘和迁移前备份均保留。
- 文件系统修复涉及镜像层 2、11、13，以及既有 Neo4j 4.4 容器层的扩展属性和工作目录。三个镜像层通过与现有 containerd 相同版本的官方解包实现重新生成，保留权限、所有者、链接和 overlay 删除语义；压缩内容及解压内容摘要均与原记录一致。原目录与 `lost+found` 中找回的条目保留。
- 内容存储中发现一个 Neo4j 4.4 镜像 blob 的 SHA256 不匹配。按原摘要 `02f2615db6b2a3858afa66f41610d194391de5d8421b1114af99a50c87871a99` 从 Docker Hub 获取同一内容并验证，没有更新镜像版本。
- 修复后、启动 Docker 前，全部 Docker 数据卷按文件路径和内容计算的聚合 SHA256 仍为 `4808ee46286f4968165a02a443c3770b93aa59371f172b2902ecf90ebac9ae30`，与修复前完全一致。这证明本次修复未改变卷内文件，不将其表述为对既有业务数据完整性的历史证明。
- 最终副本通过 `e2fsck -f -n`、全部镜像 blob 摘要检查及 `bbolt check`，切换磁盘前后整盘 SHA256 一致。清理失效运行时套接字采用目录改名保留方式，未清空 Docker 或删除业务卷。
- 恢复后两个项目 Neo4j 容器使用原容器 ID、镜像 ID 和数据卷，健康状态为 `healthy`，HTTP 端口 `27471`、`27473` 均返回 200。通过容器已有认证环境执行只读查询，relation 为 68 个节点/262 条关系，crawler 为 32 个节点/90 条关系，未输出或修改凭据。旧的 `attack-verify-neo4j-1` 保持原停止状态，未将其他项目的服务纳入本次验收。

本机恢复材料不随 Git 克隆迁移：

| 路径 | 用途 |
| --- | --- |
| `E:\docker\backup-20260911\docker_data.vhdx` | 迁移前原始整盘备份，SHA256 已重新确认 |
| `E:\docker\repair-20260911\docker-data-before-switch.vhdx` | 本次切换前的完整运行盘 |
| `E:\docker\repair-20260911\switch-result.json` | 修复盘、回滚盘和切换校验记录 |
| `E:\docker\repair-20260911\candidate-validation.json` | 快照页恢复、父链检查和应用状态 |
| `E:\docker\repair-20260911\blobs\` | 原损坏 blob 和通过摘要验证的恢复内容 |
| `E:\docker\migration-20260911.json` | 数据盘迁移与当前引擎恢复状态 |
| `.local/docker-repair-20260911/` | 本次使用的临时恢复工具、脚本、修改前快照和验收报告 |

这些备份没有自动删除。若需回滚，应先完全退出 Docker 并卸载相关 WSL 磁盘，避免两个相同标识的 VHD 同时挂载。这里记录的是本次已核对磁盘标识后的操作，不能把本机 `/dev/sdd` 直接套用到其他机器。

## 后端验收环境

使用 JDK 21；Java 临时目录在命令进程内设为 `.local/docker-repair-20260911/temp`，未修改系统级环境变量。测试 JVM 使用 `-Xmx512m -XX:MaxMetaspaceSize=256m`。

crawler 的集成测试沿用仓库 Testcontainers 配置，创建独立 MySQL 8.0.42 和 Neo4j 5.26 Community。没有对 `127.0.0.1:3306/course_crawler` 或 `course_relation` 执行测试写入、重置或迁移。

relation 的既有测试使用 `local` profile，并依赖样例数据。本次在独立、仅绑定 `127.0.0.1` 的临时 MySQL/Neo4j 容器中执行现有 `mysql-schema.sql` 和 `sample-data.sql`，通过进程环境覆盖测试连接。先用当前源码打包启动临时后端，通过真实 HTTP 登录及带 CSRF 的图同步接口完成投影，确认 68 个图节点和零待处理/失败事件，再停止临时后端并运行完整 `verify`。最终按本次标签核对归属后清理这两个临时容器及其临时卷。

## 命令与结果

命令中的 `$argLine` 为上述 JVM 内存设置及本次 E 盘临时目录；完整日志位于本机 `.local/docker-repair-20260911/reports/`。relation 的复现需要先准备上述隔离数据库，不能将测试直接指向业务库。

| 工作目录与实际命令 | 结果 |
| --- | --- |
| crawler：`.\mvnw.cmd -f backend/pom.xml "-DargLine=$argLine" '-Dtest=AuthorImportIntegrationTests,ScholarImportParserTests,CatalogExportFilterSqlTests,OpenApiDocumentTests,SecurityIntegrationTests,FlywayMigrationTests,GraphProjectionIntegrationTests,GraphQueryIntegrationTests' '-Dspring.test.mockmvc.print=NONE' test` | 64 项，63 项通过；唯一失败为旧图类型数量断言，随后修正并复测 |
| crawler：`.\mvnw.cmd -f backend/pom.xml "-DargLine=$argLine" '-Dtest=GraphQueryIntegrationTests' '-Dspring.test.mockmvc.print=NONE' test` | 10 项通过，零失败、错误和跳过 |
| crawler：`.\mvnw.cmd -f backend/pom.xml "-DargLine=$argLine" '-Dspring.test.mockmvc.print=NONE' verify` | 默认完整套件共 67 个测试类、264 项；261 项通过，3 项因旧预期失败，零执行错误和跳过；以下记录对应修复后的复测 |
| crawler：`.\mvnw.cmd -f backend/pom.xml "-DargLine=$argLine" '-Dtest=AacvSystemApplicationTests,RenderingSampleDataSqlTests,OpenAlexBatchOrchestrationIntegrationTests,OptimizationQueryPerformanceIT,OptimizationRecoveryIT' '-Dspring.test.mockmvc.print=NONE' verify` | 全部 3 项失败用例复测及 2 项补充验收通过，共 5 项，零失败、错误和跳过；后端打包通过 |
| relation/backend：`.\mvnw.cmd -DskipTests package` | 隔离 HTTP 验收前的准备打包通过，不作为测试通过证据 |
| relation/backend：`.\mvnw.cmd "-DargLine=$argLine" '-Dspring.test.mockmvc.print=NONE' verify` | 12 个测试类、81 项测试和打包全部通过，零失败、错误和跳过 |
| 根目录：`node scripts/check-source.mjs` | relation、crawler 的来源与适配检查通过；完整历史检查仍因 retired scholar 的 Git 对象 `f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d` 缺失而失败，未修改历史证据或绕过检查 |
| 根目录：`node --test scripts/lib/source-adaptations.test.mjs` | 2 项通过 |
| 根目录：`node --test scripts/tests/*.test.mjs` | 18 项统一网关认证、路由、日志与配置测试通过 |
| 根目录：`git -c core.safecrlf=false diff --check` | 通过，无空白错误 |

最终 Surefire 报告包含 crawler 69 个测试类/266 项、relation 12 个测试类/81 项，均无剩余失败、执行错误或跳过。此汇总由全量执行和上述定向复测组成；没有删用例、放宽业务校验或将失败用例标记为跳过。

十万条合成成果数据的 SQL 验收使用 MySQL 8.0.42、单并发，每组预热后采样 5 次，并执行 `EXPLAIN ANALYZE`。结果仅代表本机隔离 SQL 测量，不代表 HTTP 负载测试：

| 场景 | 本轮 P95（毫秒） |
| --- | ---: |
| 目录组合筛选计数 | 827.66 |
| 目录组合筛选分页 | 861.37 |
| 深分页 | 245.43 |
| 条件统计 | 1063.86 |
| 覆盖率统计 | 945.31 |
| 机构合作统计 | 807.16 |

故障恢复专项验证图服务中断后查询明确失败、MySQL 中断时不接纳导出任务、依赖重启后接口恢复；逻辑备份恢复核对 76 张表行数、12 条成果和机构旧名称证据，随后从空图库重建 12 条成果投影并通过登录和查询验证。本次合成样本恢复与图重建耗时 14.146 秒，不作为生产 RPO/RTO 承诺。原始报告位于 `systems/crawler/backend/target/optimization-query-current.json`、`optimization-recovery.json`，本机验收目录保留副本。

## 本次代码修正

仅修改四项与当前功能不一致的测试契约，没有为通过测试修改业务实现或关闭验证：

- `GraphQueryIntegrationTests`：图类型数量从 11 更新为 13，明确断言关系类型中存在 `SUPERVISED` 和 `PRODUCED_AT`，保留研究者不可编辑的 403 验证。
- `AacvSystemApplicationTests`：迁移数量从 14 更新为当前 17，并增加无待执行迁移的断言；保留 Flyway 校验、数据库连接、事务管理器及三个健康组的真实 HTTP 检查。
- `RenderingSampleDataSqlTests`：样例库初始化的迁移数量从 15 更新为当前 17，并增加无待执行迁移的断言；保留全部样例行数、历史数据兼容、审计动作和重复执行幂等检查。
- `OpenAlexBatchOrchestrationIntegrationTests`：明确验证 `quota-resume` 自动触发器不再注册，与已移除的 `QuartzQuotaResumeConfiguration` 一致；保留历史批次检查点、配额、手动恢复、取消、失败审计和重试断言。

`docs/source-adaptations.json` 同步这四个文件的原始来源和修改后哈希。其他既有未提交源码、锁文件、历史删除保持原状。

## 本轮项目文件清单

| 文件 | 本轮变更 |
| --- | --- |
| `systems/crawler/backend/src/test/java/com/aacv/system/AacvSystemApplicationTests.java` | 更新迁移计数并验证无待执行迁移 |
| `systems/crawler/backend/src/test/java/com/aacv/system/graph/GraphQueryIntegrationTests.java` | 验证当前图类型与新增关系，保留权限断言 |
| `systems/crawler/backend/src/test/java/com/aacv/system/infrastructure/database/RenderingSampleDataSqlTests.java` | 更新样例库迁移计数并验证无待执行迁移 |
| `systems/crawler/backend/src/test/java/com/aacv/system/source/infrastructure/openalex/OpenAlexBatchOrchestrationIntegrationTests.java` | 验证不再注册旧自动恢复触发器 |
| `docs/source-adaptations.json` | 同步上述测试的来源及内容哈希 |
| `docs/author-import.md` | 更新 Docker 阻塞解除及最终后端验收记录 |
| `docs/development.md` | 同步修复后的本机运行状态及隔离验收约定 |
| `docs/integration-baseline.md` | 同步存储恢复、作者导入当前边界及验收边界 |
| `docs/backend-acceptance-20260911.md` | 新增本次修复、验收证据、命令及限制 |

本机忽略目录中的恢复脚本、临时工具、日志和前后快照不作为项目依赖或发布内容；E 盘 VHD 与修复记录的变更见前述恢复材料表。

## 验收边界

知网导入依据用户确认的表头和合成 XLSX/XLS/CSV 测试资料，尚未取得真实导出文件。relation 外部采集与 LLM 测试使用已有模拟服务；crawler 已退场的在线采集专项 `OpenAlexOnlineAcceptanceIT` 不属于当前作者导入功能验收，未重新启用旧采集功能或访问真实模型服务。

本次测试和临时后端使用隔离库，不代表已将当前源码部署到正在运行的业务实例；真实业务库未执行 V17 升级。后续业务实例升级步骤仍见 [作者导入说明](author-import.md)。

## 项目记忆同步

实施前读取 `docs/integration-baseline.md`、`docs/development.md`，结合 `docs/author-import.md`、配置、工作区及真实 Docker 状态核对。两份指定记忆记录了迁移后引擎仍不可用的状态，本次依据引擎响应、原卷挂载、容器健康及离线校验更新为恢复后的状态；作者导入文档中的待验收记录同步本次执行结果。未新建个人记忆或另设项目记忆系统。
