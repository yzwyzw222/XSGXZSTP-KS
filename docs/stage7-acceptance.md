# 阶段 7 验收记录

记录日期：2026-09-03

验收范围：开发计划批次7.0至7.8的滚动验收。阶段7全部批次通过；未进入阶段8，未实施部署、服务器操作或Git历史操作。

## 1. 批次状态

| 批次 | 状态 | 当前结论 |
| --- | --- | --- |
| 7.0 阶段6回归、环境与依赖门禁 | 通过 | 阶段6确定性回归与依赖边界满足进入7.1的条件 |
| 7.1 OpenAPI、权限与错误契约 | 通过 | 阶段7接口、权限、状态和安全错误边界已冻结 |
| 7.2 Cytoscape.js局部图谱 | 通过 | 受限局部图、路径、主动扩展和可访问替代视图通过测试 |
| 7.3 MySQL统计与ECharts | 通过 | MySQL统计口径、六类筛选、ECharts及表格摘要通过测试 |
| 7.4 异步导出任务与文件边界 | 通过 | V10、MySQL导出范围、有界执行器和安全文件边界通过测试 |
| 7.5 导出页面、下载与审计 | 通过 | 当前筛选映射、状态轮询、Blob下载与四类审计通过测试 |
| 7.6 运维总览与系统内告警 | 通过 | V11、健康与运行汇总、三类幂等告警及确认审计通过测试 |
| 7.7 运行监控页面 | 通过 | 健康分组、运行/告警/图/审计分页及受控处置通过测试 |
| 7.8 完整回归与阶段验收 | 通过 | 全量回归、依赖边界、静态配置和文档一致性通过验收 |

## 2. 批次 7.0 验收证据

### 2.1 环境与依赖

- `java -version`：Temurin 21.0.12.1，符合Java 21基线。
- `node --version`：24.14.0；`E:\nodejs\npm.cmd --version`：11.9.0。
- `docker version`：Docker Desktop 4.72.0，Client/Server 29.4.2，Linux Engine可用。
- `docker compose version`：5.1.3。
- 直接依赖清单未包含Pinia、Axios、APOC、GDS或消息队列。`package-lock.json`中的`pinia`文本来自第三方包的可选peer元数据，不是项目直接依赖或运行时用法。
- 当前处于`dev`分支，项目文件整体仍未跟踪；没有执行重置、清理、提交、分支、标签或推送。

### 2.2 后端全量回归

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml verify
~~~

结果：通过。共123项测试，0失败、0错误、0跳过；MySQL 8.0.42与Neo4j 5.26 Testcontainers实际启动；Flyway V1至V9完成空库、阶段2至阶段5升级路径和`validate`；可执行JAR重新打包成功。

首次在Docker Desktop未启动时执行同一命令，101项已发现测试中有13个Testcontainers测试因`Could not find a valid Docker environment`报错，0个断言失败。启动本机Docker Desktop后原样复跑通过，因此该失败归因为测试环境门禁，不是阶段6代码回归。

### 2.3 前端测试与构建

~~~powershell
E:\nodejs\npm.cmd --prefix .\frontend run test
E:\nodejs\npm.cmd --prefix .\frontend run build
E:\nodejs\npm.cmd --prefix .\frontend run test:e2e
~~~

结果：Vitest共5个测试文件、12项测试通过；`vue-tsc -b`和Vite生产构建通过；Microsoft Edge下3个Playwright阶段6流程通过。

三条命令首次在普通受限执行上下文均因`spawn EPERM`无法启动Vite或Playwright子进程；在获准的宿主上下文原样复跑通过，确认属于执行上下文限制。

### 2.4 Compose静态解析

~~~powershell
$env:NEO4J_PASSWORD='aacv-stage7-config-only'
docker compose -f .\deploy\compose.yaml config --quiet
~~~

结果：通过。该值仅为当前命令进程中的非敏感配置解析占位值，未写入项目文件，也未启动或修改Compose服务。

## 3. 批次 7.0 验收结论

批次7.0通过。阶段6现有后端、前端、安全、Flyway V1至V9、MySQL/Neo4j集成和浏览器契约流程没有发现回归；当前依赖边界满足最小依赖要求，可以进入批次7.1。

## 4. 批次 7.1 验收证据

### 4.1 OpenAPI与权限契约

- `docs/openapi.yaml`版本升级为7.0.0，新增统计、导出、运维总览和系统内告警共10个路径，保持`/api/v1`、Cookie会话、CSRF和`application/problem+json`约定。
- 统计固定从MySQL规范数据聚合并返回过滤条件和更新时间；导出固定为`CSV/JSON`、最多10,000条及`PENDING/RUNNING/SUCCEEDED/FAILED/EXPIRED`状态，下载同时校验创建者或管理员及服务端不可预测令牌。
- `ANALYTICS_READ`、`EXPORT_CREATE`、`EXPORT_READ`授予三个现有角色；`OPERATIONS_READ`、`ALERT_MANAGE`只授予管理员。后端枚举、策略、Spring Security路径、前端类型和权限矩阵一致。
- 新增稳定错误码`EXPORT_LIMIT_EXCEEDED`、`EXPORT_CONCURRENCY_LIMIT`、`EXPORT_EXPIRED`，并预留导出创建/成功/失败/下载和告警确认审计动作。
- 系统设计中旧的`POST /api/v1/operations/graph-events/{id}/retry`记录与当前阶段5实现不一致；对照现有控制器、OpenAPI及测试后更正为`/replay`。

### 4.2 定向测试

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=AuthorizationPolicyTests,OpenApiDocumentTests,SecurityIntegrationTests" test
~~~

结果：通过。共18项测试，0失败、0错误、0跳过；覆盖OpenAPI YAML解析和阶段7路径/权限扩展、三个角色权限集合、匿名统计访问、科研用户和数据运营人员访问管理员运维接口、导出创建缺少CSRF及告警确认缺少CSRF。

### 4.3 完整回归

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml verify
E:\nodejs\npm.cmd --prefix .\frontend run test
E:\nodejs\npm.cmd --prefix .\frontend run build
~~~

结果：后端共124项测试通过，0失败、0错误、0跳过，可执行JAR重新打包成功；前端5个测试文件、12项测试通过；`vue-tsc -b`和Vite生产构建通过。批次7.1没有新增数据库迁移，Flyway仍为V1至V9。

## 5. 批次 7.1 验收结论

批次7.1通过。阶段7后续实现必须遵循已冻结的OpenAPI 7.0.0、权限矩阵、对象级导出边界和安全摘要约束；未新增任何依赖，满足进入批次7.2的条件。

## 6. 最终验收边界

- 阶段7新增接口和权限已经冻结，统计、异步导出、目录下载、导出审计、运维总览和系统内告警后端已实现。
- 已增加Cytoscape.js 3.34.2与ECharts 6.1.0，没有增加其他直接依赖。
- 阶段7功能批次和7.8最终完整回归均已通过。
- 未使用真实本地账号和持久化开发库执行联合验收。
- 未实施阶段8容量性能、备份恢复、部署或服务器操作。

## 7. 批次 7.2 验收证据

### 7.1 实现与依赖边界

- 新增`/graph`权限路由、业务导航和工作台入口；科研用户、数据运营人员和管理员继续通过既有`GRAPH_READ`访问。
- 页面复用既有局部子图、最短路径和图同步状态接口；没有修改后端图查询、Neo4j投影或数据库迁移。
- 中心节点、深度、节点数、年份、节点类型、关系类型和成果类型过滤均由受控字段传递；页面不自动请求大范围图数据。
- 主动扩展按稳定节点/关系ID合并去重，累计达到300节点时停止；未保留节点对应的悬空关系不会进入渲染数据。
- 图例、节点/关系文字和同源表格替代视图共同表达语义；详情侧栏仅显示后端白名单属性，并为成果节点提供受控业务路由。
- `npm view cytoscape version license engines dist.unpackedSize --json`确认Cytoscape.js 3.34.2为MIT许可证；只新增该运行时依赖，`npm install`审计0个漏洞。

### 7.2 自动化验证

~~~powershell
E:\nodejs\npm.cmd --prefix .\frontend test
E:\nodejs\npm.cmd --prefix .\frontend run build
E:\nodejs\npm.cmd --prefix .\frontend run test:e2e
~~~

结果：Vitest共6个测试文件、15项测试通过；`vue-tsc -b`与Vite生产构建通过，共转换1627个模块；Microsoft Edge下4项Playwright流程通过。新增图谱端到端流程实际验证科研用户进入图谱页、中心子图请求、服务端截断提示、Trace展示、成果详情跳转和节点/关系表格切换。

## 8. 批次 7.2 验收结论

批次7.2通过。局部图交互、累计300节点边界、同步提示、属性检查与可访问替代视图满足当前退出门禁；除Cytoscape.js外未增加依赖，没有引入Pinia、Axios、新UI框架、APOC、GDS或消息队列，可以进入批次7.3。

## 9. 批次 7.3 验收证据

### 9.1 后端统计口径

- 独立`analytics`模块使用MyBatis读取MySQL规范数据，不依赖Neo4j，不新增数据库迁移。
- 聚合排除已并入规范实体的成果成员，并沿用成果类型和发表日期人工覆盖；来源、作者和机构关系按规范成果ID归并。
- 年份范围、成果类型、来源类型、机构ID和主题ID均为显式受控参数；非法年份范围、非正实体ID和超长成果类型会明确拒绝。
- 机构、主题固定Top 20；作者和机构合作按共同规范成果即时推导，请求上限为1至100，不物化合作边。
- 未知成果类型使用独立的`UNKNOWN/未知`类别，符合未知值不能静默归入其他类别的需求。

### 9.2 后端自动化验证

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=AnalyticsQueryTests,AnalyticsServiceTests,AnalyticsPersistenceIntegrationTests" test
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=AuthorizationPolicyTests,OpenApiDocumentTests,SecurityIntegrationTests" test
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=SecurityIntegrationTests" test
~~~

结果：统计定向测试6项通过，其中真实MySQL 8.0.42容器验证规范去重、全部六类过滤、四类分布和两类合作；权限、安全与OpenAPI回归18项通过；补充科研用户通过真实HTTP边界读取空统计范围后，安全集成13项再次通过。Flyway仍为V1至V9。

### 9.3 前端与依赖验证

~~~powershell
E:\nodejs\npm.cmd view echarts version license engines dist.unpackedSize --json
E:\nodejs\npm.cmd --prefix .\frontend test
E:\nodejs\npm.cmd --prefix .\frontend run build
E:\nodejs\npm.cmd --prefix .\frontend run test:e2e
~~~

结果：ECharts 6.1.0为Apache-2.0许可证，安装后npm审计0个漏洞；前端7个测试文件、16项Vitest通过；`vue-tsc -b`与Vite生产构建通过；Microsoft Edge下5项Playwright流程通过。统计页验证MySQL口径、总览、五个语义化图表、同源表格、合作排行和筛选请求。Vite对统计页约547 kB、gzip约185 kB的独立懒加载块给出超过500 kB的非阻断提示；未通过调整告警阈值掩盖该提示。

## 10. 批次 7.3 验收结论

批次7.3通过。`FR-ANALYTICS-001`至`FR-ANALYTICS-005`的阶段内实现已有MySQL、HTTP、前端和浏览器证据；本批次只新增ECharts，没有引入Pinia、Axios、新UI框架、APOC、GDS或消息队列，可以进入批次7.4。

## 11. 批次 7.4 验收证据

### 11.1 V10、任务状态与数据范围

- Flyway V10新增`export_task`，固定`CSV/JSON`格式、`PENDING/RUNNING/SUCCEEDED/FAILED/EXPIRED`状态、10,000条计数上限、32至128字符令牌和成功文件元数据一致性约束；既有V1至V9保持不变。
- 导出范围只读取MySQL规范成果根记录，排除已合并成员；人工标题、类型、语言、发表日期和载体覆盖优先，并支持标题、作者ID、机构ID、年份范围、成果类型、来源类型、载体ID和主题ID组合过滤。
- 创建任务前锁定当前请求者，限制每用户2个活动任务，并按固定并发2加队列20限制单实例活动总量；任务提交只在创建事务提交后发生。
- 处理器以条件更新原子认领`PENDING`任务，不重复执行；应用启动时将遗留`RUNNING`任务标记为安全失败，只按当前有界容量重新提交待执行任务，不无限重试。

### 11.2 文件、下载与错误边界

- CSV使用UTF-8 BOM和全字段引号，对去除前导空白后以`= + - @ Tab CR`开头的值添加单引号，保留双引号转义；JSON复用项目既有Jackson，不增加序列化依赖。
- 导出根目录通过`AACV_EXPORT_ROOT`配置，文件名由服务端任务UUID生成；写入同目录临时文件后原子替换，所有解析路径必须规范化且位于固定根目录内，客户端请求和响应均不包含服务器路径。
- 下载要求`EXPORT_READ`、创建者或管理员对象级权限和服务端生成的256位随机URL安全令牌；令牌使用常量时间比较，仅在任务成功且未过期时通过状态响应返回。
- 超量、执行容量和过期分别使用`EXPORT_LIMIT_EXCEEDED`、`EXPORT_CONCURRENCY_LIMIT`和`EXPORT_EXPIRED`；后台生成失败只保存有限安全摘要，不向API返回异常、SQL或文件路径。

### 11.3 自动化验证

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=ExportFilterTests,LocalExportFileStoreTests,ExportServiceTests,ExportTaskProcessorTests,FlywayMigrationTests" test
.\mvnw.cmd -f .\backend\pom.xml -Dtest=ExportPersistenceIntegrationTests test
.\mvnw.cmd -f .\backend\pom.xml -Dtest=SecurityIntegrationTests test
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=*Export*Tests,FlywayMigrationTests,OpenApiDocumentTests" test
~~~

结果：首次未引用包含逗号的`-Dtest`参数时PowerShell在命令解析阶段报`Missing argument in parameter list`，未进入Maven；加双引号后原样测试范围通过。第一组15项、MySQL持久化集成1项、HTTP安全13项及最终导出/迁移/OpenAPI组合19项均为0失败、0错误、0跳过。MySQL 8.0.42实际验证V1至V10空库迁移、V3/V7/V8升级到V10、规范成果过滤、人工覆盖、任务状态与JSON筛选条件往返；HTTP测试验证导出创建CSRF、科研用户成功创建/读取及其他非管理员对象越权拒绝。

## 12. 批次 7.4 验收结论

批次7.4通过。V10异步导出元数据、MySQL规范范围、10,000条上限、本进程有界并发、重启恢复、安全文件生成、过期和对象级下载边界已有自动化证据；未新增依赖，没有引入Pinia、Axios、新UI框架、APOC、GDS或消息队列。导出页面、成功/失败/下载审计与浏览器下载流程仍属于7.5，可以进入批次7.5。

## 13. 批次 7.5 验收证据

### 13.1 当前筛选、轮询与下载

- 成果目录在已有权限路由中增加CSV/JSON操作，复用当前八类筛选，不新建独立状态库或导出页面。
- 题名、单一年份、成果类型和来源直接映射冻结契约；来源只接受`OPENALEX/CROSSREF`。作者、机构、期刊和主题文本通过既有目录接口查询，只有唯一结果才转换为规范实体ID；零匹配或多匹配会阻止创建并给出明确提示。
- 创建任务后按800毫秒串行轮询，进入`SUCCEEDED/FAILED/EXPIRED`即停止；页面展示任务ID、格式、状态、预计/实际数量、创建与过期时间及后端有限失败摘要。
- 下载使用现有原生`fetch`边界读取Blob和浏览器对象URL；令牌仅作为冻结下载接口的请求参数保存在当前页面内存，不展示、不持久化。

### 13.2 审计与恢复一致性

- `EXPORT_CREATED`与任务创建处于同一事务；成功、失败通过独立事务终结器按原请求者归属记录，`EXPORT_DOWNLOADED`只在对象权限、有效期、令牌和文件边界全部通过后记录。
- 审计摘要只包含格式、导出数量或稳定错误码，不包含筛选正文、下载令牌、文件路径、SQL或异常详情。
- 失败审计只在`PENDING/RUNNING → FAILED`条件更新真实生效时写入；并发或重复终结已终态任务不会制造伪失败记录。
- 应用启动时处理全部遗留`RUNNING`任务，`PENDING`任务重新提交仍以固定并发与队列总容量为上限，不引入无限重试。

### 13.3 自动化验证

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=*Export*Tests,SecurityIntegrationTests" test
npm test -- --run
npm run build
npx playwright test e2e/stage7-export.spec.ts
~~~

结果：后端共30项测试通过，0失败、0错误、0跳过，其中MySQL 8.0.42实际执行V1至V10并验证导出持久化，安全集成13项覆盖导出CSRF和对象权限；导出单元测试覆盖四类审计调用、终态不误记失败和全量恢复遗留运行任务。前端8个测试文件、21项Vitest通过，`vue-tsc -b`与Vite生产构建通过，Edge下1项新增导出流程通过并验证当前筛选请求、CSRF、轮询终态和浏览器下载。普通受限上下文运行Vitest首次因`spawn EPERM`失败，在获准宿主上下文原样复跑通过；属于执行上下文限制。生产构建继续报告既有统计页约547 kB的非阻断分块提示。

## 14. 批次 7.5 验收结论

批次7.5通过。目录筛选到冻结导出条件的保守映射、异步状态反馈、原生浏览器下载以及创建/成功/失败/下载审计闭环已有自动化证据；没有新增依赖，没有引入Pinia、Axios、新UI框架、APOC、GDS或消息队列。阶段7仍未完成，满足进入批次7.6的条件。

## 15. 批次 7.6 验收证据

### 15.1 V11、运维总览与告警规则

- Flyway V11新增`alert_event`，包含固定类型/级别/状态、主体、有限JSON证据、检测信号时间、首末检测时间、出现次数、确认信息和版本；生成列唯一索引限制同一去重键最多一个`OPEN`事件。
- 运维总览按管理员权限汇总应用存活、MySQL、Neo4j、活动采集、近24小时失败、图同步待处理/处理中/死信及未确认告警，不返回日志正文、SQL、Cypher、凭据或内部路径。
- Quartz默认每60秒串行评估：数据源连续失败3次，最近完成且读取至少20条的运行解析成功率低于80%，以及图同步最老待处理超过300秒或存在死信；达到两倍失败阈值、低于一半成功率阈值或存在死信时升级为`CRITICAL`。
- 同一类型与主体的相同或更旧信号不重复更新；管理员确认后，只有晚于确认时间的新信号才能重新产生未确认事件。确认使用乐观锁版本、非空原因、`ALERT_MANAGE`和CSRF，并记录`ALERT_ACKNOWLEDGED`安全审计。
- 7.7接口盘点发现领域对象的内部`detectedSignalAt`会被控制器额外序列化；已改为独立响应DTO并补充HTTP不存在断言，OpenAPI冻结契约未变，定向安全与契约测试2项复跑通过。
- 本批次复用MyBatis、Quartz、Spring Security、Actuator和既有审计服务，没有新增依赖或外部通知通道。

### 15.2 自动化验证

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=OperationsPersistenceIntegrationTests" test
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=SecurityIntegrationTests#administratorCanReadOperationsAcknowledgeAlertAndProduceAudit" test
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=AlertEvaluationQuartzScheduleTests" test
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=AlertServiceTests,AlertEvaluationServiceTests,OperationsServiceTests,AlertEvaluationQuartzScheduleTests,OperationsPersistenceIntegrationTests,FlywayMigrationTests,SecurityIntegrationTests,OpenApiDocumentTests" test
~~~

结果：持久化集成1项、管理员HTTP闭环1项和Quartz注册2项分别通过；最终组合共30项通过，0失败、0错误、0跳过。MySQL 8.0.42实际验证V1至V11空库迁移、V3/V7/V8升级到V11、运行信号查询、开放告警幂等写入、分页、确认和版本冲突；HTTP验证应用/MySQL健康摘要、管理员读取、CSRF确认和审计。首次在受限上下文执行HTTP Testcontainers时因无法访问`\\.\pipe\docker_engine`失败，获准在宿主上下文原样复跑后通过，属于执行环境限制。持久化测试开发中还发现并修复MySQL保留字别名和测试连接时区问题，最终实际容器回归通过。

## 16. 批次 7.6 验收结论

批次7.6通过。`FR-OPS-005`和`FR-OPS-006`的后端范围已具备配置校验、定时评估、去重、确认、权限、审计、迁移和真实MySQL证据；没有新增依赖，没有引入外部告警平台、Pinia、Axios、新UI框架、APOC、GDS或消息队列。运行监控页面仍属于7.7，阶段7尚未完成，满足进入批次7.7的条件。

## 17. 批次 7.7 验收证据

### 17.1 页面、降级与操作边界

- 新增`/operations`懒加载权限路由、侧栏导航和工作台入口，只有`OPERATIONS_READ`用户可进入。
- 页面分别读取Actuator liveness、readiness和graph健康组。HTTP 503携带合法`DOWN`状态时保留依赖故障语义；任一分区请求失败不会清空其他成功数据。
- 首屏展示活动采集、近24小时未解决失败、图待处理/处理中/死信及未确认告警计数；采集失败通过已有任务页定位，不新增冻结契约之外的跨运行失败接口。
- 标签页分页展示告警、图同步事件、维护运行和审计。告警确认、死信重放、初始回填、对账和全量重建继续使用既有权限与CSRF；全量重建必须输入`REBUILD_AACV_MANAGED_GRAPH`。
- Neo4j降级状态与MySQL就绪状态分离，页面继续显示MySQL侧计数、告警与审计，并明确说明目录和统计不受图状态控制。

### 17.2 自动化验证

~~~powershell
E:\nodejs\npm.cmd --prefix .\frontend test -- --run
E:\nodejs\npm.cmd --prefix .\frontend run build
E:\nodejs\npx.cmd playwright test e2e/stage7-operations.spec.ts
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=AuthorizationPolicyTests,OpenApiDocumentTests" test
~~~

结果：前端8个测试文件、24项Vitest通过；`vue-tsc -b`与Vite生产构建通过，运行监控页为约30 kB、gzip约11 kB的独立懒加载块；Edge下新增运行监控流程1项通过，实际验证Neo4j 503/`DOWN`降级、应用/MySQL正常、失败聚合、告警确认请求体、CSRF、死信重放、维护入口和审计记录。后端权限与OpenAPI 5项回归通过。普通受限上下文首次运行Vitest和Vite均因`spawn EPERM`失败，获准在宿主上下文原样复跑通过，属于执行环境限制。生产构建继续报告既有统计页约547 kB的非阻断分块提示。

## 18. 批次 7.7 验收结论

批次7.7通过。管理员运行监控、部分失败隔离、Neo4j降级、MySQL功能保留、分页定位和既有运维处置入口具备前端、浏览器与契约证据；未新增依赖或后端接口，没有引入Pinia、Axios、新UI框架、APOC、GDS或消息队列。本结论随后由7.8完整回归复核。

## 19. 批次 7.8 验收证据

### 19.1 回归发现与最小修复

- 首次后端全量`verify`执行159项，出现1个失败和2个上下文错误：应用启动测试仍断言V9迁移，两个持久化上下文在原始Payload清理计划注册时遇到Quartz JDBC表死锁。
- 对照V10/V11迁移与启动日志后，将启动断言更新为11；原始Payload清理计划改为最高优先级`ApplicationRunner`，在即时图Outbox Runner之前注册固定Job和Trigger，未改变任务周期、身份或业务行为。
- 修复后，原3个失败用例、Flyway迁移和告警调度共9项定向回归通过；后端159项全量回归随后通过。
- Playwright跨文件实际启动5个worker并共享一个Vite开发服务器，阶段7大型懒加载模块首次转换时出现默认5秒断言超时，失败快照显示页面最终正常。配置固定为单worker后恢复默认断言超时，全量7项稳定通过；该套件不作为并发性能证据。

### 19.2 最终自动化与静态验证

~~~powershell
.\mvnw.cmd -f .\backend\pom.xml "-Dtest=AacvSystemApplicationTests,AnalyticsPersistenceIntegrationTests,SourceCrawlPersistenceTests,AlertEvaluationQuartzScheduleTests,FlywayMigrationTests" test
.\mvnw.cmd -f .\backend\pom.xml verify
E:\nodejs\npm.cmd --prefix .\frontend test -- --run
E:\nodejs\npm.cmd --prefix .\frontend run build
E:\nodejs\npx.cmd playwright test
$env:NEO4J_PASSWORD='stage7-compose-validation-only'
docker compose -f .\deploy\compose.yaml config --quiet
E:\nodejs\npm.cmd --prefix .\frontend ls --depth=0
~~~

结果：定向后端9项和全量后端159项均为0失败、0错误、0跳过，可执行JAR重打包成功；前端8个测试文件、24项Vitest通过；`vue-tsc -b`与Vite生产构建通过；Microsoft Edge以单worker运行7项关键流程全部通过；Compose静态解析退出码0，未启动或修改服务。生产构建继续给出统计页约547 kB、gzip约185 kB的既有非阻断分块提示。

直接依赖仅在既有Vue/Element Plus工具链上增加本阶段批准的Cytoscape.js 3.34.2和ECharts 6.1.0。`package-lock.json`中的Pinia字符串只是`vue-router`的可选peer元数据，`npm ls --depth=0`确认未安装Pinia；代码和清单未引入Axios、新UI框架、APOC、GDS、Redis、搜索引擎或消息队列。Flyway迁移为连续V1至V11；OpenAPI 7.0.0和权限矩阵由全量测试验证，无需修改冻结内容。

## 20. 阶段 7 验收结论

批次7.8及阶段7通过。局部图谱与可访问替代视图、MySQL统计、受控异步导出、系统内告警和管理员运行监控均具备后端、前端、真实MySQL/Neo4j容器、浏览器与文档证据。阶段7未执行容量性能、备份恢复、真实本地账号/持久化开发库联合运行、部署、服务器或Git历史操作；这些事项仍属于阶段8或后续明确授权范围。
