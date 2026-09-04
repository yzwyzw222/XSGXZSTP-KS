# 阶段3 OpenAlex 单源采集闭环验收记录

## 1. 验收范围

本记录对应 `docs/development-plan.md` 的批次3.0至3.8及第8.5、8.6节。验收只覆盖OpenAlex单源采集闭环，不包含Crossref、多源数据治理、Outbox、Neo4j业务图、业务前端或服务器部署。

验收期间保留了当前 `dev` 工作区全部未跟踪文件和用户凭据；未执行Git清理、重置、提交、分支、标签或推送。测试使用独立Testcontainers数据库，没有清理、重建或覆盖本机 `aacv_system`。

## 2. 批次结果

| 批次 | 实施结果 | 定向验证结果 |
| --- | --- | --- |
| 3.0 | 复核阶段2、Docker、MySQL、Neo4j、前后端和OpenAlex接入门禁 | 阶段2后端25项、前端2项和构建通过；Compose、Neo4j及本地三个健康组通过；OpenAlex匿名轻量请求HTTP 200 |
| 3.1 | 增加Spring Batch和Quartz，建立 `source`、`crawl`、`ingestion`、`catalog` 边界、权限及契约 | 5项模块、权限和契约定向测试通过；依赖树确认Spring Batch 6.0.5、Quartz 2.5.2 |
| 3.2 | 增加Flyway V4至V7及官方Batch、Quartz元数据结构 | 空库及V1至V3升级两条MySQL 8.0.42迁移路径共2项通过，均通过Flyway `validate` |
| 3.3 | 实现固定OpenAlex数据源、受控配置、探测、有界任务、触发和每日计划API | 16项服务与API契约测试、应用上下文和MySQL持久化回环通过 |
| 3.4 | 实现受控OpenAlex `/works` 客户端、不透明游标、解析、超时、大小边界及有限重试 | 6项客户端与解析器定向测试通过；真实匿名小请求成功 |
| 3.5 | 实现原始快照、标准化、确定性身份、幂等关系和单页事务 | 3项标准化测试及1项MySQL综合集成测试通过，覆盖重复、单条隔离和系统错误回滚 |
| 3.6 | 实现状态机、Chunk后控制、恢复、Quartz计划、异常重启协调及有限失败重试 | 15项状态与控制测试、应用上下文和1项Batch/Quartz/MySQL编排集成测试通过 |
| 3.7 | 实现八类成果筛选、详情与实体入口、失败分页和90天Payload清理 | 1项MySQL综合集成测试通过，覆盖目录、追溯、失败分页和清理幂等性 |
| 3.8 | 补齐边界、安全、契约、恢复及在线闭环测试并同步文档 | 46项核心回归、12项健康/安全/OpenAPI集成测试及1项真实OpenAlex在线闭环测试通过 |

## 3. 完整验收

- `docker version`：Client和Server均为29.4.2，Docker Desktop Linux Engine可用。
- `docker compose -f .\deploy\compose.yaml config --quiet`：通过。
- `docker compose -f .\deploy\compose.yaml ps`：`aacv-neo4j` 使用 `neo4j:5.26-community`，状态为healthy。
- `docker compose -f .\deploy\compose.yaml up -d --no-recreate neo4j`：已有容器保持Running，未重建或替换用户凭据。
- `.\mvnw.cmd -f .\backend\pom.xml verify`：73项测试通过，0失败、0错误、0跳过，Spring Boot可执行JAR构建成功。
- `npm --prefix .\frontend run test`：2项测试通过。
- `npm --prefix .\frontend run build`：TypeScript检查和Vite生产构建通过。
- 本地 `liveness`、`readiness`、`graph`：均返回 `UP`；当前代码的Testcontainers应用测试也验证三个健康组均为 `UP`。
- `.\mvnw.cmd -f .\backend\pom.xml "-Dtest=OpenAlexOnlineAcceptanceIT" test`：真实OpenAlex匿名小样本采集、同条件重跑和目录去重通过；该在线测试显式执行，不作为确定性离线构建的默认用例。

首次在受限沙箱执行Docker集成测试时出现 `AccessDeniedException: \\.\pipe\docker_engine`，前端Vite首次执行出现 `spawn EPERM`。两者均以原命令在获准的宿主环境复跑通过，判定为执行环境权限限制，不是代码回归。在线OpenAlex访问未遇到API权限、预算或限流阻塞。

## 4. 退出结论

第8.6节退出条件已经满足。MySQL仍是业务权威数据源，Neo4j仅保留阶段1至2的连通性与健康基线；阶段3没有写入Neo4j业务节点或关系。项目在此停止，不自动进入阶段4。
