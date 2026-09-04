# 阶段 5 Neo4j 图投影验收记录

## 1. 验收范围

本记录对应 `docs/development-plan.md` 第 10 节批次 5.0 至 5.8。阶段 5 只实现 MySQL 权威数据到 Neo4j 可重建投影的后端基础设施、受限查询和运维能力；不包含阶段 6 业务前端、阶段 7 图谱页面/统计/导出、阶段 8 容量与交付验收，也未执行服务器部署或 Git 历史操作。

验收期间所有确定性数据库测试使用 MySQL 8.0.42 与 Neo4j 5.26 Community Testcontainers。故障、回填、对账和重建只操作隔离测试数据；未清理或重建本机 MySQL 开发库及 Neo4j 卷。凭据只允许通过当前进程环境变量注入，本记录不包含任何凭据值。

## 2. 架构边界

- MySQL 当前规范视图是唯一权威源；Neo4j 不反写业务实体，可以由 MySQL 全量重建。
- 采集、治理、`desired_version` 和 `graph_outbox_event` 在同一 `JdbcTransactionManager` 事务内提交或回滚；Neo4j 使用独立事务，不伪装跨库原子提交。
- Outbox 只发布 `ACHIEVEMENT/REFRESH` 和期望版本，消费时重新读取 MySQL，不复制来源 Payload 或业务快照。
- Quartz 执行小批轮询；Spring Batch 执行主键游标回填、对账和重建。未新增消息队列、APOC、GDS、微服务或依赖。
- Neo4j 写入只使用固定参数化 Cypher；全量重建只删除 `aacvManaged=true` 的投影，不删除未知图数据、索引、约束或卷。

## 3. 逐批次结果

| 批次 | 实际结果 | 门禁 |
| --- | --- | --- |
| 5.0 | 读取指令、需求、设计、计划和阶段4验收；保护全部未跟踪内容；阶段4后端108项、前端2项及构建通过；Docker 29.4.2/Neo4j 5.26 可用；首次写入前盘点为空图、无业务约束冲突 | 通过 |
| 5.1 | 冻结三项图权限、角色映射、审计动作、稳定错误码、OpenAPI 和权限矩阵；匿名、越权、CSRF 失败路径纳入测试 | 通过 |
| 5.2 | 新增不可变 V9，包含投影状态、Outbox、死信和维护运行；迁移不隐式生成事件；空库/升级/约束/事务回滚测试通过 | 通过 |
| 5.3 | 采集、引用后到、治理合并/撤销和字段覆盖在既有 MySQL 事务内发布刷新；入口不调用 Neo4j | 通过 |
| 5.4 | 实现 `SKIP LOCKED` 短事务认领、2分钟租约、每批50、10秒 Quartz、最多5次有界退避、死信和重放 | 通过 |
| 5.5 | 初始化五项唯一约束和七项查询索引；投影五类节点/五类关系及有限属性；幂等、旧版本、规范合并、引用和故障补偿测试通过 | 通过 |
| 5.6 | 实现局部子图、最短路径、同步状态和事件接口；深度2、默认100/硬上限300、路径6跳、查询3秒；重建期间返回专用错误 | 通过 |
| 5.7 | 实现每页100条的 Spring Batch 回填、对账和受管全量重建、单实例维护锁、失败游标恢复及消费者暂停；非AACV节点保留 | 通过 |
| 5.8 | 完成需求追溯、设计、OpenAPI、权限矩阵、README和本记录同步，并执行第10.6节验证矩阵 | 最终命令结果见第5节 |

## 4. 故障演练与修正记录

1. 首次用 Testcontainers 暂停 Neo4j 后同步等待失败，发现底层 Bolt socket 在容器暂停时不会仅凭服务端事务超时立即返回；测试进程已终止，未影响本机数据。
2. 改为停止并启动同一 Testcontainers 容器时，容器没有在限定时间内恢复到原 Bolt 端口，导致该轮2项测试环境性失败；未把它误报为业务失败。
3. 最终演练真实断开隔离 Neo4j 容器网络，并同步切换既有图能力降级门禁：MySQL 成果与 Outbox 已提交，消费失败后事件保持 `PENDING`；恢复容器网络和图能力后，同一事件被成功补偿并生成受管成果节点。`GraphProjectionIntegrationTests` 2项通过。
4. 最终复核补齐重建期间 `GRAPH_REBUILD_IN_PROGRESS`、Neo4j事务超时 `GRAPH_QUERY_TIMEOUT` 错误路径，以及 `orcid`、机构标准码/国家码、`issn`、主题 `code` 和对应索引；均增加自动化断言。

## 5. 第 10.6 节验证结果

| 命令 | 实际结果 |
| --- | --- |
| `.\mvnw.cmd -f .\backend\pom.xml '-Dtest=FlywayMigrationTests,GraphOutboxPersistenceTests' test` | 通过，10项测试，0失败、0错误、0跳过 |
| `.\mvnw.cmd -f .\backend\pom.xml '-Dtest=GraphProjectionIntegrationTests' test` | 通过，2项测试，0失败、0错误、0跳过；包含隔离Neo4j网络故障与恢复补偿 |
| `.\mvnw.cmd -f .\backend\pom.xml '-Dtest=GraphQueryIntegrationTests,GraphMaintenanceIntegrationTests' test` | 通过，4项测试，0失败、0错误、0跳过 |
| `.\mvnw.cmd -f .\backend\pom.xml '-Dtest=SecurityIntegrationTests,OpenApiDocumentTests' test` | 通过，13项测试，0失败、0错误、0跳过 |
| `.\mvnw.cmd -f .\backend\pom.xml verify` | 首轮122项中1项失败：`/actuator/health/graph` 因健康组缩进错误返回404；修正后重跑通过，123项测试，0失败、0错误、0跳过，并成功生成JAR |
| `.\mvnw.cmd -f .\backend\pom.xml '-Dtest=AacvSystemApplicationTests,GraphQueryIntegrationTests,GraphQueryServiceTests,OpenApiDocumentTests' test` | 健康组与查询超时修正后的聚焦回归通过，6项测试，0失败、0错误、0跳过 |
| `npm --prefix .\frontend run test` | 沙箱内首次因Windows子进程权限限制报 `Error: spawn EPERM`；以相同命令在获准环境重跑通过，1个测试文件、2项测试通过 |
| `npm --prefix .\frontend run build` | 通过，`vue-tsc -b` 与Vite生产构建成功，转换27个模块 |
| `docker version` | 通过，Client/Engine 29.4.2，Server为Docker Desktop 4.72.0、Linux容器模式 |
| `docker compose -f .\deploy\compose.yaml config --quiet` | 通过；只在当前命令进程注入非生产占位值，未写入仓库或持久环境 |
| `docker compose -f .\deploy\compose.yaml ps` | 通过，既有 `aacv-neo4j` 使用 `neo4j:5.26-community` 且状态为 `healthy`；未重启或重建服务 |
| 三个 `Invoke-RestMethod` 健康检查 | `liveness`、`readiness`、`graph` 均返回 `UP` |
| `git diff --check` | 通过；仓库内容全部未跟踪，该命令无已跟踪差异可检查，因此另以 `rg` 检查工作区源码/文档，行尾空白候选文件数为0 |
| `git status --short --branch` | `dev` 分支；`.gitattributes`、`.gitignore`、`.mvn/`、`README.md`、`backend/`、`deploy/`、`docs/`、`frontend/`、`mvnw`、`mvnw.cmd` 仍全部未跟踪并被保留 |

补充安全检查以常见云密钥、私钥、Bearer凭据格式扫描源码、配置与文档，候选文件数为0。未读取、打印或持久化任何真实凭据。

## 6. 完成定义对照

| 完成定义 | 证据 | 结论 |
| --- | --- | --- |
| MySQL业务与Outbox原子提交/回滚 | V9约束、`GraphProjectionRequestPort`、Outbox持久化与采集集成测试 | 满足 |
| Neo4j停机不回滚MySQL，恢复可补偿 | 隔离容器网络故障演练 | 满足 |
| 重复、乱序、租约恢复不重复或回退 | 版本守卫、唯一约束、认领/租约/幂等测试 | 满足 |
| 五类节点、五类关系来自当前规范视图 | 快照 Mapper、固定Cypher及投影集成测试 | 满足 |
| 查询权限、输入、深度、节点数和超时有界 | Spring Security、白名单查询模板、3秒查询事务超时及查询测试 | 满足 |
| 回填、重放、对账和重建有双容器测试 | Outbox、投影、维护集成测试 | 满足 |
| 既有回归、前端测试和生产构建通过 | 第5节最终验证 | 满足 |

## 7. 已知限制与非目标

- 图查询的 3 秒和投影写事务的 5 秒是服务端事务期限；操作系统级网络黑洞仍可能受底层 TCP 超时影响。正常断连由 Outbox 有界重试和租约恢复补偿。
- `graphSchema` 表示版本化约束初始化状态，`neo4j` 健康指标表示实时连接状态；同步状态提供投影积压和 5 分钟延迟度量。
- 阶段 5 未执行 10 万成果/100 万关系容量测试，也未验证多实例调度、高可用、生产备份或服务器部署。
- 不包含业务前端、图谱交互、合作网络物化、统计分析、导出、APOC/GDS 或任意 Cypher 接口。
- 仓库仅有初始化提交，工程内容仍全部显示为未跟踪；本阶段保留这一既有边界，未提交、未清理、未重置、未创建分支或推送。
