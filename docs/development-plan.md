# AACV System 第一阶段开发计划

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档版本 | 2.8 |
| 文档状态 | 阶段0至7已验收；2026-09-05优化与验证补充 |
| 制定日期 | 2026-09-01 |
| 最近更新 | 2026-09-05 |
| 适用范围 | 第一阶段 Windows 本地开发与验收 |
| 需求基线 | [需求分析](./requirements-analysis.md) |
| 设计基线 | [系统设计](./system-design.md) |

本文档用于将已经确认的需求和系统设计拆分为可执行、可验证的开发阶段。执行会话应按阶段推进；当前一阶段的退出条件未满足时，不得为了展示进度而跳到后续阶段。

2026-09-05用户授权的后续优化范围、当前实现和实际验证结果统一见[优化验收记录](./optimization-acceptance.md)。历史阶段结果保留其日期；当前每日计划模式已校正为固定范围复查，不能沿用历史“滚动窗口”名称推断执行行为。

### 2026-09-05 账号与日志增量

用户确认在现有架构上扩展审计分类、账号统计、统一编辑与六项资料字段，并增加独立安全版本及管理员保护。实现边界、验证结果和剩余限制统一见[账号管理与日志验收记录](./account-management-acceptance.md)。V14为新增版本，不修改历史迁移；完整回归使用隔离数据库，业务迁移、真实备份恢复和部署不属于本次执行范围。

同日后续授权已完成实际业务库V13备份和新版前后端启动，业务库升级到V14，原账号及成果数量保持一致；真实恢复与自动保留尚未执行。此次运行证据见[升级前备份记录](./account-upgrade-backup-evidence.json)及账号验收记录文末。

## 2. 执行原则

1. 指令优先级依次为：适用的 `AGENTS.md`、需求分析、系统设计、本开发计划、当前已验证的工程状态。
2. 每个执行会话开始时先检查 Git 状态，保留已有未提交和未跟踪内容，不重置、不清理、不覆盖用户工作。
3. 每次只实施当前阶段所需的最小完整改动；不得顺便引入微服务、Redis、消息队列、搜索引擎或其他未确认组件。
4. 所有行为变更必须具备相应测试；所有数据库结构变更必须通过版本化迁移实现。
5. 不在代码、文档、日志、测试样例或提交内容中保存真实账号、密码、Token、API Key 或私钥。
6. MySQL 是业务权威数据源；Neo4j 是可重建图投影，禁止假设两个数据库可以在同一事务中共同提交。
7. 第一阶段只允许本机或经用户明确批准的同一内网访问；不实施服务器部署、生产 HTTPS 和公网发布。
8. 未经用户明确要求，不创建 Git 提交、分支、标签，不执行推送、部署或远程资源变更。

## 3. 当前状态与进入条件

### 3.1 已确认技术基线

| 分类 | 第一阶段基线 |
| --- | --- |
| 架构 | 单个 Spring Boot 进程、模块化单体 |
| 后端 | Java 21、Spring Boot 4.1.x、Spring MVC |
| MySQL 访问 | MyBatis，Mapper 接口与 XML SQL |
| 数据库迁移 | Flyway |
| 图数据库访问 | Spring Data Neo4j |
| 认证 | Spring Security、Spring Session JDBC、HttpOnly Cookie、CSRF |
| 调度与批处理 | Quartz JDBC JobStore、Spring Batch |
| 前端 | Vue 3、TypeScript、Vite |
| 图表与图谱 | ECharts、Cytoscape.js |
| 业务数据库 | 兼容基线为 MySQL 8.0.42；当前验收主机实测 MySQL80 8.0.41，数据库名 `aacv_system` |
| 图数据库 | Docker 中的 Neo4j 5.26 Community |
| 首批数据源 | OpenAlex、Crossref |

截至 2026-09-01，Spring Boot 官方文档显示 4.1.1 为稳定版本，MyBatis Spring Boot Starter 4.1.0 对应 Spring Boot 4.1。工程初始化时建议以这两个版本作为候选组合，并以实际构建、Mapper 集成测试和依赖树检查作为最终兼容性证据。Spring生态依赖优先使用 Spring Boot 依赖管理，不单独覆盖 Spring Data、Jackson、Spring Batch 或 Neo4j Driver 版本。

Neo4j 5.26 是长期支持版本，当前基线继续使用 5.26 Community。容器不得使用 `latest`，应使用明确的 `5.26` Community 标签并记录实际镜像摘要。目录中的 `images/neo4j-4.4.tar` 只保留为既有文件，不作为开发运行基线。

### 3.2 本机环境快照

以下结果仅代表制定计划时的只读检查，执行会话必须重新验证：

| 项目 | 当前结果 | 处理要求 |
| --- | --- | --- |
| Git | `dev` 分支；`docs/`、`images/` 未跟踪 | 保留现状，先确认差异再修改 |
| Java | 24.0.1 | 与 Java 21 基线不一致，阶段 0 必须配置 JDK 21 |
| Maven | 系统命令不可用 | 工程必须提交 Maven Wrapper，不依赖全局 Maven |
| Node.js | 24.14.0 | 初始化前验证与选定 Vite 版本兼容 |
| npm | 11.9.0 | 使用锁文件并通过 `npm ci` 验证 |
| Docker Client | 29.4.2 | 已安装 |
| Docker Compose | 5.1.3 | 已安装 |
| Docker Engine | 当前未运行 | 阶段 0 必须启动并验证 Linux 容器引擎 |
| MySQL80 | 当前主机实测 8.0.41，服务运行，配置端口 3306 | 与8.0.42基线存在补丁差异；阶段8隔离环境使用8.0.42复核 |
| 备份目录 | `E:\AACV_System_Backups` 不存在 | 待实施逻辑备份、7日/4周保留、SHA-256校验和隔离恢复 |

### 3.3 阶段 0 之前不得假设成立的事项

- 不得假设现有 MySQL 账号具备创建数据库、建表和执行 Flyway 的权限；
- 不得假设 `aacv_system` 数据库当前不存在或为空；
- 不得假设 Docker 能够拉取或运行 Neo4j 5.26；
- 不得假设本机能够稳定访问 OpenAlex 和 Crossref；
- 不得把仅有端口配置视为数据库连接已经通过；
- 不得在缺少真实测试结果时宣称性能、备份恢复或端到端流程已经验收。

## 4. 里程碑总览

| 阶段 | 目标 | 主要产物 | 依赖 | 状态 |
| --- | --- | --- | --- | --- |
| 0 | 环境和权限门禁 | 可重复的本地开发前置条件 | 无 | 已实现并复验 |
| 1 | 工程骨架与连通性 | 后端、前端、Neo4j Compose、基础测试 | 阶段 0 | 已实现并复验 |
| 2 | 认证、审计与数据基础 | 用户权限、Flyway 基础结构、错误模型 | 阶段 1 | 已实现并复验 |
| 3 | OpenAlex 采集闭环 | 第一条端到端采集链路 | 阶段 2 | 已实现并复验 |
| 4 | Crossref 与数据治理 | 双源融合、去重、消歧、人工审核 | 阶段 3 | 已实现并验收 |
| 5 | Neo4j 图投影 | Outbox、图同步、图查询、对账 | 阶段 4 | 已实现并验收 |
| 6 | 业务前端 | 登录、数据源、任务、成果、治理页面 | 阶段 5 | 已完成（批次6.0至6.8） |
| 7 | 可视化、分析、导出与运维 | 图谱、统计、导出、监控页面 | 阶段 6 | 已完成（批次 7.0 至 7.8） |
| 8 | 非功能验收与本地交付 | 性能、安全、备份恢复、最终文档 | 阶段 7 | 实施中 |

### 4.1 2026-09-01 阶段 0、1 复验结论

当前仓库已经具备 Maven Wrapper、Spring Boot 后端、Vue 前端、Neo4j Compose、Flyway 基线迁移、MyBatis探针和Testcontainers集成测试，阶段2业务尚未提前实现。

本次复验得到以下证据：

- 当前终端使用Temurin JDK 21.0.12.1，符合Java 21基线；
- MySQL80服务运行，`127.0.0.1:3306` TCP连接可达；
- OpenAlex和Crossref轻量HTTPS请求均返回HTTP 200；
- Neo4j Compose在注入临时占位环境变量后配置校验通过；
- 前端Vitest共2个测试通过，Vite生产构建通过；
- 当前Docker Desktop和Docker Engine未运行，后端 `mvnw verify` 因Testcontainers找不到有效Docker环境而失败；
- 当前终端未配置MySQL和Neo4j凭据，这符合凭据不持久化原则，但意味着未复验本机数据库和Neo4j联合启动。

因此，当时阶段0和阶段1可以认定为“实现完成”，但不能把该次复验标记为全部通过。该门禁后续已经按第4.2节完成，不再代表当前阻塞状态。

### 4.2 2026-09-01 阶段 2 实施与复验结论

批次2.0已经先于阶段2代码实施完成并全部通过：

- Docker Client和Server均为29.4.2，Docker Desktop Linux Engine可用；
- 本机MySQL80为8.0.42，安全交互登录和只读检查通过；阶段2迁移前 `aacv_system` 仅存在 `flyway_schema_history`，未发现未知业务表；
- Neo4j 5.26 Community Compose配置校验通过，容器健康；
- 阶段1后端全量验证通过，MySQL 8.0.42与Neo4j 5.26 Testcontainers、Flyway、MyBatis和Actuator均实际运行；
- 本机MySQL80、Neo4j和后端联合启动后，liveness、readiness和graph健康组均为UP；
- 前端Vitest共2个测试通过，Vite生产构建通过，Vite代理访问后端存活端点成功。

批次2.1至2.6已经按顺序实现并复验：

- 引入Spring Security 7.1.1和Spring Session JDBC 4.1.1，建立 `identity`、`operations` 和受控 `shared` 包边界；
- 在MySQL与Neo4j并存时显式使用 `JdbcTransactionManager` 作为阶段2主事务边界，避免Spring Session和MyBatis写入误用Neo4j事务；
- V1保持原样，新增V2身份、会话和审计结构及V3固定角色数据；空MySQL 8.0.42容器可执行全部迁移并通过Flyway `validate`；
- 实现一次性初始管理员、三级角色、用户新增/启停/密码重置/角色变更、乐观锁和旧会话失效；
- 实现JSON登录、退出、当前用户、CSRF、HttpOnly会话Cookie、后端授权和管理员用户接口；
- 实现 `application/problem+json`、受校验traceId、登录与管理操作审计、安全摘要限制和审计分页查询；
- 使用版本控制的静态 `docs/openapi.yaml` 作为阶段2接口契约，未引入未经验证的springdoc运行时依赖；
- 最终后端 `verify` 共25个测试全部通过，0失败、0错误、0跳过；前端测试和构建通过；Compose及本机联合健康检查通过；
- 未实现OpenAlex、Crossref、Quartz、Spring Batch、Outbox或Neo4j业务图同步，未进入阶段3。

### 4.3 2026-09-02 阶段 3 计划制定前复核

本次只读复核确认阶段2实现仍存在于当前工作区：身份、会话、权限、审计、统一错误、Flyway V1至V3、OpenAPI和权限矩阵均已落地，生产代码中仍未出现阶段3业务实现。

当前复核结果如下：

- 后端不依赖Docker的定向测试共14个通过，0失败、0错误、0跳过；
- 前端Vitest共2个测试通过，Vite生产构建通过；
- 当前Docker Client可用，但Docker Desktop Linux Engine未运行；本次后端全量 `verify` 因3个Testcontainers测试无法找到Docker环境而失败，不能将该环境阻塞解释为阶段2代码回归；
- 2026-09-01已有一次Docker可用时后端25个测试、Compose和本机联合健康检查全部通过的验收记录；阶段3执行会话仍必须重新完成批次3.0，不直接复用历史结果；
- 当前处于 `dev` 分支，项目文件整体仍为未跟踪状态。阶段3必须保留这些文件，不执行清理、重置、提交或部署。

### 4.4 2026-09-02 阶段 3 实施与验收结论

批次3.0至3.8已经按顺序完成，详细证据见[阶段3验收记录](./stage3-acceptance.md)。最终状态如下：

- 形成固定OpenAlex官方地址的数据源配置与探测、有界一次性和每日固定范围复查任务、Quartz JDBC计划、Spring Batch Chunk、业务检查点、暂停/恢复/取消、异常重启协调和有限失败重试闭环；历史“滚动发表日期窗口”模式名称已由V12按实际实现校正；
- Flyway V4至V7包含数据源与任务、Spring Batch 6.0.5、Quartz 2.5.2、原始快照和成果目录结构；空MySQL 8.0.42及既有V1至V3升级路径均已执行并通过 `validate`；
- OpenAlex客户端使用不透明游标、受控字段、响应大小和预算边界，只对临时网络故障及429、502、503、504进行有限重试；真实匿名小样本在线闭环通过，重复执行未产生重复目录数据；
- 成果目录支持标题、作者、机构、年份、类型、来源、载体和主题筛选，并提供作者、机构、载体、主题关联入口及来源追溯；普通目录接口不返回原始Payload；
- 后端全量 `verify` 共73个测试通过，前端2个测试和生产构建通过，Compose配置、Neo4j健康状态以及本地liveness、readiness、graph健康组通过；
- Crossref、多源数据治理、Outbox、Neo4j业务图、业务前端和服务器部署均未进入；未执行Git清理、重置、提交、分支、标签或推送。

### 4.5 2026-09-02 阶段 4 计划制定前复核

本次根据当前代码而不是仅依据阶段3验收记录制定阶段4计划。复核结果如下：

- Docker Client和Server均为29.4.2，Docker Desktop Linux Engine实际可用；
- 后端全量 `verify` 再次通过，共73个测试，0失败、0错误、0跳过，可执行JAR构建成功；
- 前端Vitest共2个测试通过，TypeScript检查和Vite生产构建通过；首次在受限环境执行出现 `spawn EPERM`，同一命令在获准宿主环境复跑通过，属于执行环境限制；
- 阶段3的V1至V7、OpenAlex适配器、Quartz、Spring Batch、业务检查点、MyBatis幂等写入和成果目录均存在并通过回归；
- 当前双源扩展点真实存在，但 `SourceType`、数据源数据库检查约束、`DataSourceConfiguration`、Batch Reader、`IngestionPageService`、标准化器和部分外部标识表仍带有OpenAlex单源假设；阶段4需要做最小通用化，不能复制第二套采集框架；
- 当前处于 `dev` 分支，项目文件整体仍为未跟踪状态。阶段4必须保护全部现有文件和凭据，不执行Git清理、重置、提交或部署。

### 4.6 2026-09-02 阶段 5 计划制定前复核

本次根据阶段4实际实现而不是原有阶段5概要制定计划。复核结果如下：

- 阶段4的Crossref适配器、确定性双源融合、`canonical_entity_link`逻辑规范关联、人工决定与字段修正、质量指标和规范目录均已存在；阶段4验收记录明确Outbox和Neo4j业务图尚未实现；
- MySQL当前只有V1至V8迁移，生产源码中未发现 `graph_outbox_event`、`graph_sync_dead_letter`、`Neo4jClient`、Neo4j实体映射或 `/api/v1/graph` 接口，阶段5没有需要兼容的半成品图实现；
- 当前 `achievement`、`author`、`organization`、`venue` 具有业务版本字段，治理合并通过 `canonical_entity_link` 表达；实际结构尚无通用 `status/deleted_at` 业务删除字段。阶段5必须处理已有规范关联，不得为了图同步顺带发明未确认的删除业务；
- `spring-boot-starter-data-neo4j` 已存在，实际解析为Spring Data Neo4j 8.1.1和Neo4j Java Driver 6.1.0；MyBatis、Quartz JDBC JobStore、Spring Batch和Neo4j Testcontainers也已存在，无需增加消息队列、APOC、GDS或新的图对象映射框架；
- Docker Desktop 4.72.0及Docker Engine 29.4.2可用。当前任务进程没有继承本地MySQL和Neo4j环境变量，因此持久化Compose容器状态未复验；隔离Testcontainers不依赖这些凭据；
- 后端全量 `verify` 复跑108项全部通过，0失败、0错误、0跳过，可执行JAR构建成功；前端Vitest 2项通过，TypeScript检查和Vite生产构建通过；
- 当前处于 `dev` 分支，`git ls-files` 仍为空且项目树整体未跟踪。阶段5必须先记录基线并保护全部现有文件，不得将空 `git diff` 误判为没有改动。

## 5. 阶段 0：环境和权限门禁

### 5.1 目标

在创建工程代码前消除环境不确定性，形成能够支持 Java 21、MySQL、Neo4j、Testcontainers 和前端构建的本地开发环境。

### 5.2 工作项

1. 重新读取适用指令和三份项目文档，执行 `git status --short --branch`，确认用户已有文件。
2. 安装或配置 JDK 21，并明确 `JAVA_HOME` 和终端实际使用的 `java`；不得仅依靠 JDK 24 编译后声称符合 Java 21 基线。
3. 启动 Docker Desktop Linux Engine，验证客户端和服务端版本、Compose 及容器网络。
4. 使用现有 MySQL 账号通过安全交互方式验证连接、字符集和建库权限；密码不得出现在命令历史、文档或日志中。
5. 查询 `aacv_system` 是否已存在。若已存在且包含对象，停止并向用户报告，不得覆盖；若不存在，在用户授权的执行会话中创建 `utf8mb4` 数据库。
6. 验证本机能够通过 HTTPS 访问 `api.openalex.org` 和 `api.crossref.org`，记录响应状态和限流相关响应头，不采集大批数据。
7. 确认开发端口未冲突，建议后端 8080、前端 5173、Neo4j HTTP 7474、Bolt 7687；如需修改，统一写入本地运行文档。

### 5.3 建议验证命令

~~~powershell
git status --short --branch
java -version
docker version
docker compose version
docker info
Get-Service -Name MySQL80
Test-NetConnection -ComputerName localhost -Port 3306
Invoke-WebRequest -Uri 'https://api.openalex.org/works?per-page=1' -TimeoutSec 15
Invoke-WebRequest -Uri 'https://api.crossref.org/works?rows=1' -TimeoutSec 15
~~~

MySQL连接和建库命令必须使用不会暴露密码的交互方式。若数据库已存在，只允许先做只读检查。

### 5.4 退出条件

- 终端实际使用 JDK 21；
- Docker Engine 可用；
- MySQL连接和权限已经验证，`aacv_system` 状态明确；
- 两个外部数据源基础连通；
- 没有覆盖或删除用户已有文件。

任一条件不满足时，阶段 0 保持未完成，不进入工程初始化。

## 6. 阶段 1：工程骨架与连通性

### 6.1 目标

创建最小可运行工程，验证后端、前端、本机 MySQL 和 Neo4j 容器能够联合启动，但不提前实现业务功能。

### 6.2 工作项

1. 按确认结构创建 `backend/`、`frontend/`、`deploy/`、根 `README.md` 和必要的 `.gitignore`。
2. 后端使用单一 Maven 模块，根包 `com.aacv.system`，提交 `mvnw`、`mvnw.cmd` 和 Wrapper 配置。
3. 锁定经过验证的 Spring Boot 和 MyBatis Starter 版本；其余 Spring 依赖由 Spring Boot 管理。
4. 初始后端只引入当前阶段需要的依赖，避免提前引入 Jsoup、搜索引擎、消息队列等未使用能力。
5. 建立 `application.yml` 和本地配置约定；真实凭据只通过本地环境变量或未提交安全配置注入。
6. 创建只包含 Neo4j 5.26 Community 的 `deploy/compose.yaml`。不得创建新的 MySQL 容器，不得使用 `images/neo4j-4.4.tar`。
7. 初始化 Vue 3、TypeScript、Vite，配置本地 API 代理和基础路由；锁定 `package-lock.json`。
8. 建立统一日志基础、Actuator存活检查和最小应用启动测试。
9. 增加 MySQL、Flyway和Neo4j的最小连通性集成测试；Testcontainers测试必须使用独立测试容器和隔离数据。

### 6.3 主要交付物

- 可运行的后端和前端工程；
- Maven Wrapper与npm锁文件；
- Neo4j 5.26 Compose配置和持久卷；
- 无敏感值的配置模板；
- 本地启动、停止和验证说明；
- 最小单元测试和数据库连通性集成测试。

### 6.4 退出条件

- `mvnw verify` 通过；
- 前端测试和构建通过；
- Compose配置校验通过，Neo4j健康且Bolt连接成功；
- 后端能连接本机 MySQL80 并完成空库Flyway基线迁移；
- 前端可以通过本地代理访问后端健康接口；
- 仓库中不存在真实凭据。

## 7. 阶段 2：认证、审计与数据基础（已实现并复验）

### 7.1 目标

建立所有后续业务功能共用的安全边界、错误契约、审计能力和MySQL基础结构。

### 7.2 批次 2.0：阶段 1 复验门禁

开始编写阶段2代码前，必须完成：

1. 启动Docker Desktop Linux Engine，确认 `docker version` 同时返回Client和Server版本。
2. 在当前终端通过安全交互方式重新注入MySQL和Neo4j环境变量，不在对话、命令文本或文件中写入真实密码。
3. 校验并启动Neo4j 5.26 Compose，等待健康检查通过。
4. 运行后端全量 `verify`，确认MySQL 8.0.42和Neo4j 5.26 Testcontainers、Flyway、MyBatis及Actuator测试通过。
5. 启动后端连接本机MySQL80和Neo4j，验证liveness、readiness和graph健康组。
6. 重新运行前端测试和构建，必要时通过Vite代理执行一次浏览器存活检查。

若后端测试或本地联合健康检查失败，应先定位并修复阶段1问题，不得通过跳过Testcontainers、关闭Flyway或删除断言进入阶段2。

### 7.3 阶段 2 执行批次

#### 批次 2.1：依赖、模块边界与权限矩阵

1. 仅增加阶段2所需的Spring Security、Spring Session JDBC及测试依赖；引入springdoc-openapi前必须验证其与Spring Boot 4.1.1的兼容性。
2. 建立 `identity`、`operations` 和受控 `shared` 业务包，内部按 `api`、`application`、`domain`、`infrastructure` 分层。
3. 明确三种角色：管理员、数据运营人员、科研用户；将接口和操作权限整理为可测试的权限矩阵。
4. API层不得直接调用MyBatis Mapper，领域层不得依赖Spring MVC、MyBatis或数据库实现。
5. 不创建没有行为的批量占位类，不提前创建采集、图谱、分析或导出模块。

#### 批次 2.2：Flyway 身份、会话与审计结构

1. 保留现有 `V1__initialize_baseline.sql`，不得修改已经应用的V1迁移。
2. 使用后续独立迁移创建用户、角色、用户角色、Spring Session和审计日志结构，并以单独迁移写入固定角色基础数据。
3. 用户名使用数据库唯一约束；用户记录包含状态、密码哈希、乐观锁版本、创建/更新时间和必要的最近安全变更时间。
4. 角色关系、会话查询、审计查询和用户状态查询建立明确索引，避免仅依赖应用层去重。
5. 所有业务时间按UTC保存，迁移必须兼容MySQL 8.0.42。
6. 不通过迁移写入默认管理员密码、真实邮箱、测试账号或任何凭据。
7. Spring Session建表必须由Flyway管理，关闭框架对正式数据库的隐式建表。

#### 批次 2.3：初始管理员与账号领域能力

1. 实现用户状态和角色领域模型，至少覆盖启用、停用和密码重置后的安全状态变化。
2. 实现一次性初始管理员引导：只允许在用户表为空且显式启用时运行，凭据通过环境变量安全注入，成功后不重复创建或覆盖用户。
3. 使用Spring Security推荐的强密码哈希；不得保存、返回或记录明文密码。
4. 实现管理员新增用户、启用、停用和重置密码的应用服务，所有写入使用事务并处理重复用户名与并发更新。
5. 停用用户时使其已有会话失效；密码重置后旧会话不得继续访问受保护接口。

#### 批次 2.4：会话认证、CSRF与授权接口

1. 实现 `POST /api/v1/auth/login`、`POST /api/v1/auth/logout` 和 `GET /api/v1/auth/me`。
2. 实现管理员用户管理接口，保持 `/api/v1`、JSON和服务端分页约定。
3. 使用服务端会话和HttpOnly Session Cookie；本地阶段配置合适的SameSite属性，未来HTTPS部署时再强制Secure。
4. 保持CSRF防护开启，为Vue客户端提供明确的令牌获取和提交方式；不得为了让测试通过而关闭CSRF。
5. 后端对每个受保护接口执行授权，前端路由控制不能替代后端检查。
6. 统一处理匿名、未授权、禁止访问、账号停用、凭据错误和会话过期，响应不得泄露账号是否存在、密码细节或内部堆栈。
7. 用户名、密码和分页等输入在API边界校验，限制长度并拒绝空白或畸形输入。

#### 批次 2.5：错误契约、追踪与审计

1. 统一使用 `application/problem+json`，响应包含稳定 `errorCode`、`traceId` 和安全描述。
2. 通过请求过滤器建立traceId；外部传入值必须校验长度和字符集，不可信值不得直接进入日志上下文。
3. 记录登录成功、登录失败、退出、用户创建、启停、角色变更和密码重置审计事件。
4. 审计记录包含操作人、操作类型、目标类型、目标ID、结果、时间和必要的安全摘要，不保存密码、Cookie、会话ID或完整请求体。
5. 明确业务写入与审计写入的事务关系；关键管理操作不得在业务失败时留下“成功”审计。
6. 日志只记录定位所需信息，不返回SQL、数据库异常、类路径或堆栈给客户端。

#### 批次 2.6：自动化测试与文档同步

1. 使用MySQL Testcontainers验证所有Flyway迁移能从空库执行并通过 `validate`。
2. 增加Mapper、应用服务、控制器和完整安全链路测试。
3. 覆盖空输入、超长输入、重复用户名、错误密码、账号停用、越权、缺失或错误CSRF、会话过期、密码重置和并发版本冲突。
4. 验证停用用户和密码重置用户的旧会话失效，匿名用户不能访问管理接口。
5. 验证登录失败和关键管理操作产生正确审计，且日志、响应和审计中无敏感数据。
6. 更新OpenAPI、README、本地管理员初始化说明和阶段状态；不得在文档中加入真实示例凭据。
7. 重新执行后端全量验证、前端回归测试、前端构建、Compose配置校验和本地联合健康检查。

### 7.4 阶段 2 主要交付物

- 身份、会话和审计的Flyway迁移；
- 用户、角色、权限和账号状态领域实现；
- 登录、退出、当前用户和用户管理API；
- Spring Security、Spring Session JDBC、CSRF和会话失效机制；
- 统一错误响应、traceId、结构化日志和审计记录；
- 一次性初始管理员安全引导；
- 后端自动化测试、OpenAPI和本地使用文档。

### 7.5 阶段 2 建议验证命令

~~~powershell
docker version
docker compose -f .\deploy\compose.yaml config --quiet
docker compose -f .\deploy\compose.yaml up -d neo4j
docker compose -f .\deploy\compose.yaml ps

.\mvnw.cmd -f .\backend\pom.xml verify
npm --prefix .\frontend run test
npm --prefix .\frontend run build

Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/liveness
Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/readiness
Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/graph

git diff --check
git status --short --branch
~~~

运行Compose和本地应用前必须先按README在当前终端安全注入凭据。验证命令不得把环境变量值、Cookie或CSRF令牌打印到日志或交付报告。

### 7.6 阶段 2 退出条件

- 批次2.0全部通过，阶段1不再有未解决的环境或集成测试阻塞；
- Flyway能够从空MySQL测试库完整创建V1及后续身份、会话和审计结构，并通过 `validate`；
- 登录、退出、当前用户、用户新增、启停和密码重置接口符合权限矩阵；
- 登录成功、登录失败、用户停用、权限拒绝、CSRF失败、会话过期和并发冲突均有自动化测试；
- 停用用户和密码重置用户的已有会话失效；
- 关键管理操作产生与实际结果一致的审计记录；
- 错误响应符合 `application/problem+json`，不返回堆栈、SQL或敏感配置；
- 后端全量验证、前端回归测试和构建、Compose校验及本地联合健康检查全部通过；
- README、OpenAPI和开发计划与最终实现一致，敏感信息扫描无命中。

### 7.7 阶段 2 非目标

- 不实现OpenAlex或Crossref适配器；
- 不实现数据源管理、采集任务、Quartz或Spring Batch；
- 不创建成果、作者、机构、主题和引用等业务表；
- 不实现Outbox、Neo4j业务图投影、统计、导出或业务前端页面；
- 不实施服务器部署、HTTPS、公网访问或统一身份认证；
- 不创建Git提交、分支、标签或远程变更，除非用户另行明确授权。

## 8. 阶段 3：OpenAlex 采集闭环（已完成）

### 8.1 目标与完成定义

以OpenAlex完成第一条从受控数据源、任务定义、计划触发、游标分页、原始快照、标准化、幂等保存到成果查询的完整纵向链路。阶段3完成时，系统应能在单机环境中可靠执行小批量一次性任务和每日任务，并能从已提交检查点恢复。

阶段3只解决OpenAlex单源闭环。Crossref、多源模糊去重和人工合并属于阶段4；Outbox、Neo4j业务图投影和图查询属于阶段5；业务前端属于阶段6。

### 8.2 批次 3.0：阶段 2 回归与外部接入门禁

开始编写阶段3代码前必须完成以下门禁：

1. 检查适用指令、Git状态和当前差异，确认阶段2文件完整且未被覆盖；不得清理当前未跟踪文件。
2. 启动Docker Desktop Linux Engine，重新运行后端全量 `verify`、前端测试和构建、Compose配置校验与Neo4j健康检查。
3. 使用当前终端安全注入本机MySQL和Neo4j凭据，验证后端连接本机MySQL80与Neo4j后的liveness、readiness和graph健康组。
4. 对现有V1至V3执行Flyway `validate`，检查数据库实际表和迁移历史；不得修改已经应用的迁移。
5. 使用受控的小请求探测OpenAlex官方API，记录HTTP状态、响应结构、分页元数据和限流头；探测失败时先区分网络、认证、预算、限流和来源故障。
6. OpenAlex API Key只能通过本机环境变量 `OPENALEX_API_KEY` 可选注入，并通过 `Authorization: Bearer` 发送；不得写入URL、日志、文档、数据库明文字段或测试样例。没有Key时只允许受预算约束的极小探测。
7. 默认仅使用OpenAlex `core` 语料。若以后需要 `corpus=all`，必须另行评估扩展语料的质量和容量影响。

批次3.0任一回归失败时，先定位阶段2或环境问题，不得通过跳过Testcontainers、关闭Flyway、放宽安全断言或伪造外部响应继续实施。

### 8.3 阶段 3 执行批次

#### 批次 3.1：依赖、模块边界、权限与契约

1. 仅增加阶段3必需的Quartz、Spring Batch及测试依赖，优先使用Spring Boot依赖管理版本；添加依赖前检查现有能力、许可证、依赖树和Spring Boot 4.1.1兼容性。
2. 建立 `source`、`crawl`、`ingestion` 和 `catalog` 模块，每个模块按现有 `api`、`application`、`domain`、`infrastructure` 分层；API层不得直接调用Mapper。
3. 定义数据源适配器契约，包括 `validate`、`probe`、`fetchPage`、`parse` 和 `capabilities`；适配器只生成来源DTO和不透明下一游标，不直接写成果表或Neo4j。
4. 扩展权限模型：管理员管理和探测数据源；管理员、数据运营人员创建及控制采集任务；所有已认证角色可查询成果目录。所有修改和任务控制接口继续启用CSRF、服务端授权和审计。
5. 先更新OpenAPI与请求/响应模型，再实现控制器；保持 `/api/v1`、服务端分页、最大每页100条和 `application/problem+json` 约定。
6. 不提前创建Crossref、数据治理、Outbox、图谱、统计、导出或业务前端占位实现。

#### 批次 3.2：Flyway 数据源、任务与成果结构

1. 从V4开始使用不可变的增量迁移，分批创建数据源与任务、Spring Batch与Quartz元数据、原始记录与标准成果表；不得修改V1至V3。
2. 数据源与任务至少覆盖 `data_source`、`crawl_task`、`crawl_schedule`、`crawl_run`、`crawl_checkpoint` 和 `crawl_failure`。业务运行记录必须关联Spring Batch JobExecution，但不得复制框架内部状态。
3. 原始和目录数据至少覆盖 `raw_record`、`achievement`、`paper_detail`、`achievement_source`、`author`、`author_external_id`、`organization`、`venue`、`topic` 及必要关联表。
4. 引用关系必须允许先保存尚未导入的OpenAlex被引Work ID，后续目标成果出现时再解析关联；不得因目标尚不存在而丢弃引用证据。
5. 使用数据库约束保证 `source_code`、来源记录ID、外部实体ID、任务运行号和关联关系的唯一性。非空标准化DOI使用唯一约束；无DOI记录只保存匹配指纹，阶段3不做模糊自动合并。
6. 原始快照包含来源、外部记录ID、来源定位、抓取时间、Payload哈希、解析器版本、解析状态和JSON Payload。90天到期后只清除Payload正文，来源映射、哈希和追溯元数据继续保留。
7. 所有时间使用UTC，表使用InnoDB和utf8mb4；为任务状态、检查点、失败重试、成果组合筛选及各关联方向建立可解释索引。
8. Quartz和Spring Batch元数据结构必须来自当前实际解析版本的官方脚本并由Flyway管理，关闭正式库的自动初始化；迁移需在空MySQL 8.0.42容器和既有V1至V3数据库上分别验证。

#### 批次 3.3：数据源与任务管理API

1. 初始化唯一的OpenAlex数据源类型和固定官方基础地址 `https://api.openalex.org`；普通接口不得提交或覆盖任意协议、主机、端口或基础路径。
2. 实现数据源分页、详情、创建/更新受控配置、启停和探测接口。只允许修改请求频率、并发、超时、重试上限、合规说明等允许字段，基础主机由适配器固定。
3. 实现采集任务创建、分页、详情、更新未运行任务、手动触发和每日计划配置。任务参数使用结构化字段或经过版本校验的JSON，拒绝未知字段和来源不支持的过滤条件。
4. 第一批任务范围支持发表日期区间、关键词、OpenAlex作者ID和机构ID；每次运行必须设置最大页数或最大记录数，禁止无边界抓取。
5. 创建运行时在数据库事务中锁定相关任务或数据源并检查活动运行，防止相同来源、相同参数和重叠范围的冲突任务并发。
6. 数据源配置变更、启停、探测、任务创建、计划变更、触发和控制操作写入安全审计；审计不保存API Key、完整查询响应或原始Payload。

#### 批次 3.4：OpenAlex 客户端与适配器

1. 在当前Spring MVC阻塞批处理模型中优先使用Spring `RestClient`，避免仅为外部HTTP引入完整响应式Web栈；客户端必须由受控工厂创建，不能接受用户提供的完整URL。
2. 使用 `/works`、`per_page` 和游标分页。首次游标为 `*`，后续原样保存并回传 `meta.next_cursor`；游标为空时正常结束，不解析或拼接不透明游标。
3. 显式选择阶段3需要的Work字段，至少处理OpenAlex ID、DOI、标题、类型、语言、发表日期、主要载体、authorships、topics、referenced_works和abstract_inverted_index；不使用已弃用的 `host_venue` 或以concepts替代topics。
4. 将倒排索引重建为摘要时限制词数、位置值、最大位置和最终长度；字段缺失、位置冲突或畸形数据进入可观察的字段级告警或单条解析失败，禁止猜测补全。
5. OpenAlex authorships最多只返回文档规定的前100项。列表达到上限或来源提供截断信号时保存“作者列表可能不完整”元数据，不宣称作者集合完整。
6. 设置有界连接和响应超时、最大解压后响应大小、JSON内容类型检查、单来源并发、请求间隔、最大页数/记录数和预算保护。建议从并发1、每页100条的小批量配置开始，再根据实测调整。
7. 仅对连接超时、临时DNS、429、502、503和504有限重试，优先遵守 `Retry-After`，否则使用带抖动的指数退避；400、401、403、404、解析失败和预算耗尽不得盲目重试。
8. 记录请求次数、状态分类、限流头和来源返回的费用/预算字段，但不得记录Authorization头、完整查询URL中的敏感参数或外部响应正文。
9. 使用固定官方样例的本地副本作为解析契约测试。在线OpenAlex测试只用于受控冒烟验收，不得成为确定性构建的必要条件。

#### 批次 3.5：标准化与幂等事务写入

1. 流水线固定为“获取页面 → 保存原始快照 → 解析来源DTO → 标准化 → 幂等写MySQL → 提交批次和检查点”。来源适配器、标准化服务和Mapper职责分离。
2. 标准化标题空白和Unicode、DOI、ORCID、日期精度、成果类型及OpenAlex外部ID；保留原始值，不能把未知日期或字段缺失转换为虚假默认值。
3. 成果身份优先匹配既有OpenAlex Work ID来源映射，再使用非空标准化DOI进行确定性关联。无DOI时只生成标题、年份和作者摘要指纹，不在阶段3执行模糊合并。
4. 作者、机构、载体和主题优先按OpenAlex稳定ID幂等写入；同名但没有相同稳定标识的作者不得强制合并。作者顺序和每个authorship中的机构关系必须保留。
5. 相同来源记录重复运行时更新 `last_seen_at`、来源快照和允许由来源维护的字段，不重复创建成果及关联；`first_seen_at`保持不变。
6. 每页使用边界清晰的MySQL事务。只有原始记录、标准化数据、统计和业务检查点全部提交后才推进游标；事务回滚时游标保持在上一成功页。
7. 单条解析或校验失败写入 `crawl_failure` 并继续处理同页其他合法记录；数据库连接、迁移、事务或系统级异常使当前Chunk失败并按分类决定是否重试。

#### 批次 3.6：Quartz、Spring Batch与任务状态机

1. Quartz只创建业务运行并启动Spring Batch Job，不在调度线程内执行抓取；使用JDBC JobStore，当前保持单后端实例，不启用未经验证的多实例集群。
2. Spring Batch负责Chunk事务、ExecutionContext和作业重启；业务 `crawl_run` 保存用户可理解状态并与JobExecution关联。
3. 实现 `PENDING`、`RUNNING`、`PAUSING`、`PAUSED`、`SUCCEEDED`、`PARTIAL_SUCCESS`、`FAILED`、`CANCELLING` 和 `CANCELLED` 状态及合法转换测试。
4. 暂停和取消请求只设置意图，批处理在当前Chunk提交后停止。暂停后的恢复复用业务运行与已提交游标；取消后的运行不得被自动恢复。
5. 应用异常退出后，只有状态和检查点一致的可重启运行才能恢复；若Batch元数据与业务状态不一致，标记为运维异常并停止自动推进。
6. 支持一次性触发和每日计划。任务时区显式保存，下一执行时间可查询；计划更新采用乐观锁并同步更新Quartz Trigger。
7. OpenAlex免费或基础访问下的每日模式为`FIXED_SCOPE_REFRESH`，幂等复查任务既定范围。2026-09-05核对调用链发现原“滚动发表日期窗口”名称与执行不符，V12已更正名称；日期窗口不会自动推进，不能宣称完整捕获历史记录更新。只有确认账号具备 `from_updated_date` 同步权限后，才可另行启用完整更新时间增量模式。
8. 提供运行详情、统计、失败分页、暂停、恢复、取消和有限重试接口；重试失败记录必须保留原运行证据并生成可追踪的新尝试。

#### 批次 3.7：成果目录、来源追溯与原始Payload保留

1. 实现成果分页和详情，支持标题、作者、机构、年份、成果类型、来源、载体和主题组合筛选；所有列表使用稳定排序和服务端分页，单页最大100条。
2. 实现作者、机构、载体和主题分页查询及其关联成果入口；避免逐条查询导致N+1，复杂详情使用明确的批量查询或结果聚合。
3. 成果详情返回标准字段、作者顺序、机构、主题、引用摘要、来源记录ID、来源URL、首次/最近采集时间和解析器版本，不向普通目录接口返回完整原始Payload。
4. 原始Payload仅允许管理员或数据运营人员通过专用受审计接口查看，并限制响应大小；若当前MVP没有明确业务需要，则阶段3只保留后台存储，不开放Payload读取API。
5. 实现按 `payload_expires_at` 小批量清除超过90天的Payload正文，保留哈希和追溯元数据；清理任务必须幂等、可统计且不级联删除成果来源关系。

#### 批次 3.8：自动化测试、文档与小批量验收

1. 增加领域和应用单元测试：任务状态机、权限、参数校验、标准化、摘要重建、重试分类、冲突互斥和指纹稳定性。
2. 增加适配器契约测试：正常页、空页、空游标、缺失字段、无DOI、无摘要、100项authorships、畸形倒排索引、无效JSON、超大响应、429、401/403和临时5xx。
3. 使用MySQL Testcontainers验证V1至阶段3全部Flyway迁移、Mapper、唯一约束、幂等重跑、事务回滚、检查点不前移、单条失败隔离和Payload清理。
4. 增加Spring Batch恢复测试：Chunk提交后中断、提交前失败、同游标重跑、暂停/恢复、取消和应用重启；断言统计与业务状态一致。
5. 增加安全测试：匿名访问、角色越权、CSRF、任意URL/重定向注入、API Key脱敏、响应体大小限制和审计敏感字段扫描。
6. 执行一次受控OpenAlex在线小批量验收，建议不超过5页或500条；随后对相同范围重跑，验证无重复成果、检查点和统计正确。若外部API不可用，必须报告在线验收阻塞，不能用Mock结果冒充。
7. 更新README、OpenAPI、权限矩阵、需求追溯和本开发计划，记录实际依赖版本、配置项、任务语义、限制和验证结果，不保存真实凭据。

### 8.4 阶段 3 主要交付物

- 阶段3 Flyway增量迁移及空库/升级迁移测试；
- 数据源、任务、运行、检查点、失败记录和原始记录模块；
- OpenAlex受控HTTP客户端、适配器、固定样例及契约测试；
- 标准化、幂等持久化、来源追溯和目录查询模块；
- Quartz每日计划与Spring Batch可恢复作业；
- 数据源、任务控制、运行监控和成果目录API；
- 更新后的OpenAPI、权限矩阵、README和阶段验收记录。

### 8.5 阶段 3 建议验证命令

~~~powershell
git status --short --branch
docker version
docker compose -f .\deploy\compose.yaml config --quiet
docker compose -f .\deploy\compose.yaml up -d neo4j
docker compose -f .\deploy\compose.yaml ps

.\mvnw.cmd -f .\backend\pom.xml verify
npm --prefix .\frontend run test
npm --prefix .\frontend run build

Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/liveness
Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/readiness
Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/graph

git diff --check
git diff -- .\docs .\backend .\frontend .\deploy .\README.md
git status --short --branch
~~~

运行Compose、本地应用和在线OpenAlex冒烟前，必须按README在当前终端安全注入凭据。执行会话应另外记录实际运行的定向测试、API调用和数据库断言；不得把环境变量值、Cookie、CSRF令牌、API Key或完整外部响应写入报告。

### 8.6 阶段 3 退出条件

- 批次3.0全部通过，阶段2无未解决的回归或环境阻塞；
- V1至阶段3迁移可从空MySQL 8.0.42执行，并可在既有V1至V3数据库上安全升级和通过Flyway `validate`；
- 管理员可管理及探测OpenAlex数据源，数据运营人员可创建、触发和控制任务，科研用户不能访问管理接口；
- 可以创建有界一次性任务和每日固定范围复查计划；API和文档明确不自动推进窗口、不承诺完整变更同步，达到数量上限必须报告覆盖不完整；
- 受控OpenAlex小批量任务完成，运行详情显示读取、解析、新增、更新、重复、失败、请求次数和检查点；
- 相同来源、相同范围重复执行不会产生重复成果、作者、机构、主题或关联；
- Chunk异常中断后可从最近成功提交的游标恢复，提交失败不会提前推进检查点；
- 单条解析失败不会回滚同批其他合法记录，失败证据可查询并有限重试；
- 成果可按标题、作者、机构、年份、类型、来源、载体和主题组合检索，并可追溯到来源记录；
- 后端全量验证、前端回归与构建、Compose校验、本地联合健康检查和敏感信息检查全部通过；
- README、OpenAPI、权限矩阵、需求追溯和开发计划与最终实现一致。

### 8.7 阶段 3 非目标

- 不实现Crossref或其他来源适配器；
- 不实现跨来源模糊去重、重复候选、人工合并、修正或撤销；
- 不实现Outbox、Neo4j业务节点/关系、图查询或图对账；
- 不实现数据源、任务或成果业务前端页面；
- 不下载全量OpenAlex快照，不进行无边界全库镜像，不绕过预算、限流或访问控制；
- 不采集HTML、PDF全文、登录后页面或机构内网站；
- 不实施服务器部署、生产HTTPS、公网访问、多实例调度或外部告警；
- 不创建Git提交、分支、标签或远程变更，除非用户另行明确授权。

## 9. 阶段 4：Crossref 与数据治理（已完成）

### 9.1 目标与完成定义

在不复制阶段3采集框架的前提下接入Crossref，形成OpenAlex与Crossref两来源独立采集、确定性融合、来源追溯、重复候选、人工决策、字段修正和质量定位闭环。

阶段4采用保守治理策略：相同DOI、ORCID、ROR、ISSN等稳定标识可以确定性关联；无稳定标识的记录只在证据充分时生成候选，不使用未经标注样本验证的数值阈值自动合并。所有人工决定可追溯、可撤销，并且优先于后续自动匹配。

### 9.2 批次 4.0：阶段 3 回归与Crossref接入门禁

开始阶段4代码前必须完成：

1. 重新读取项目指令、需求分析、系统设计、开发计划和阶段3验收记录，检查Git状态并保留全部未跟踪文件。
2. 确认Docker Client和Server可用，重新执行后端全量 `verify`、前端测试和构建、Compose配置校验、Neo4j健康检查及本地三个健康组。
3. 使用MySQL Testcontainers确认V1至V7空库迁移和V1至V3升级路径仍通过Flyway `validate`；不得修改已经应用的V1至V7。
4. 对 `https://api.crossref.org/works?rows=0` 执行一次匿名受控探测，记录HTTP状态、 `x-api-pool`、 `x-rate-limit-limit`、 `x-rate-limit-interval` 和 `x-concurrency-limit`，不写入业务数据。
5. Crossref官方基础地址固定为 `https://api.crossref.org`，请求方不得提交完整URL或任意协议、主机、端口和重定向目标。
6. 可选的联系邮箱只通过当前进程环境变量 `CROSSREF_CONTACT_EMAIL` 注入，用于可识别的User-Agent或 `mailto`；不得写入数据库、文档、日志、审计或错误响应。没有联系邮箱时使用更保守的匿名公共池边界。
7. 记录Crossref当前游标契约：首次使用 `cursor=*`，后续游标保持不透明；同一游标链的所有筛选、字段和行数参数必须完全一致。

批次4.0失败时应先修复阶段3回归或外部接入假设，不得通过跳过测试、关闭Flyway、提高匿名请求并发或伪造在线结果进入后续批次。

### 9.3 阶段 4 执行批次

#### 批次 4.1：最小双源通用化

1. 在现有 `SourceType` 中增加 `CROSSREF`，保留 `DataSourceAdapter` 和 `DataSourceAdapterRegistry`，不再创建第二套数据源服务、任务服务或调度框架。
2. 将数据源合法性改为两个固定组合：`OPENALEX + https://api.openalex.org` 和 `CROSSREF + https://api.crossref.org`；使用简单枚举或静态映射校验，不引入插件系统或动态脚本。
3. 将 `OpenAlexPageItemReader`、OpenAlex专用Job/Step和 `processOpenAlexPage` 最小改造为按运行关联的数据源类型选择适配器的通用实现；Quartz、Spring Batch状态机、检查点和控制接口保持一套。
4. 将 `OpenAlexWorkNormalizer` 中真正共用的DOI、ORCID、ISSN、文本、日期和指纹逻辑改为来源无关的标准化器；来源特有字段解析继续留在各自适配器中。
5. 保持阶段3既有接口兼容。现有OpenAlex任务参数继续按parameter version 1解析；新增parameter version 2承载Crossref所需的DOI、ORCID、ROR和更新时间窗口字段，禁止改变既有已保存任务的含义。
6. 清理生产代码中会阻止Crossref运行的“阶段3仅允许OpenAlex”硬编码，但不做无关重命名、全仓格式化或模块重写。
7. 为所有通用化改动先增加OpenAlex回归测试，确保重构前后的OpenAlex请求、检查点、统计和目录结果一致。

#### 批次 4.2：Flyway 双源标识、治理与质量结构

1. 从V8开始增加不可变迁移，扩展 `data_source` 的类型和固定地址检查约束，以及 `crawl_schedule` 的Crossref增量模式；不得修改V4或V7中的原始定义。
2. 为机构和载体增加来源无关的外部标识映射，至少支持 `OPENALEX`、`ROR`、`ISSN` 和 `ISSN_L`；现有OpenAlex列先兼容保留，通过迁移回填后再由新代码使用通用映射，不在本阶段删除旧列。
3. 扩展引用标识，区分OpenAlex Work ID与标准化DOI，使Crossref带DOI的参考文献可以解析到标准成果；无DOI的非结构化参考文献只保留在原始快照，不猜测目标。
4. Crossref `subject` 使用来源范围内的主题词结构保存，不自动映射为OpenAlex Topic；只有后续存在明确词表映射时才建立跨词表关系。
5. 增加成果字段来源记录，保存当前标准字段由哪个source/raw record选出及规则版本；字段冲突的完整原值仍通过原始快照追溯，不复制整份Payload。
6. 增加 `duplicate_candidate`、`merge_decision`、人工字段覆盖/修订记录和按运行汇总的数据质量指标表。候选实体对使用固定顺序和算法版本唯一，防止同一候选重复生成。
7. 成果、作者和机构采用逻辑规范实体关联：接受合并时保留原实体和来源数据，仅建立到规范实体的受控关联；拒绝不删除数据；撤销恢复合并前关联。禁止通过物理删除和不可逆搬移实现人工合并。
8. 新表和新增列使用外键、乐观锁版本、UTC时间、必要的检查约束及查询索引；证据JSON限制结构和大小，不保存凭据或完整原始Payload。
9. 迁移必须覆盖空MySQL 8.0.42、既有V1至V7升级、已有OpenAlex成果与关系回填，以及失败回滚/前向修复说明。

#### 批次 4.3：Crossref客户端与适配器

1. 继续使用现有Spring `RestClient`、Jackson、请求门禁和错误模型，不引入Crossref第三方SDK或新的HTTP框架。
2. 使用 `/works`、 `rows`、 `select`、 `filter` 和游标分页。阶段4继续沿用每页最多100条、每次最多5页或500条的本地安全上限，不因Crossref允许更大页数而扩大任务边界。
3. 后续游标请求必须重复首个请求的全部查询参数，只替换游标值；游标按不透明值保存和URL编码，不解析、拼接或记录完整值。
4. 一次性回补可使用封闭的 `from-pub-date`/`until-pub-date`；每日更新使用同时具备起止值的 `from-index-date`/`until-index-date` 小时间窗口，不允许只有起点的开放结果集。
5. Crossref当前游标结果集可能在运行中变化。适配器保存首个响应的 `total-results`，完成时对账读取数量和去重数量；不一致时将窗口标记为部分成功并允许整窗幂等重试，不直接推进每日水位。
6. 游标模式不与published/issued相关排序组合；大范围任务拆分为日或更小窗口，减少索引变化造成的遗漏或重复。
7. 解析字段至少覆盖DOI、URL、title、type、language、published/issued日期精度、container-title、ISSN、作者姓名、ORCID、affiliation/ROR、subject、reference DOI及indexed时间。数组为空、字段缺失或日期不完整时保留真实精度，不虚构默认值。
8. Crossref摘要可能包含JATS/HTML且可能受出版方或作者版权约束。阶段4不把Crossref摘要写入标准摘要字段或直接展示，只随受限原始Payload按既有90天规则保存；以后如需使用必须单独确认清洗、展示和授权策略。
9. 默认并发1、保守请求频率，并动态遵守响应中的速率和并发上限。仅对连接超时、临时DNS、429、502、503和504有限重试，优先遵守 `Retry-After`；400、401、403、404和解析错误不盲目重试。
10. 使用可识别User-Agent并监控响应时间；所有日志和错误响应必须脱敏联系邮箱、游标、Authorization和完整查询参数。
11. 使用固定Crossref JSON样例验证解析，不把在线API作为确定性构建依赖。

#### 批次 4.4：跨来源标准化、确定性匹配与来源优先级

1. DOI继续按现有规则标准化；ORCID校验位、ISSN格式和ROR URI/ID统一处理。无效标识作为字段质量问题，不转成虚假稳定ID。
2. 将匹配指纹升级为来源无关版本，使用标准化标题、发表年份和来源无关的主要作者证据；不得继续把OpenAlex Author ID直接纳入跨来源指纹。保存指纹版本并幂等回填现有OpenAlex成果。
3. 成果自动匹配优先级固定为：同来源记录ID、标准化DOI、其他稳定成果ID。相同DOI跨来源自动关联同一成果，并记录规则版本、证据和高置信级别。
4. 无DOI记录即使指纹完全相同也只生成待审核候选，不自动合并；标题近似匹配、编辑距离和机器学习相似度在没有标注样本前不实现。
5. 作者仅在ORCID精确一致或同一来源稳定作者ID一致时自动关联。Crossref没有ORCID的作者按“来源记录+作者顺序”保持幂等出现，不因姓名相同自动合并；可生成包含姓名、机构和共同成果证据的候选。
6. 机构仅在ROR或已验证来源稳定ID精确一致时自动关联；Crossref纯文本affiliation保留来源名称并生成候选，不按名称直接合并。载体优先使用ISSN-L/ISSN精确关联。
7. 标准字段采用明确且与导入顺序无关的优先级：人工覆盖最高；出版登记型书目信息优先Crossref；主题、OpenAlex结构化机构和现有标准摘要优先OpenAlex；缺失值可由另一来源补充。每个被选择字段更新来源记录。
8. 来源值冲突时保留当前规范值、两条来源追溯和质量问题，不以最后导入者覆盖。人工决策存在时，后续采集不得自动推翻，除非人工撤销。
9. 两个来源以任意顺序和任意次数导入必须得到相同的规范成果、来源映射、关系和候选集合。

#### 批次 4.5：重复候选、人工治理与修订API

1. 扩展权限模型：管理员和数据运营人员可查看及处理治理任务；科研用户只能看到已生效的规范目录结果。写操作继续要求CSRF、服务端权限和审计。
2. 实现重复候选分页和详情，支持按实体类型、状态、来源、规则版本和创建时间筛选；响应展示有限的匹配证据，不返回完整原始Payload。
3. 实现候选接受和拒绝。接受前使用事务锁和乐观版本校验，确认两个实体仍有效、目标为规范实体且不存在冲突人工决定；拒绝后同一规则版本不得反复生成相同候选。
4. 接受合并采用逻辑规范实体关联，不删除原实体、来源记录、关系或修订历史。目录查询聚合规范实体成员的来源追溯，阶段5图投影只读取规范实体。
5. 实现合并撤销：只能撤销仍为当前生效决定且未被后续依赖决定覆盖的记录；撤销在事务内恢复此前规范关联并写入新决策记录，不修改历史审计。
6. 实现成果允许字段的人工修正和撤销，至少覆盖标题、类型、语言、发表日期和载体。人工覆盖与来源规范值分离保存，后续来源更新不能覆盖人工值。
7. 每次接受、拒绝、撤销、字段修正和修正撤销都保存操作人、原因、前后值摘要、规则/版本和时间，并写入现有审计日志；原因不能为空且限制长度。
8. 接口沿用系统设计中的 `/api/v1/duplicate-candidates`、接受/拒绝和 `/api/v1/merge-decisions/{id}/revert` 边界；字段修正保持在成果资源下，OpenAPI先于控制器实现更新。

#### 批次 4.6：数据质量指标与冲突定位

1. 在每个采集运行中累计来源级质量指标：总记录、有效记录、缺失/无效DOI、缺失标题、缺失日期、缺失作者、无稳定作者标识、无ROR机构、字段冲突、自动匹配和新增候选数量。
2. 指标必须关联source ID、task ID、run ID、指标代码、分子、分母和计算时间，比例由服务端统一计算；分母为0时返回空值而不是0%。
3. 实现按来源、运行和指标查询的只读API，使数据运营人员能从异常指标定位到有限的失败记录、冲突或候选；科研用户不访问内部质量证据。
4. 解析字段缺失属于质量问题时不得自动升级为任务失败；违反必需标识、响应结构或事务完整性时仍按现有失败模型处理。
5. 阶段4只提供数据库内指标和API，不增加Prometheus、外部告警平台、规则引擎或前端仪表板；系统内告警页面仍属于阶段7。

#### 批次 4.7：目录聚合、引用解析与双源一致性

1. 成果列表默认只返回规范实体，详情聚合逻辑成员的OpenAlex和Crossref来源追溯，并明确展示当前字段来源和人工覆盖状态。
2. 作者、机构和载体关联查询使用规范实体ID，避免重复计数；MyBatis查询使用批量加载或集合查询，防止双源聚合产生N+1。
3. Crossref参考文献中的DOI与现有成果DOI精确关联；后续新成果出现时执行幂等补解析。无DOI参考文献不做模糊匹配。
4. 规范实体被合并或撤销后，目录计数、筛选、详情、作者顺序、机构、主题、引用和来源追溯应立即保持一致，不需要写Neo4j。
5. 所有自动关联、人工决定和修订只提交MySQL；阶段4仍不创建Outbox或写入Neo4j业务节点和关系。

#### 批次 4.8：测试、文档与双源在线验收

1. 增加Crossref固定样例契约测试：正常页、空页、缺失数组、日期精度、无ORCID、带ROR、无ROR、无效DOI、无效JSON、超大响应、429、403和临时5xx。
2. 增加游标测试：所有参数跨页保持一致、游标URL编码、末页无next-cursor、返回条数小于rows、封闭index窗口、总数不一致和整窗重试。
3. 使用MySQL Testcontainers验证V1至阶段4空库迁移、V1至V7升级、既有OpenAlex数据回填、唯一约束、事务回滚和Flyway `validate`。
4. 增加双源顺序测试：OpenAlex后Crossref、Crossref后OpenAlex、两个来源重复导入、相同DOI、无DOI、来源字段冲突和一方字段缺失；结果必须与顺序无关。
5. 增加实体边界测试：ORCID精确作者、同名不同作者、ROR精确机构、同名机构、ISSN载体、Crossref无稳定作者ID和无DOI参考文献。
6. 增加治理测试：候选去重、并发接受、拒绝抑制、逻辑合并、依赖存在时禁止撤销、成功撤销、人工字段覆盖、来源重导不覆盖人工值和修订撤销。
7. 增加权限与安全测试：匿名、科研用户越权、CSRF、任意URL、重定向、联系邮箱脱敏、原始Payload边界和审计敏感信息扫描。
8. 执行OpenAlex和Crossref各一次不超过5页/500条的在线小批量验收，并选取同一DOI范围验证双源融合及重复执行。在线失败必须按来源、网络、限流或环境阻塞如实报告，不能用固定样例冒充。
9. 更新README、OpenAPI、权限矩阵、需求追溯、系统设计、开发计划并新增阶段4验收记录，记录实际规则版本、已验证行为、限制和未实施内容。

### 9.4 阶段 4 主要交付物

- 阶段4 Flyway增量迁移及V1至V7安全升级测试；
- Crossref固定官方数据源、HTTP客户端、适配器、固定样例和在线验收；
- 两来源共用的Batch/Quartz/检查点及标准化流水线；
- 来源无关指纹、确定性匹配、字段来源和导入顺序无关的融合策略；
- 重复候选、逻辑合并、接受/拒绝/撤销、人工字段修正和修订历史；
- 按来源及运行定位的数据质量指标API；
- 双源规范目录、来源追溯及更新后的OpenAPI、权限矩阵和使用文档。

### 9.5 阶段 4 建议验证命令

~~~powershell
git status --short --branch
docker version
docker compose -f .\deploy\compose.yaml config --quiet
docker compose -f .\deploy\compose.yaml up -d --no-recreate neo4j
docker compose -f .\deploy\compose.yaml ps

.\mvnw.cmd -f .\backend\pom.xml verify
npm --prefix .\frontend run test
npm --prefix .\frontend run build

Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/liveness
Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/readiness
Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/graph

git diff --check
git diff -- .\docs .\backend .\frontend .\deploy .\README.md
git status --short --branch
~~~

运行Compose、本地应用和在线来源验收前，必须按README在当前终端安全注入凭据。若使用Crossref polite pool，可在当前进程设置 `CROSSREF_CONTACT_EMAIL`，但执行输出、报告和截图不得包含真实邮箱、数据库凭据、Cookie、CSRF令牌或完整游标。

### 9.6 阶段 4 退出条件

- 批次4.0全部通过，阶段3无未解决回归；
- V1至阶段4迁移可从空MySQL 8.0.42执行，也可在保留既有OpenAlex数据的V1至V7数据库上安全升级并通过Flyway `validate`；
- OpenAlex与Crossref均可独立创建一次性和每日任务，复用同一套状态机、Batch、Quartz、检查点和控制API；
- Crossref游标跨页保持参数一致，封闭index时间窗口可恢复；结果数量变化时不推进水位并可整窗幂等重试；
- 相同DOI无论来源导入顺序如何都只形成一个规范成果，并保留两条独立来源追溯；
- DOI、ORCID、ROR和ISSN确定性匹配具备证据和规则版本；无DOI、同名作者和纯文本机构不发生激进自动合并；
- 两来源字段冲突按固定优先级处理，目录可查看当前字段来源，人工覆盖不会被后续采集覆盖；
- 重复候选可接受、拒绝和受控撤销，逻辑合并不删除原实体或来源证据；人工字段修正及撤销完整审计；
- 数据质量指标可按来源和运行定位缺失标识、字段冲突、自动匹配和候选数量；
- OpenAlex阶段3全部回归测试、Crossref契约与在线小批量测试、双源顺序测试、前端回归和构建全部通过；
- README、OpenAPI、权限矩阵、需求追溯、系统设计、阶段4验收记录和开发计划与最终实现一致。

### 9.7 阶段 4 非目标

- 不实现标题模糊距离、机器学习实体匹配、向量检索或未经标注样本验证的数值阈值；
- 不下载Crossref公共全量文件、Metadata Plus快照或建立全库镜像；
- 不抓取Crossref链接的PDF/HTML全文，不直接展示或写入标准字段中的Crossref JATS/HTML摘要；
- 不将Crossref subject自动映射为OpenAlex Topic，不建立未经验证的主题词表；
- 不实现Outbox、Neo4j业务图投影、图查询、图对账或图重建；
- 不实现数据治理业务前端、统计图表、导出或外部告警；
- 不引入Crossref第三方SDK、搜索引擎、规则引擎、消息队列、缓存服务或微服务拆分；
- 不实施服务器部署、生产HTTPS、公网访问或多实例调度；
- 不创建Git提交、分支、标签或远程变更，除非用户另行明确授权。

### 9.8 2026-09-02 阶段 4 实施结论

批次4.0至4.8已经按顺序完成。实现复用了阶段3的适配器契约、Quartz、Spring Batch、检查点、MyBatis和目录模块，只增加Crossref及MySQL治理所需的最小通用化；V1至V7保持不变，阶段4结构通过V8不可变迁移增加。

当前确定性自动关联仅基于DOI、ORCID、ROR、ISSN和经验证的来源稳定标识。无DOI成果、无ORCID作者、同名作者和纯文本机构只生成可复现候选，不存在按标题、姓名或机构名称执行的模糊数值阈值自动合并。逻辑合并保留原实体和来源证据，人工决定及字段修正可追溯并受控撤销。

本阶段未实现Outbox、Neo4j业务图、业务前端或服务器部署，也未执行Git清理、重置、提交、分支、标签或推送。逐批次门禁、在线失败与修复、完整测试命令和退出条件对照见[阶段4验收记录](./stage4-acceptance.md)。阶段4完成后停止，不自动进入阶段5。

## 10. 阶段 5：Neo4j 图投影（已实施）

实施状态：批次 5.0 至 5.8 已按顺序执行。实际变更、逐批门禁、故障演练、验证结果与限制见[阶段5验收记录](./stage5-acceptance.md)。阶段 5 完成后停止，不进入阶段 6。

### 10.1 目标与完成定义

阶段5只建设后端图投影基础设施：通过MySQL事务Outbox把阶段4治理后的当前规范视图可靠投影到Neo4j，提供受限图查询、同步状态、失败重放、对账和可恢复的全量重建。MySQL始终是权威数据源，Neo4j只是可重建查询投影，两者不组成跨数据库事务。

本阶段完成不等于图谱前端或完整MVP完成。只有同时满足以下条件才可进入阶段6：

- 采集或治理事务提交时，业务变化与Outbox事件共同提交；事务回滚时二者共同回滚；
- Neo4j停机不影响认证、采集、治理和MySQL目录查询，待处理事件可在恢复后补偿；
- 重复、乱序和租约超时后的再次消费都不会创建重复节点或关系，也不会使新状态退回旧状态；
- 成果、作者、机构、载体、主题和已解析引用能够按当前规范关联形成正确图投影；
- 局部子图、同步状态和受限最短路径接口具备权限、输入、节点数、深度和超时边界；
- 初始回填、失败重放、对账和全量重建均经过MySQL 8.0.42与Neo4j 5.26 Testcontainers集成测试；
- 阶段0至阶段4后端回归、前端现有测试和生产构建继续通过，并形成独立阶段5验收记录。

### 10.2 必须保持的架构边界

1. **权威边界**：所有图数据必须从MySQL当前规范视图生成；图查询不得反向修改MySQL业务实体。
2. **聚合边界**：推荐只发布 `ACHIEVEMENT` 图刷新事件。每个事件表示“按当前MySQL状态重建该成果及其一跳关系”，不把来源Payload或完整业务快照复制到Outbox。
3. **版本边界**：不要直接假设 `achievement.version` 覆盖全部关系和治理变化。推荐新增按成果维护的 `graph_projection_state`，以 `desired_version` 和 `applied_version` 表达期望与已投影版本。
4. **事务边界**：采集、治理、版本递增和Outbox写入使用现有主 `JdbcTransactionManager`；Neo4j写事务在MySQL事务提交后单独执行。任何方法都不得伪装成MySQL与Neo4j原子提交。
5. **访问方式**：复用Spring Data Neo4j提供的命令式 `Neo4jClient`，使用固定Cypher模板和参数绑定；不建立一套与MySQL领域对象重复的Neo4j Repository实体层。
6. **调度边界**：复用现有Quartz运行小批量Outbox轮询，使用不可并发执行的固定Job；全量回填、对账和重建复用Spring Batch。不得增加Kafka、RabbitMQ或Redis。
7. **降级边界**：Neo4j约束初始化或连接失败只能使图同步/图查询降级，不能阻止MySQL目录接口和应用主体启动。
8. **数据边界**：图中只保存展示和遍历需要的有限字段，不保存原始Payload、摘要全文、凭据、审计详情或来源完整响应。
9. **当前删除边界**：阶段4没有通用业务删除字段或删除API。阶段5只处理已存在的规范合并、撤销和图侧陈旧节点清理；通用成果删除业务须另行确认，不能在本阶段暗中引入。

### 10.3 图模型实施口径

节点继续采用系统设计中的标签，`businessId` 使用MySQL稳定主键的字符串形式，并统一增加 `aacvManaged=true`、`projectionVersion` 和 `syncedAt`：

| Neo4j标签 | MySQL来源 | 主要属性 | 规范化规则 |
| --- | --- | --- | --- |
| `Achievement` | `achievement`及人工字段修正 | title、doi、type、year | 只投影当前规范成果，字段值与目录当前选择规则一致 |
| `Author` | `author`及外部标识 | displayName、orcid | `canonical_entity_link`成员统一指向规范作者 |
| `Institution` | `organization`及外部标识 | name、standardCode、countryCode | 保留既有系统设计标签，不新增同义 `Organization` 标签 |
| `Venue` | `venue`及外部标识 | name、type、issn | 人工venue覆盖和规范载体关联必须生效 |
| `Topic` | `subject`、`achievement_subject` | name、code | 使用阶段4多来源主题结构，不从原始文本临时造节点 |

关系实施口径如下：

| 关系 | 方向 | 关键属性与幂等键 |
| --- | --- | --- |
| `AUTHORED` | `Author` → `Achievement` | authorOrder；由两个端点和固定关系类型唯一确定 |
| `AFFILIATED_WITH` | `Author` → `Institution` | 使用成果、作者、机构组成的稳定 `authorshipKey`，防止同一作者在多项成果中的隶属证据互相覆盖 |
| `PUBLISHED_IN` | `Achievement` → `Venue` | 由两个端点唯一确定 |
| `HAS_TOPIC` | `Achievement` → `Topic` | 保存position；由两个端点唯一确定 |
| `CITES` | `Achievement` → `Achievement` | 只为已解析到 `cited_achievement_id` 的引用建边；未解析外部ID继续留在MySQL |

作者合作和机构合作由共同成果查询推导，不在阶段5物化 `COOPERATES_WITH` 或 `INSTITUTION_COOPERATES_WITH`，避免提前承担派生边维护成本。阶段7根据实际查询性能再决定是否物化。

### 10.4 阶段 5 执行批次

#### 批次 5.0：阶段 4 回归与Neo4j写入门禁

1. 重新读取适用项目指令、需求分析、系统设计、本计划和阶段4验收记录，执行 `git status --short --branch` 并记录未跟踪工作树边界。
2. 复跑后端 `verify`、前端测试和生产构建；任何阶段4回归失败先定位并报告，不得带病进入图实现。
3. 安全地配置当前进程所需MySQL和Neo4j环境变量，只检查变量是否存在，不输出其值；复核Docker Engine、Neo4j 5.26 Community和三个健康组。
4. 在首次图写入前只读执行 `RETURN 1`、`SHOW CONSTRAINTS`、`SHOW INDEXES`，并统计现有标签、关系类型及 `aacvManaged` 数据。发现与计划标签或约束冲突的未知数据时停止，不得清空图数据库。
5. 确认V1至V8校验通过，确认当前没有半成品Outbox、图接口或Neo4j业务节点需要迁移。

本批次退出门禁：阶段4回归通过；目标Neo4j可连接；现有图对象已盘点且不会被阶段5初始化覆盖或删除。

#### 批次 5.1：先冻结事件、API与权限契约

1. 在 `docs/openapi.yaml` 中先定义局部子图、最短路径、同步状态、事件分页/重放、对账运行和全量重建接口，再实现控制器。
2. 冻结内部枚举：聚合类型初始只有 `ACHIEVEMENT`；事件类型初始只有表达当前状态重建的 `REFRESH`；状态至少包含 `PENDING`、`PROCESSING`、`SUCCEEDED`、`DEAD`。
3. 冻结图响应结构：`nodes`、`edges`、`rootNodeId`、`truncated`、`appliedLimits`、`syncedAt`、`projectionLagSeconds`和 `traceId`；不返回Cypher、堆栈、原始Payload或内部锁信息。
4. 增加 `GRAPH_READ`、`GRAPH_SYNC_READ`、`GRAPH_SYNC_MANAGE` 权限：三个角色都可读取图；管理员和数据运营人员可查看同步状态/事件；单条重放和对账允许管理员与数据运营人员，全量重建只允许管理员。
5. 更新权限矩阵、统一错误码和安全错误响应，写接口继续要求CSRF并记录审计。

本批次退出门禁：OpenAPI可被项目内SnakeYAML测试解析；权限矩阵与后端策略一一对应；未经认证、越权和缺少CSRF的行为已写成失败测试。

#### 批次 5.2：V9事务Outbox与投影状态结构

1. 保持V1至V8不可变，新增单一主题迁移 `V9__create_graph_projection_schema.sql`。
2. 新增 `graph_projection_state`：以 `achievement_id` 为主键，至少保存 `desired_version`、`applied_version`、最近入队时间和最近成功投影时间；所有版本单调递增。
3. 新增 `graph_outbox_event`：保存唯一 `event_id`、成果ID、期望版本、事件类型、状态、尝试次数、下一次时间、锁持有者、租约截止时间、安全错误码、创建/更新时间和完成时间。
4. 新增 `graph_sync_dead_letter`：一对一关联最终失败事件，保存安全错误摘要、失败时间和人工重放关联，不复制敏感详情。
5. 新增轻量 `graph_maintenance_run`：记录 `INITIAL_BACKFILL`、`RECONCILE`、`FULL_REBUILD` 的状态、游标、有限统计、发起人和错误码；不把大量业务ID塞入单个JSON。
6. 建立待处理认领、租约恢复、聚合顺序、死信查询和维护运行所需索引与检查约束；数据库约束与应用校验必须同时存在。
7. 迁移本身不得遍历既有成果生成海量事件。现有数据的首次投影由后续显式初始回填Job完成。

本批次退出门禁：空库可执行V1至V9；V8升级到V9不丢失阶段4数据；状态/版本/唯一约束、事务回滚和重复插入均有MySQL 8.0.42 Testcontainers测试。

#### 批次 5.3：业务事务内发布图刷新请求

1. 在 `graph` 模块定义最小 `GraphProjectionRequestPort`，由MyBatis实现版本递增和Outbox写入；`ingestion` 与 `governance` 只依赖该端口，不依赖Neo4j客户端。
2. 采集写入在成果、作者、机构、载体、主题和引用关系全部落库后，为受影响规范成果增加一次刷新请求；同一事务内重复影响同一成果时应合并或生成可安全重复的版本事件。
3. 引用后到并回填 `cited_achievement_id` 时，同时刷新被回填的引用方成果，确保 `CITES` 最终出现。
4. 接受成果、作者、机构或载体合并时，找出受影响成果并在同一治理事务内批量入队；撤销合并时刷新原成员与当前规范目标的受影响成果。
5. 成果字段修正和修正撤销必须刷新对应规范成果；单纯拒绝重复候选不会改变图，不产生事件。
6. MySQL事务提交失败时业务数据、投影版本和Outbox必须全部回滚；不得在这些入口同步调用Neo4j。

本批次退出门禁：采集、字段覆盖、合并、撤销和引用回填的原有集成测试同时断言Outbox；注入异常后验证业务与事件共同回滚；Neo4j停机时MySQL事务仍可成功。

#### 批次 5.4：有界认领、消费、重试与死信

1. 使用MySQL 8的 `SELECT ... FOR UPDATE SKIP LOCKED` 在短事务中按成果和版本顺序认领到期事件，写入 `locked_by` 与 `locked_until` 后立即提交；Neo4j网络调用期间不得持有MySQL行锁。
2. 复用Quartz注册固定、不可并发的同步Job。默认每10秒触发、单次最多50项、单个消费者线程；这些边界允许配置但必须校验并设置硬上限。
3. 消费前从MySQL重新读取该成果的最新规范快照和 `desired_version`。旧事件可以触发最新状态刷新，但不得携带旧快照覆盖新数据。
4. Neo4j事务成功后再用独立MySQL短事务推进 `applied_version` 并完成事件；若完成标记失败，重复消费依靠幂等Cypher恢复。
5. 临时连接、超时和可重试服务错误采用有抖动的指数退避，建议最多5次、基础30秒、最长30分钟；无界重试和紧密循环均禁止。
6. 超过上限后原事件进入 `DEAD` 并写死信。人工重放不篡改原死信，而是按MySQL当前状态创建新的刷新版本并记录 `replay_of_event_id` 与审计。
7. 应用重启后回收过期租约；同一成果已有更早未完成版本时，不并发处理更高版本。

本批次退出门禁：并发认领不重复占有事件；租约过期可恢复；退避有界；死信可定位；重复完成、MySQL完成标记失败和应用重启均通过测试。

#### 批次 5.5：Neo4j约束、幂等投影与规范合并

1. 将Neo4j初始化Cypher放在版本控制资源目录中，使用明确名称和 `IF NOT EXISTS`；记录阶段5图模式版本，但不引入Neo4j专用迁移框架。
2. 为五种业务节点的 `businessId` 建属性唯一约束，并为实际过滤字段建立必要索引。Community版只使用已验证可用的属性唯一约束和索引，不使用仅Enterprise提供的存在、类型或Key约束。
3. 初始化失败时将图能力标记为不可用并阻止消费，不使Spring Boot整体启动失败；初始化恢复后才继续处理Outbox。
4. 使用固定标签、固定关系类型和参数化属性执行 `MERGE`。节点先按 `businessId` 合并，关系只在两个已绑定端点之间合并；不得拼接用户输入形成标签、关系类型或Cypher片段。
5. 每次刷新以一个成果为边界，在单个Neo4j写事务内更新规范节点、删除该成果范围内已不再存在的受管关系、重建当前关系并写入版本和同步时间。
6. 规范合并后不保留成员实体的重复业务节点；只有 `aacvManaged=true` 且已确认陈旧的节点可以清理。任何非AACV节点、关系、索引和约束都不得修改。
7. 未解析引用不生成占位 `Achievement`；待目标成果出现并在MySQL回填后再生成 `CITES`。
8. 写事务配置显式超时，所有网络、会话和结果资源由Spring Data Neo4j/Driver约定可靠释放。

本批次退出门禁：同一快照连续投影、同一事件并发重试、旧版本晚到均不会重复或回退；五类节点和五类关系正确；合并/撤销、字段覆盖及引用后到结果与MySQL规范目录一致。

#### 批次 5.6：受限图查询与同步状态接口

1. 实现 `GET /api/v1/graph/subgraph`：中心类型只允许成果、作者、机构、载体、主题；中心ID必须为正整数；深度默认1、最大2；节点上限默认100、硬上限300。
2. 关系类型、节点类型、成果年份和成果类型使用固定枚举白名单。按中心类型和深度选择预定义查询模板，不接收任意Cypher。
3. 分层查询并在每层消耗剩余节点预算，稳定去重和排序；达到预算时返回 `truncated=true`、实际限制和收窄建议，不一次性加载完整图。
4. 实现 `GET /api/v1/graph/path`：只允许两个明确节点，最大跳数6，只返回一条确定性最短路径，并设置建议3秒的服务端事务超时。
5. 实现 `GET /api/v1/graph/sync-status`：返回Neo4j可用性、图模式版本、待处理/处理中/死信数量、最老待处理年龄、最近成功时间和是否超过5分钟建议延迟；不得泄露锁标识或内部异常。
6. 图不可用、正在全量重建或查询超时时返回稳定 `application/problem+json` 错误；同一时间MySQL目录接口必须保持可用。

本批次退出门禁：空图、中心不存在、非法枚举、负数/超限参数、高度节点、达到300节点、超时、Neo4j不可用和越权均有自动化测试；正常响应字段与OpenAPI一致。

#### 批次 5.7：初始回填、对账、重放与全量重建

1. 实现受审计的初始回填Job，通过MySQL主键游标分页扫描当前规范成果并生成刷新请求；可暂停、失败后从游标恢复，不能在内存中一次加载全部成果。
2. 实现事件分页与单条重放接口，查询只返回安全错误码和摘要；管理员及数据运营人员可重放，任何操作不得要求客户端提交Cypher。
3. 实现对账Job：比较五类规范实体和五类关系数量，按主键游标抽查/比较业务ID与投影版本，识别MySQL缺失、Neo4j缺失、版本落后、陈旧受管节点和长期积压事件。
4. 对账发现差异时生成新的成果刷新请求，不直接修改MySQL权威实体；差异数量、有限样本和修复事件数写入维护运行记录。
5. 实现管理员专用全量重建：需要显式确认字段和CSRF，取得单实例维护锁，暂停普通消费者，只删除 `aacvManaged=true` 的业务投影，随后按MySQL主键分页重建并恢复消费者。
6. 全量重建中图查询返回明确的重建状态，不把部分图冒充完整结果；重建失败后MySQL不受影响，可从维护游标继续或重新执行。
7. 重建不得执行无条件 `MATCH (n) DETACH DELETE n`，不得删除未知节点、关系、索引、约束或Neo4j卷。

本批次退出门禁：既有MySQL数据可完成首次回填；制造缺失、陈旧和积压后对账能发现并补偿；全量重建结果与重建前规范投影一致；预置非AACV节点在重建后仍存在。

#### 批次 5.8：完整验收与文档同步

1. 复跑阶段0至阶段4全部回归；对新增Graph测试按Outbox、投影、查询、权限、故障恢复和维护任务分组记录数量与结果。
2. 使用Testcontainers实际运行MySQL 8.0.42与Neo4j 5.26 Community，验证空库V1至V9、V8升级、Cypher约束初始化、幂等消费和重建。
3. 在本地联合环境做一次受控故障演练：停止Neo4j后完成MySQL写入并观察积压，再恢复Neo4j并确认补偿；不得删除现有卷。
4. 复核Neo4j不可用时的liveness、readiness、graph健康组和MySQL目录接口，确保健康语义与降级设计一致。
5. 更新需求追溯、系统设计实际边界、OpenAPI、权限矩阵、README运行说明和 `docs/stage5-acceptance.md`；只记录已实际执行的命令和结果。
6. 执行差异、空白、敏感信息、临时文件和工作树复核；由于仓库当前无跟踪文件，必须结合显式文件清单和源码复读，不能只依赖空 `git diff`。

本批次退出门禁：第10.1节完成定义逐项有证据；所有失败、环境限制和未验证项被明确记录；阶段5完成后停止，不进入阶段6业务前端。

### 10.5 预计涉及的文件与组件

实际文件名应遵循现有包结构，预计最小范围如下：

- `backend/src/main/resources/db/migration/V9__create_graph_projection_schema.sql`；
- `backend/src/main/java/com/aacv/system/graph/` 下的应用端口、Outbox服务、投影器、查询、运维和API对象；
- `backend/src/main/resources/mapper/graph/` 下的Outbox、规范快照和对账MyBatis XML；
- `backend/src/main/resources/neo4j/schema/` 下的版本化幂等Cypher；
- 现有 `ingestion` 与 `governance` 事务接入点，以及引用回填路径；
- `Permission`、`AuthorizationPolicy`、`AuditAction`、`ErrorCode`、`application.yml`；
- `docs/openapi.yaml`、`docs/authorization-matrix.md`、`docs/system-design.md`、`docs/requirements-analysis.md`、`README.md`；
- MySQL/Neo4j Testcontainers集成测试、权限与OpenAPI测试，以及新增 `docs/stage5-acceptance.md`。

预计不需要修改 `backend/pom.xml` 或前端业务代码。若实现中发现必须新增或升级依赖，应停止并单独说明必要性、兼容性和维护成本。

### 10.6 建议验证命令

执行会话应先运行定向测试，再运行完整验证；测试类名允许按最终实现微调，但不得省略对应行为。

~~~powershell
# 阶段5迁移、Outbox事务和认领机制
.\mvnw.cmd -f .\backend\pom.xml '-Dtest=FlywayMigrationTests,GraphOutboxPersistenceTests' test

# Neo4j模式、幂等投影、合并与引用关系
.\mvnw.cmd -f .\backend\pom.xml '-Dtest=GraphProjectionIntegrationTests' test

# 查询限制、同步状态、对账和重建
.\mvnw.cmd -f .\backend\pom.xml '-Dtest=GraphQueryIntegrationTests,GraphMaintenanceIntegrationTests' test

# 权限、CSRF、错误模型和OpenAPI契约
.\mvnw.cmd -f .\backend\pom.xml '-Dtest=SecurityIntegrationTests,OpenApiDocumentTests' test

# 阶段0至阶段5后端全量验证
.\mvnw.cmd -f .\backend\pom.xml verify

# 既有前端回归与生产构建
npm --prefix .\frontend run test
npm --prefix .\frontend run build

# 需在当前PowerShell安全注入环境变量后执行的本地环境检查
docker version
docker compose -f .\deploy\compose.yaml config --quiet
docker compose -f .\deploy\compose.yaml ps

# 联合启动后的健康检查
Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/liveness
Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/readiness
Invoke-RestMethod -Uri http://127.0.0.1:8080/actuator/health/graph

# 最终工作树与空白检查
git diff --check
git status --short --branch
~~~

故障演练、重放、对账和全量重建必须使用隔离测试数据或明确标记的AACV受管图数据；禁止为了验证方便清空本机MySQL开发库或整个Neo4j卷。

### 10.7 阶段 5 退出条件

- V1至V9从空MySQL 8.0.42迁移成功，V8至V9升级保留阶段4数据；
- MySQL业务与Outbox原子提交/回滚，所有图相关写入均发生在提交后；
- 事件认领、聚合顺序、租约回收、有限重试、死信和人工重放均有自动化证据；
- Neo4j约束与索引可重复初始化，重复/乱序消费不创建重复节点或关系，不覆盖更新状态；
- 规范合并、撤销、人工字段修正、引用后到和陈旧受管节点清理结果正确；
- 局部子图默认100、硬上限300、深度最大2，最短路径最大6跳且有显式超时；
- 初始回填、对账和全量重建可恢复，且不会删除非AACV图数据；
- Neo4j故障期间MySQL目录继续工作，恢复后积压在正常条件下可补偿，5分钟延迟目标可被同步状态接口度量；
- 后端全量验证、前端现有测试和生产构建通过，文档与代码一致且没有敏感信息；
- 新增阶段5验收记录并停止在阶段5边界。

### 10.8 阶段 5 非目标

- 不实现业务前端、Cytoscape.js图谱页面或ECharts统计页面；
- 不物化作者合作和机构合作派生边，不引入GDS或APOC；
- 不实现任意Cypher控制台、图数据编辑API或从Neo4j反写MySQL；
- 不新增Kafka、RabbitMQ、Redis、Elasticsearch、OpenSearch或微服务；
- 不引入通用成果删除API或未确认的 `deleted_at/status` 业务语义；
- 不实施服务器部署、生产HTTPS、公网访问、多实例调度、集群或高可用；
- 不做10万成果/100万关系最终性能验收，该容量验收仍属于阶段8；
- 不创建Git提交、分支、标签、推送或远程资源变更，除非用户另行明确授权。

## 11. 阶段 6：业务前端

实施范围：只完成批次6.0至6.8。阶段6不实现阶段7的图谱可视化、统计分析、导出和运维页面，不实施服务器部署或Git历史操作。

### 11.1 目标与完成定义

完成日常业务操作所需页面，使认证、数据源、任务、成果和数据治理形成可用的浏览器工作流。前端只保存当前页面必要状态，服务端会话、权限和业务状态继续由后端负责。

### 11.2 必须保持的前端边界

1. 使用Vue 3、TypeScript、Vue Router、Vitest、Playwright和原生fetch；Element Plus是阶段6唯一业务UI组件库。
2. 不引入Pinia、Axios或自动导入插件；会话使用轻量响应式模块，API请求集中在单一fetch边界。
3. HttpOnly Cookie由浏览器管理，前端不读取或持久化会话Cookie；所有非安全HTTP方法先获取并携带`X-CSRF-TOKEN`。
4. 路由权限只改善交互，后端仍是最终授权边界；401清理本地会话并跳转登录，403、409、超时和会话过期分别展示。
5. 服务端分页参数保持`page`从0开始、`size`默认20且不超过100；外部文本只通过Vue文本插值展示，不使用`v-html`。

### 11.3 阶段6执行批次

实施状态（2026-09-02）：批次6.0至6.8已按顺序完成；验证证据和未验证边界见`docs/stage6-acceptance.md`。

#### 批次6.0：阶段5回归与前端门禁

读取项目指令、需求、设计、OpenAPI、权限矩阵和阶段5验收；检查Git及未跟踪文件；复跑后端`verify`、前端测试和生产构建；核对Node、npm及既有依赖。只有阶段5回归通过且接口契约足以支撑业务页面时才能继续。

#### 批次6.1：工具链、UI基线与测试基础

只按需增加Element Plus、`@vue/test-utils`、jsdom和`@playwright/test`；配置显式UI注册、jsdom组件测试和本机Edge端到端测试，不增加其他运行时依赖或自动导入能力。

#### 批次6.2：API、CSRF与会话基础

实现原生fetch客户端、`application/problem+json`解析、有界超时、CSRF获取与写请求注入、401会话失效通知，以及登录、退出和当前用户会话模块；为401、403、409、超时、空响应和CSRF写请求增加单元测试。

#### 批次6.3：登录、布局与权限路由

实现登录页、业务布局、工作台入口、权限菜单、路由守卫、403、404和会话过期反馈。管理员、数据运营人员和科研用户只看到其权限允许的入口。

#### 批次6.4：成果目录

实现八类组合筛选、服务端分页、成果详情、作者/机构/载体/主题目录和关联成果入口；详情展示作者、机构、主题、引用标识、字段来源和来源追溯，所有外部文本按纯文本呈现。

#### 批次6.5：数据源管理

实现数据源分页、创建/更新、启停和受限连通性探测。数据运营人员只读，管理员可执行写操作；页面不接受任意基础地址或凭据。

#### 批次6.6：采集任务与运行控制

实现任务分页、创建/更新、每日计划、触发、运行统计、暂停、恢复、取消、失败记录和有限重试；控制操作明确说明在安全检查点生效，并处理版本/状态冲突。

#### 批次6.7：数据治理与质量指标

实现重复候选筛选、有限证据、接受、拒绝、决定撤销、成果字段人工修正和撤销，以及质量指标筛选、比率和有限问题样本；不展示原始Payload。

#### 批次6.8：用户管理与完整验收

实现管理员用户分页、创建、启停、角色替换和密码重置；补齐关键组件和登录、权限、目录流程的Vitest与Playwright测试，复跑后端安全/OpenAPI回归和全量`verify`，同步README、系统设计和阶段6验收记录。

### 11.4 退出条件

- 管理员、数据运营人员和科研用户只能访问各自权限范围；
- 浏览器可以完成数据源配置、任务运行、成果查询和重复候选处理；
- 分页、空状态、加载、失败、超时和会话过期状态均有明确反馈；
- 前端单元测试、端到端测试和生产构建通过；
- 前后端均未暴露敏感配置。

### 11.5 非目标

- 不实现Cytoscape.js图谱页面、ECharts统计、导出和图同步运维页面；
- 不修改后端业务接口、MySQL迁移或Neo4j投影；
- 不引入Pinia、Axios、自动导入插件或第二套业务UI组件库；
- 不部署服务器，不创建Git提交、分支、标签或推送远程。

## 12. 阶段 7：可视化、分析、导出与运维（实施中）

### 12.1 目标与完成定义

完成图谱浏览、统计分析、受控导出和系统内运行监控能力。实施范围仅覆盖批次 7.0 至 7.8；阶段 8 的容量性能、备份恢复、真实凭据联合运行和本地最终交付不提前实施。

### 12.2 必须保持的架构与依赖边界

1. MySQL继续作为业务权威数据源，Neo4j继续作为可重建图投影；统计和导出从MySQL读取，图查询从Neo4j读取。
2. 复用现有模块化单体、MyBatis、Quartz、Spring Security、Spring Session、Actuator和前端原生`fetch`服务，不增加微服务或第二套状态管理、HTTP客户端和UI框架。
3. 前端只允许按实际页面需要增加Cytoscape.js和ECharts；不引入Pinia、Axios、自动导入插件或新UI框架。
4. 不引入APOC、GDS、消息队列、Redis、搜索引擎或外部告警平台；合作关系优先通过现有图模型和受限查询推导，不预先物化合作边。
5. 所有修改请求保持CSRF、后端权限和应用服务权限双重边界；导出和运维操作记录安全摘要审计。
6. 所有列表继续使用服务端分页，默认20、最大100；局部图默认100节点、硬上限300节点；单个导出任务最多10,000条。

### 12.3 阶段 7 执行批次

#### 批次 7.0：阶段 6 回归、环境与依赖门禁

读取项目指令、需求、设计、OpenAPI、权限矩阵和阶段6验收记录；检查Git状态及未跟踪文件；核对JDK、Node、npm、Docker、Compose和依赖清单；复跑后端全量`verify`、前端Vitest、生产构建和Playwright阶段6流程。仅当失败能够修复或明确归因于执行环境，并且宿主复跑通过时，才能进入批次7.1。

2026-09-02复核结果：Temurin JDK 21.0.12.1、Node.js 24.14.0、npm 11.9.0、Docker Desktop 4.72.0、Docker Engine 29.4.2和Compose 5.1.3符合当前基线；后端123项测试、前端12项测试、Playwright 3项流程和生产构建通过；Flyway V1至V9空库及升级路径通过；使用非敏感临时占位值的Compose静态解析通过。普通受限上下文首次执行前端命令出现`spawn EPERM`，Docker未启动时后端Testcontainers失败；启动本机Docker Desktop并在宿主上下文原样复跑后全部通过，确认属于执行环境而非代码回归。项目清单未直接引入Pinia、Axios、APOC、GDS或消息队列。

#### 批次 7.1：先冻结 OpenAPI、权限与错误契约

1. 将OpenAPI升级为阶段7契约，先定义统计、导出、运维总览、系统内告警以及现有图查询前端所需字段；保持`/api/v1`、分页和`application/problem+json`约定。
2. 在权限枚举和角色矩阵中分别冻结统计读取、导出创建/读取和运维读取/处置权限；科研用户可以读取统计并导出授权目录结果，数据运营人员和管理员具备相同能力，只有管理员读取系统级运维与审计信息。
3. 导出创建、状态和下载均绑定创建者或管理员；下载使用不可预测令牌，不接受客户端文件路径；创建请求要求CSRF并记录审计。
4. 冻结导出状态`PENDING/RUNNING/SUCCEEDED/FAILED/EXPIRED`、格式`CSV/JSON`、最大10,000条、过期语义和稳定错误码。
5. 冻结监控总览、告警分页/确认、图事件和采集失败聚合字段；响应只提供有限安全摘要，不返回日志正文、SQL、Cypher、凭据或内部路径。

本批次退出门禁：OpenAPI可被项目内SnakeYAML测试解析；权限枚举、角色矩阵、Spring Security路径规则和后端策略一一对应；匿名、越权和写请求缺少CSRF的失败行为具备自动化测试；阶段7后续实现不得在无文档同步的情况下偏离已冻结契约。

2026-09-02验收结果：`docs/openapi.yaml`已升级为7.0.0，冻结统计、导出、运维总览和系统内告警的路径、字段、状态、错误及`x-required-permission`；后端和前端权限枚举、角色策略、Spring Security路径规则及权限矩阵一致。定向18项契约与安全测试通过，后端完整124项测试、前端12项测试和生产构建通过；未新增依赖或数据库迁移。系统设计中旧的图事件`retry`路径已按当前实现和OpenAPI更正为`replay`。

#### 批次 7.2：Cytoscape.js 局部图谱与可访问替代视图

按需增加Cytoscape.js，复用现有`/api/v1/graph/subgraph`、`/path`和`/sync-status`接口，实现中心节点加载、主动展开、关系/年份/成果类型/节点类型过滤、受限布局、图例、节点和关系详情侧栏、业务详情跳转、同步时间和截断提示。达到300节点硬上限时停止扩展并提示收窄范围；提供与当前图数据一致的节点/关系列表替代视图，颜色不是唯一的信息表达方式。

2026-09-02验收结果：仅新增Cytoscape.js 3.34.2（MIT）作为运行时依赖，未增加其他直接依赖。新增`/graph`权限路由和导航入口，使用现有图接口实现中心子图、最短路径、主动一跳扩展、受控筛选、节点类型图例、属性检查器、成果详情跳转、同步状态及服务端/客户端截断提示；客户端按稳定ID去重并在累计300节点时停止扩展，同时提供节点表和关系表。Vitest共15项通过，生产构建通过，Microsoft Edge下4项Playwright流程通过，其中阶段7图谱流程覆盖科研用户访问、图加载、截断提示、详情跳转和替代表格。

#### 批次 7.3：MySQL 统计聚合与 ECharts 页面

在独立`analytics`模块中使用MyBatis实现总览、年度趋势、成果类型/来源/机构/主题分布和作者/机构合作基础统计；所有查询使用受控过滤参数和有界排行。按需增加ECharts，图表旁显示数据范围、实际过滤条件、更新时间和表格摘要；Neo4j不可用不得影响MySQL统计。

2026-09-02验收结果：新增独立`analytics`模块，MyBatis从规范成果及现有作者、机构、主题、来源关联聚合，排除已并入规范实体的成员记录，并尊重成果类型和发表日期人工覆盖；支持年份范围、成果类型、来源、机构和主题六类过滤，机构/主题分布固定Top 20、合作排行限制1至100。作者与机构合作由共同规范成果即时推导，不新增表或图关系，整个统计模块没有Neo4j依赖。前端仅新增ECharts 6.1.0（Apache-2.0），采用模块化注册并提供五个图表、四项总览、实际筛选、MySQL口径、更新时间及同源表格摘要。后端6项统计定向测试、13项HTTP安全集成测试、18项权限与契约回归、前端16项Vitest及Edge下5项流程通过；生产构建通过并保留统计页独立懒加载块。

#### 批次 7.4：V10 异步导出任务与安全文件边界

通过Flyway V10增加导出任务元数据；在独立`export`模块中复用MySQL目录过滤语义和本进程有界执行器生成CSV或JSON。单任务最多10,000条，超过限制明确拒绝；使用服务端生成的不可预测下载令牌、配置化固定根目录、规范化路径校验、原子完成和过期时间，禁止客户端提供文件名或路径。单实例限制并发和每用户活动任务数，应用重启后将遗留`RUNNING`任务安全标记为失败或重新认领，不无限重试。

2026-09-02验收结果：新增Flyway V10与独立`export`模块，导出查询复用规范成果根记录、人工字段覆盖和规范实体过滤语义；异步任务使用本进程固定并发2、队列20及每用户最多2个活动任务，单任务超过10,000条时在创建阶段拒绝。CSV使用UTF-8 BOM、全字段引号和公式注入中和，JSON由既有Jackson生成；文件名仅由服务端任务ID生成，固定根目录下规范化解析、同目录临时文件原子替换并保留24小时。状态和下载执行创建者/管理员对象级校验及43字符随机令牌校验；遗留`RUNNING`任务在启动时标记为失败，待执行任务仅按有界容量重新提交，不无限重试。导出、迁移和OpenAPI定向19项测试、HTTP安全13项测试通过；未新增依赖，前端与审计闭环仍按7.5保留。

#### 批次 7.5：导出页面、下载与审计闭环

在成果目录保留当前筛选条件创建导出，提供任务状态、失败安全摘要、到期时间和下载入口；创建者只能读取和下载自己的任务，管理员可以协助查看。创建、成功、失败和下载写入受控审计摘要；测试CSV公式注入防护、JSON编码、数量上限、权限、CSRF、并发、过期、令牌和路径遍历边界。

2026-09-03验收结果：成果目录在`EXPORT_CREATE`权限下提供CSV/JSON导出，题名、年份、成果类型和固定来源代码直接映射冻结条件；作者、机构、期刊和主题文本必须通过现有目录接口唯一解析为规范实体ID，零匹配或多匹配会在创建前明确拒绝，避免扩大结果范围。前端使用原生`fetch`读取Blob并在内存中轮询任务状态，不引入新的HTTP客户端或状态库。后端对创建、真实成功迁移、真实失败迁移和有效下载分别记录由请求者归属的安全审计摘要；启动恢复覆盖全部遗留`RUNNING`任务，已终态任务不会产生伪失败审计。导出与安全定向30项、前端21项Vitest、生产构建和Edge导出流程通过；未新增依赖。

#### 批次 7.6：运维总览与系统内告警

在现有`operations`、`crawl`和`graph`能力之上聚合任务运行/失败、Outbox待处理/重试/死信、维护运行、审计和Actuator健康摘要；通过Flyway新增最小告警事件结构，仅持久化连续采集失败、解析成功率异常和图同步积压的系统内事件。告警生成和确认幂等，管理员读取和确认；不发送邮件、短信或即时通信通知。

2026-09-03验收结果：通过Flyway V11新增`alert_event`，以生成列唯一键保证同一类型与主体最多一个未确认事件，保留检测信号时间、首末检测时间、出现次数、证据、确认人/原因和乐观锁版本。独立`operations`应用服务聚合应用存活、MySQL、Neo4j、活动采集、近24小时失败、图同步积压/处理/死信和未确认告警；Quartz默认每60秒串行评估数据源连续3次失败、最近完成且至少读取20条记录的运行解析成功率低于80%、图同步最老待处理超过300秒或存在死信三类条件。相同或更旧信号不会重复计数，已确认告警只在出现更新信号时重新打开；确认要求管理员权限、CSRF、原因和版本，并写入安全审计。组合验收30项通过，覆盖V1至V11空库及V3/V7/V8升级、MySQL持久化、Quartz注册、HTTP权限/确认/审计和OpenAPI契约；未新增依赖或外部通知。

#### 批次 7.7：运行监控页面

实现管理员运行监控页，展示liveness、readiness、Neo4j独立状态、任务失败、图同步积压/死信、维护运行、告警和审计分页；已有重放、回填、对账和全量重建沿用阶段5权限。Neo4j短暂不可用时图谱区域明确降级，成果目录、统计和其他MySQL功能仍可进入。

2026-09-03验收结果：新增仅`OPERATIONS_READ`可进入的`/operations`懒加载页面和权限导航，分别读取Actuator liveness、readiness、graph健康组以及冻结的运维总览、告警、图事件、维护运行和审计分页。健康客户端保留HTTP 503响应中的合法`DOWN`状态，页面使用独立状态卡和分区错误处理；Neo4j降级时应用/MySQL状态、近24小时失败计数、告警与审计仍可见。告警确认、死信重放、回填、对账和全量重建复用原生`fetch`、CSRF与既有权限，全量重建必须在页面输入固定确认值。失败明细继续通过具体采集运行定位，不新增契约外聚合接口。前端24项Vitest、生产构建、Edge运行监控流程和后端权限/OpenAPI 5项回归通过；未新增依赖。

#### 批次 7.8：完整回归、文档同步与阶段验收

补齐后端权限/OpenAPI/MyBatis/Testcontainers、安全和恢复测试，补齐前端组件、图谱替代视图、统计、导出、运维以及Playwright关键流程；复跑全量后端`verify`、前端测试、生产构建、端到端测试和Compose静态解析。同步README、需求追溯、系统设计、权限矩阵、OpenAPI、开发计划和`docs/stage7-acceptance.md`，只记录实际执行的命令和结果。

2026-09-03验收结果：后端全量`verify`共159项测试通过并完成可执行JAR重打包，前端8个测试文件、24项Vitest通过，`vue-tsc -b`与Vite生产构建通过，Microsoft Edge下7项阶段6/7关键流程以单worker稳定通过，Compose使用非敏感占位值完成静态解析。首次全量回归发现应用启动测试仍断言V9，以及原始Payload清理计划晚于即时图Outbox任务注册导致Quartz表偶发死锁；已将迁移断言同步为V11，并让静态清理计划以最高优先级`ApplicationRunner`先注册，失败用例与Flyway/告警调度定向9项及后端全量回归均通过。Playwright原配置仍会跨文件启动5个worker竞争同一Vite开发服务器，已固定为单worker并恢复默认断言超时。OpenAPI 7.0.0与权限矩阵经全量测试确认无漂移，因此未修改冻结契约；未新增依赖，未进入阶段8。

### 12.4 退出条件

- 300节点内的局部图可以交互，达到边界时行为明确；
- 图谱具备表格替代视图，颜色不是唯一的信息表达方式；
- 统计口径可追溯到过滤条件和更新时间；
- 导出权限、数量上限、过期、并发和路径安全测试通过；
- 管理员可以定位采集失败、同步积压、死信、系统内告警和关键审计记录；
- 后端全量验证、前端测试、生产构建、阶段7端到端流程和Compose静态解析通过；
- README、OpenAPI、权限矩阵、需求追溯、系统设计、阶段7验收记录和开发计划与最终实现一致。

### 12.5 非目标

- 不实施阶段8的10万成果/100万关系容量与性能验收、备份恢复或真实凭据联合运行；
- 不物化作者或机构合作边，不引入APOC、GDS或消息队列；
- 不引入Pinia、Axios、新UI框架、自动导入插件、Redis、搜索引擎或外部告警平台；
- 不部署服务器，不创建Git提交、分支、标签或推送远程。

## 13. 阶段 8：非功能验收与本地交付

### 13.1 目标

依据需求文档完成性能、可靠性、安全、备份恢复和本地联合运行验收，形成可复核的第一阶段交付物。

### 13.2 工作项

1. 完成关键接口、任务状态机、幂等去重、Outbox、权限和导出安全的回归测试。
2. 使用Testcontainers验证MySQL、Flyway和Neo4j集成，不依赖开发库中的既有数据。
3. 构造或生成10万成果、100万图关系的非敏感测试数据，执行容量测试；测试工具与正式代码隔离。
4. 验证普通列表和详情查询P95不超过2秒，300节点以内局部图查询P95不超过3秒，并记录机器配置、数据规模、并发和测试方法。
5. 验证应用重启、网络超时、MySQL失败、Neo4j失败、重复消费、任务取消和资源释放。
6. 进行依赖、输入、SSRF、XSS、CSRF、越权、敏感日志、导出路径和配置安全复核。
7. 在仓库外创建 `E:\AACV_System_Backups`，实现MySQL每日逻辑备份、7个每日备份和4个每周备份保留策略；凭据不得写入脚本。
8. 通过新建隔离恢复数据库完成一次恢复演练，验证Flyway版本、业务数据和图投影重建。不得直接覆盖开发库。
9. 更新README、启动手册、配置说明、备份恢复手册、测试结果和已知限制。
10. 对照需求文档第11节逐项记录通过、失败或未验证，不得以“接口可启动”替代业务验收。

### 13.3 退出条件

- 后端全量验证、前端测试、前端构建和端到端测试均通过；
- Flyway能够从空库建库并验证通过；
- MySQL80、Neo4j容器、后端和前端完成本地联合启动；
- 两个来源的小批量真实采集通过，且固定样例测试仍可重复；
- 性能结果满足目标，或明确记录未满足指标及原因；
- 备份恢复演练成功，RPO 24小时、RTO 4小时的本地方案具备可执行依据；
- 文档、代码和实际配置一致，未引入秘密或临时调试文件。

### 13.4 2026-09-04 实施状态

阶段8仍为“实施中”，当前不能关闭退出门禁。容量环境已实际生成并校验100,000条成果、413,000个图节点和1,000,000条图关系；四个HTTP场景按固定预热、并发、样本数和nearest-rank算法实测P95均满足目标，原始证据保存在`docs/stage8-performance-evidence.json`。

新执行的后端完整`verify`共160项，0个断言失败、36个环境错误；MySQL 8.0.42 Testcontainers和Flyway可运行，但Codex派生进程在Neo4j Java Driver创建Netty selector时触发Windows `UnixDomainSockets` 的`Invalid argument: connect`，因此完整测试状态为“受阻”而不是通过。另行执行的72项无Neo4j Driver安全/可靠性定向测试、4项Flyway迁移测试和1项Neo4j超时配置绑定测试全部通过。前端24项Vitest、生产构建和7项Edge Playwright通过；Playwright仍是路由模拟，不替代真实账号联合验收。

已新增独立恢复Compose、业务逻辑备份和隔离恢复工具。备份使用临时文件后原子完成，生成SHA-256与非敏感计数元数据，目录ACL仅允许当前用户、SYSTEM和Administrators；保留删除必须显式使用`-ApplyRetention`。恢复只接受固定备份目录内的已校验SQL，拒绝非空MySQL或Neo4j目标，恢复后先禁用恢复副本中的外部采集，再启动独立后端验证成果抽样、执行受控图全量重建与零差异对账。恢复后端固定使用本机28080端口，启动前拒绝端口冲突，清理时只停止属于本次脚本进程树的监听进程。恢复容器、数据库和命名卷均与容量环境分离且不自动删除。

完整后端`verify`仍受普通PowerShell复跑门禁约束。Neo4j故障旧运行态曾出现45秒不返回；5秒Driver超时配置与绑定测试已经加入，但本轮后台`Get-Credential`未获得交互输入，安全重启及故障脚本尚未复验。`E:\AACV_System_Backups`仍未创建，新增备份恢复工具只完成静态验证，实际备份、7日/4周保留、隔离恢复、RPO/RTO计时和真实OpenAlex/Crossref联合运行仍待用户在凭据窗口输入后执行。阶段8整体保持未完成。

### 13.5 2026-09-05优化验证

新增四组导航、图谱名称检索及本机常用查询、OpenAlex有界额度恢复、固定范围历史复查、治理对照与版本保护、V13机构名称证据和学术字段覆盖。十万条组合查询根据实际执行计划优化，六个SQL场景最终P95均在一秒内。真实双源采集和隔离断连、逻辑恢复已有新证据；当前完整测试结果见[优化验收记录](./optimization-acceptance.md)，13.4节保留为历史记录。实际业务备份的目录ACL、文件流程、保留策略和RPO/RTO没有据此推定通过。

## 14. 全阶段验证矩阵

工程初始化后，按实际脚本名称执行以下验证。命令未执行或环境受阻时必须如实报告，不得视为通过。

~~~powershell
# 后端单元测试、集成测试和构建
.\mvnw.cmd -f .\backend\pom.xml verify

# 前端确定性安装、单元测试和生产构建
npm --prefix .\frontend ci
npm --prefix .\frontend run test
npm --prefix .\frontend run build

# 当前没有lint脚本；执行已经配置的端到端测试
npm --prefix .\frontend run test:e2e

# Neo4j 本地配置与状态
docker compose -f .\deploy\compose.yaml config
docker compose -f .\deploy\compose.yaml up -d neo4j
docker compose -f .\deploy\compose.yaml ps

# 联合启动后的健康检查
Invoke-RestMethod -Uri http://localhost:8080/actuator/health/liveness
Invoke-RestMethod -Uri http://localhost:8080/actuator/health/readiness
~~~

每个阶段至少还应执行：

- 当前变更的直接测试；
- 受影响模块的回归测试；
- `git diff --check`；
- 完整Git状态和差异复核；
- 敏感信息和临时文件检查；
- 文档与项目记忆同步检查。

## 15. 数据库与迁移纪律

1. Flyway是MySQL结构唯一迁移入口，不使用运行时自动建表替代迁移。
2. 每个迁移只承担一个清晰主题，命名说明业务目的；迁移必须确定、可复核并兼容MySQL 8.0.42。
3. 已应用迁移不得修改或重排；修复通过新迁移完成。
4. 破坏性迁移、字段收窄、删除表或批量数据修正必须单独评审，并提供备份、回滚或前向恢复方案。
5. 业务唯一约束必须由数据库约束和应用幂等逻辑共同保障。
6. 测试使用独立数据库或Testcontainers，不在本机开发库执行清空、重建或破坏性验证。
7. Neo4j约束、索引和初始化Cypher必须版本控制、幂等并有集成测试。

## 16. 必须停止并报告的情况

出现以下任一情况时，执行会话应停止当前阶段并向用户说明证据和方案：

- 发现与任务重叠的现有未提交代码，无法确认归属；
- 现有 `aacv_system` 数据库包含未知对象或数据；
- 需要用户提供密码、Token或其他秘密；
- 需要安装、升级或替换计划外依赖；
- Flyway校验失败或迁移可能破坏现有数据；
- 外部数据源条款、限流或返回格式与设计假设冲突；
- 必须扩大到服务器、HTTPS、公网、微服务、消息队列或搜索引擎才能继续；
- 当前阶段测试失败且无法在授权范围内修复；
- 需要删除文件、清理数据库、重写Git历史或执行其他不可逆操作。

## 17. 第一阶段明确不实施的内容

- 服务器部署、域名、证书、生产HTTPS和公网访问；
- 微服务拆分、多实例水平扩容和分布式基础设施；
- Redis、Kafka、RabbitMQ、Elasticsearch、OpenSearch和向量数据库；
- arXiv、OAI-PMH、机构网页、PDF全文、验证码或登录后页面采集；
- 绕过robots.txt、反爬控制、付费墙或访问控制；
- 邮件、短信和即时通信外部告警；
- LDAP、CAS、OAuth 2.0和OpenID Connect；
- 专利、项目、奖项、著作、软件和数据集；
- 将Neo4j 4.4作为运行或兼容基线。

## 18. 后续会话启动边界

阶段5已完成并形成独立验收记录。后续执行会话必须重新读取项目指令、需求分析、系统设计、本开发计划及阶段5验收记录，检查Git状态，并以当前代码、迁移、依赖和实际测试结果为准。除非用户另行明确授权，不得自动进入阶段6。

可在新会话使用以下启动指令：

~~~text
阶段5已验收。后续工作请先对照 docs/stage5-acceptance.md 的限制和未实施范围，在获得用户对下一阶段的明确授权后再制定计划；不得把阶段5后端接口等同于阶段6业务前端完成。
~~~

## 19. 官方兼容性参考

- [Spring Boot 官方文档](https://docs.spring.io/spring-boot/)
- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [MyBatis Spring Boot Starter 官方项目](https://github.com/mybatis/spring-boot-starter)
- [MyBatis Spring Boot Starter 文档](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
- [Spring Data Neo4j Reference](https://docs.spring.io/spring-data/neo4j/reference/)
- [Spring Data Neo4j Neo4jClient](https://docs.spring.io/spring-data/neo4j/reference/appendix/neo4j-client.html)
- [Neo4j Supported Versions](https://neo4j.com/developer/kb/neo4j-supported-versions/)
- [Neo4j 5 Cypher约束语法](https://neo4j.com/docs/cypher-manual/5/constraints/syntax/)
- [Neo4j Cypher MERGE](https://neo4j.com/docs/cypher-manual/current/clauses/merge/)
- [Neo4j Java Driver事务](https://neo4j.com/docs/java-manual/current/transactions/)
- [OpenAlex API Overview](https://help.openalex.org/api/)
- [OpenAlex Paging](https://help.openalex.org/api/paging/)
- [OpenAlex Filtering](https://help.openalex.org/api/filtering/)
- [OpenAlex Sync](https://help.openalex.org/access/sync/)
- [OpenAlex Errors and Rate Limits](https://help.openalex.org/api/errors/)
- [OpenAlex Work Attributes](https://help.openalex.org/data/works/attributes/)
- [Crossref REST API](https://www.crossref.org/documentation/retrieve-metadata/rest-api/)
- [Crossref Access and Authentication](https://www.crossref.org/documentation/retrieve-metadata/rest-api/access-and-authentication/)
- [Crossref REST API Tips](https://www.crossref.org/documentation/retrieve-metadata/rest-api/tips-for-using-the-crossref-rest-api/)
- [Crossref REST API Filters](https://www.crossref.org/documentation/retrieve-metadata/rest-api/rest-api-filters/)
- [Crossref 2026 Cursor Changes](https://community.crossref.org/t/changes-to-cursors-filtering-and-sorting-in-the-rest-api/16246)
