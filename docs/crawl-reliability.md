# 采集可靠性修复与数据源名称（2026-09-10）

当前采集实现增加了可持久化的增量窗口、完整的计划管理和运行恢复入口，修正了来源过滤与重复入库行为。任务、首页任务卡片、质量与治理页面使用 OpenAlex、Crossref 名称，用户无需记忆来源编号；内部 sourceId 外键和旧接口保持兼容。

## 实际行为

| 原问题 | 修复后的行为 |
| --- | --- |
| 每日固定范围复查无法推进 | 保留旧计划模式，可显式选择增量模式；实际查询窗口按运行保存，成功后推进，超限时下次缩小，失败时保留。 |
| Crossref 多值过滤错误，部分筛选被忽略 | 同类值重复过滤名，如 `doi:a,doi:b`；创建、更新、执行均校验来源能力，拒绝 Crossref 的 OpenAlex 作者/机构标识。 |
| PENDING 在重启后悬挂 | 启动恢复查询包含等待运行；恢复/续跑提交后尚未创建新 Batch 的运行也可重新派发。 |
| 整页错误没有可用明细 | FETCH、PARSE、VALIDATE、PERSIST、SYSTEM 分类保存固定安全建议；前端在状态变化时刷新明细，执行失败提供“从检查点重试”。 |
| total-results 变化中断游标 | 保留非法值校验，数量变化只记录日志，以游标和采集上限判断结束。 |
| 缺少历史和计划读取、版本靠手工填写 | 任务运行历史分页；计划每次打开从服务器读取，自动携带版本；支持启用、停用、移除。 |
| 重复记录也重建关联、重复触发图同步 | 比较规范内容（忽略观测时间）及解析器版本；不变时刷新观测时间并保留成果修改时间、关系和人工决策，解析器变化仍重新构建。 |

## 窗口与恢复边界

- 每次运行仍最多 5 页、500 条；每次手动或定时触发最多处理一个 7 天窗口。
- Crossref 的 `CLOSED_INDEX_DATE_WINDOW` 从任务 `updatedFrom` 起步，以当前 UTC 时间前 5 分钟为截止，索引时间边界保留重叠，以幂等入库处理边界记录。
- OpenAlex 的 `ROLLING_PUBLICATION_DATE_WINDOW` 从 `publicationDateFrom` 起步，按 UTC 完整出版日处理，不包含当天。出版日期推进不能自动捕获过去日期的迟到或修订记录，需另建固定范围复查任务。
- 增量模式下对应的原始结束日期不再作为最终停止点，结束日期由实际窗口计算；其他筛选条件保留。固定模式继续完整使用原始起止范围。
- 只有窗口耗尽且没有记录失败才推进。页数或记录上限触发时，下次将窗口减半；最小窗口为 Crossref 1 秒、OpenAlex 1 天。最小窗口仍超限会明确拒绝推进，需按关键词、作者、机构等进一步分组建任务。
- 取消或失败后重新触发会重做原窗口；“从检查点重试”沿用同一运行与最后已提交游标。已有后续增量运行时禁止重试旧窗口，避免回退进度。
- 失败续跑与新触发共用来源锁，并拒绝停用的来源或任务；运行级失败不混入记录失败计数，历史错误明细保留；“重试失败记录”与“从检查点重试”分别处理记录和整页执行错误。存在记录失败的窗口须重采并完整成功后才能推进。
- 停用计划只停止自动触发；手动触发仍使用该计划选择的模式。移除计划保留数据与运行窗口，后续手动触发回到固定范围；重新创建增量计划仍可读取同任务既有窗口进度。
- 当前恢复继续以单实例部署为前提。没有引入消息队列或多实例协调，不宣称完整采集全库。
- 重启后若来源拒绝旧游标，可重新触发任务，从该窗口起点重采；原已提交数据保留。

## 接口与迁移

接口均沿用已有 Session、CSRF 和业务权限。

| 接口 | 行为 |
| --- | --- |
| `GET /api/v1/crawl/tasks/{taskId}/schedule` | 读取计划及版本，无计划返回空体；启用计划返回按当前时间推算的下次执行时间。 |
| `PUT /api/v1/crawl/tasks/{taskId}/schedule` | 继续接受 localTime（HH:mm）、timeZone、version；新增可选 incrementalMode、enabled，省略时为固定范围、启用。 |
| `DELETE /api/v1/crawl/tasks/{taskId}/schedule?version=...` | 按版本移除计划，返回 204。 |
| `GET /api/v1/crawl/tasks/{taskId}/runs?page=0&size=20` | 按运行 ID 降序读取历史，最大页大小 100。 |
| `GET /api/v1/crawl/runs/{runId}/window` | 读取该次运行的实际窗口与范围，固定范围运行返回空体。 |
| `POST /api/v1/crawl/runs/{runId}/retry-run` | 对 FAILED/BATCH_FAILED 运行从已提交检查点续跑，返回 202。 |

新增 Flyway V16 的 `crawl_run_window` 表保存 run_id、模式、起止时间和参数 JSON，既有迁移未被修改。旧计划继续为 FIXED_SCOPE_REFRESH。首次启动新版后端时执行迁移；本轮仅在隔离测试库执行过迁移，没有连接或修改当前业务库。

## 验证记录

命令均从相应目录执行。测试失败没有被跳过或删除，相关修复及最终结果如下。

前端目录：`F:/Program/Java/course_design/systems/crawler/frontend`

- `npm.cmd run test -- --run src/views/CrawlTasksView.test.ts src/services/business.test.ts`：最终 8 项通过，覆盖名称选源、条件切换、版本读取、历史、等待取消和整页错误刷新。
- `npm.cmd run test`：36 个文件、172 项通过。
- `npm.cmd run build`：通过类型检查与构建；后续按当前集成路径执行 `npm.cmd run build -- --base=/crawler/`，通过。
- 首次构建发现缺失的 DELETE 封装调用、测试组件类型错误，已分别改用既有 apiRequest 和具备类型定义的测试组件；首次页面测试使用了与实际按钮不符的定位文本，已修正测试定位并重跑。
- Vite 在沙箱内曾报 spawn EPERM，正常本机权限重试通过。构建仍提示既有图谱/图表分包超过 500 kB，没有扩大本次范围修改分包。

后端目录：`F:/Program/Java/course_design/systems/crawler/backend`

1. `..\mvnw.cmd -o -B '-DskipTests' compile`：通过。
2. `..\mvnw.cmd -o -B '-Dtest=CrawlTaskServiceTests,CrawlRecoveryServiceTests,CrawlWindowServiceTests,CrawlRunServiceTests,OpenAlexPageItemReaderTests,CrossrefHttpTransportTests,CrossrefDataSourceAdapterTests,SpringBatchCrawlRunLauncherTests' test`：首轮 50 项通过。
3. `..\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/crawler/tmp' '-Dtest=SourceCrawlPersistenceTests,IngestionPipelineIntegrationTests,OpenAlexBatchOrchestrationIntegrationTests,FlywayMigrationTests' test`：正常权限下迁移 5 项、持久化与批次编排通过；重复采集的成果修改时间断言暴露 MySQL 自动更新时间问题，随后修复。沙箱内首次执行因 Docker 管道访问受限未启动测试，正常权限下使用独立 MySQL/Neo4j 容器继续验证。
4. `..\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/crawler/tmp' '-Dtest=SourceCrawlPersistenceTests,IngestionPipelineIntegrationTests,OpenAlexBatchOrchestrationIntegrationTests,CrawlTaskServiceTests,CrawlRecoveryServiceTests,CrawlWindowServiceTests,CrawlRunServiceTests,OpenAlexPageItemReaderTests,CrossrefHttpTransportTests,CrossrefDataSourceAdapterTests,CrawlJobExecutionListenerTests,SpringBatchCrawlRunLauncherTests' test`：55 项中 54 项通过；新增持久化测试使用不存在的 Batch 外键导致 1 项错误，随后补齐测试 Batch 数据。
5. `..\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/crawler/tmp' '-Dtest=SourceCrawlPersistenceTests,IngestionPipelineIntegrationTests' test`：2 项通过，含修正后的外键、重试时间清理及解析器版本变化的重新投影验证。
6. `..\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/crawler/tmp' '-Dtest=CrawlTaskServiceTests,OpenAlexPageItemReaderTests,CrawlRunStateMachineTests,CrossrefFusionIntegrationTests' test`：23 项通过，验证执行前冻结窗口、重启读取窗口、状态机与跨来源融合。

7. `..\mvnw.cmd -o -B '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/crawler/tmp' '-Dtest=CrawlRunServiceTests,OpenAlexBatchOrchestrationIntegrationTests' test`：9 项通过，验证失败续跑与新触发共用来源锁，并实际完成检查点续跑。

按各测试类最终结果合计 66 项后端定向测试通过；未运行全部后端套件。来源响应使用测试夹具，未执行真实外部批量采集或生产部署。运行验证不等于当前仍运行的旧后端已被替换。

仓库根目录另执行 `node scripts/check-source.mjs`：relation、crawler 的原始来源、祖先关系及全部适配哈希通过。首次在沙箱内启动 Git 子进程报 EPERM，正常权限重试通过。`git -c core.safecrlf=false diff --check -- systems/crawler docs/development.md docs/integration-baseline.md docs/crawl-reliability.md` 通过；本轮文件 UTF-8、无 BOM、文档链接和尾随空白检查通过。

## 生效步骤

本轮没有停止、重启或替换正在运行的后端。窗口查询、历史及计划新接口需要新版后端配套。前端已按 /crawler/ 构建。

如当前使用整合启动脚本，先处理正在采集的运行，在仓库根目录依次执行以下操作。停止 crawler 期间统一登录和跨系统身份确认会暂时受影响；已有库由 Flyway 执行新增 V16，不需要重建数据库。

```powershell
.\scripts\Stop-Integration.ps1 -System crawler
.\systems\crawler\mvnw.cmd -o -B -f systems/crawler/backend/pom.xml '-DskipTests' package
.\scripts\Start-Integration.ps1 -System crawler -Mode Demo
```

如果原来使用 Development 模式，启动时继续使用原模式。此处命令为待执行的生效步骤，本轮未执行；启动后刷新页面，在“调度”中选择需要的增量模式。

## 本轮变更文件

只列本轮实际修改或新增的文件；既有门户管理、两系统整合和采集启动诊断改动已保留，没有列为本轮新增成果。

| 文件 | 作用 |
| --- | --- |
| [CrawlScheduleResponse.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/api/CrawlScheduleResponse.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlTaskController.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/api/CrawlTaskController.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [DailyScheduleRequest.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/api/DailyScheduleRequest.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlRecoveryService.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/CrawlRecoveryService.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlRunService.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/CrawlRunService.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlTaskService.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/CrawlTaskService.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlWindowService.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/CrawlWindowService.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/port/CrawlRepository.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlRunStateMachine.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/domain/CrawlRunStateMachine.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlExecutionFailure.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/domain/CrawlExecutionFailure.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlWindow.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/domain/CrawlWindow.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlJobExecutionListener.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/batch/CrawlJobExecutionListener.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [OpenAlexPageItemReader.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/batch/OpenAlexPageItemReader.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/persistence/CrawlMapper.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [MyBatisCrawlRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/persistence/MyBatisCrawlRepository.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [CrawlWindowRow.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/persistence/CrawlWindowRow.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [QuartzCrawlScheduleAdapter.java](../systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/quartz/QuartzCrawlScheduleAdapter.java) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [IngestionMapper.java](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/infrastructure/persistence/IngestionMapper.java) | 相同内容及解析器版本的重复记录仅刷新观测信息。 |
| [MyBatisIngestionRepository.java](../systems/crawler/backend/src/main/java/com/aacv/system/ingestion/infrastructure/persistence/MyBatisIngestionRepository.java) | 相同内容及解析器版本的重复记录仅刷新观测信息。 |
| [CrossrefDataSourceAdapter.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/infrastructure/crossref/CrossrefDataSourceAdapter.java) | 修正多值过滤并拒绝不支持的标识。 |
| [CrossrefHttpTransport.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/infrastructure/crossref/CrossrefHttpTransport.java) | 修正多值过滤并拒绝不支持的标识。 |
| [V16__persist_crawl_windows.sql](../systems/crawler/backend/src/main/resources/db/migration/V16__persist_crawl_windows.sql) | 新增运行窗口快照表，保留既有数据。 |
| [CrawlMapper.xml](../systems/crawler/backend/src/main/resources/mapper/crawl/CrawlMapper.xml) | 运行恢复、错误记录、检查点重试、窗口或计划管理。 |
| [IngestionMapper.xml](../systems/crawler/backend/src/main/resources/mapper/ingestion/IngestionMapper.xml) | 相同内容及解析器版本的重复记录仅刷新观测信息。 |
| [CrawlRunServiceTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/crawl/application/CrawlRunServiceTests.java) | 验证失败续跑先锁定来源再检查同范围冲突。 |
| [CrawlTaskServiceTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/crawl/application/CrawlTaskServiceTests.java) | 新增或补充采集回归验证。 |
| [CrawlRecoveryServiceTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/crawl/application/CrawlRecoveryServiceTests.java) | 新增或补充采集回归验证。 |
| [CrawlWindowServiceTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/crawl/application/CrawlWindowServiceTests.java) | 新增或补充采集回归验证。 |
| [CrawlJobExecutionListenerTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/crawl/infrastructure/batch/CrawlJobExecutionListenerTests.java) | 新增或补充采集回归验证。 |
| [OpenAlexPageItemReaderTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/crawl/infrastructure/batch/OpenAlexPageItemReaderTests.java) | 新增或补充采集回归验证。 |
| [SourceCrawlPersistenceTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/crawl/infrastructure/persistence/SourceCrawlPersistenceTests.java) | 新增或补充采集回归验证。 |
| [FlywayMigrationTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/infrastructure/database/FlywayMigrationTests.java) | 新增或补充采集回归验证。 |
| [IngestionPipelineIntegrationTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/ingestion/infrastructure/persistence/IngestionPipelineIntegrationTests.java) | 新增或补充采集回归验证。 |
| [CrossrefDataSourceAdapterTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/source/infrastructure/crossref/CrossrefDataSourceAdapterTests.java) | 新增或补充采集回归验证。 |
| [CrossrefHttpTransportTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/source/infrastructure/crossref/CrossrefHttpTransportTests.java) | 新增或补充采集回归验证。 |
| [OpenAlexBatchOrchestrationIntegrationTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/source/infrastructure/openalex/OpenAlexBatchOrchestrationIntegrationTests.java) | 新增或补充采集回归验证。 |
| [business.ts](../systems/crawler/frontend/src/services/business.ts) | 扩展计划、历史、运行窗口与续跑接口契约。 |
| [business.test.ts](../systems/crawler/frontend/src/services/business.test.ts) | 新增或补充采集回归验证。 |
| [api.ts](../systems/crawler/frontend/src/types/api.ts) | 扩展计划、历史、运行窗口与续跑接口契约。 |
| [useDataSources.ts](../systems/crawler/frontend/src/composables/useDataSources.ts) | 共享名称解析、来源加载和迟到响应处理。 |
| [CrawlTasksView.vue](../systems/crawler/frontend/src/views/CrawlTasksView.vue) | 名称选源、来源筛选字段、历史、调度管理与失败续跑。 |
| [CrawlTasksView.test.ts](../systems/crawler/frontend/src/views/CrawlTasksView.test.ts) | 新增或补充采集回归验证。 |
| [GovernanceView.vue](../systems/crawler/frontend/src/views/GovernanceView.vue) | 数据源编号展示或筛选改为具体名称。 |
| [QualityView.vue](../systems/crawler/frontend/src/views/QualityView.vue) | 数据源编号展示或筛选改为具体名称。 |
| [OverviewView.vue](../systems/crawler/frontend/src/views/OverviewView.vue) | 数据源编号展示或筛选改为具体名称。 |
| [README.md](../systems/crawler/README.md) | 同步采集能力和 V16 迁移说明。 |
| [development.md](./development.md) | 记录实际行为、验证结果和运行边界。 |
| [integration-baseline.md](./integration-baseline.md) | 记录实际行为、验证结果和运行边界。 |
| [source-adaptations.json](./source-adaptations.json) | 同步本轮来源适配哈希。 |
| [crawl-reliability.md](./crawl-reliability.md) | 记录实际行为、验证结果和运行边界。 |

## 项目记忆

实施前读取 docs/integration-baseline.md、docs/development.md，并核对项目源码、测试、迁移与现有未提交改动。原记忆与实施前代码基本一致；原 README 的固定范围复查及 V15 描述已随本轮功能更新。两份指定记忆、crawler README 和来源适配记录同步到实际代码，没有创建新的独立记忆体系。此文为本次采集变更的接口与验证说明。

## 作者机构名称选择补充

### 使用与接口

OpenAlex 作者、机构现在按名称搜索并多选；作者候选显示最近已知机构，机构候选显示所在地，附可用的成果数量。同名候选各自保留对应标识，不自动选中第一个结果。每项最多选择50个；任务参数保持 authorIds/institutionIds，历史URL形式的标识原样保留。查询或名称回显失败时提示重试，不删除原条件；未完成选择的搜索文字阻止保存。

关键词为可选的成果主题检索条件。OpenAlex 使用 `search` 搜索标题、摘要及可检索全文；Crossref 使用 `query` 检索成果元数据。整串文本经去除首尾空白后交给来源，不自动拆分逗号；留空仅按其他条件采集。不同条件共同限定作品范围，同组作者或机构为或关系。作者和机构为作品层面独立过滤，不保证指向同一条署名。

- `GET /api/v1/sources/{sourceId}/entities/{kind}?query=...`：名称1至200字符，kind仅authors或institutions，最多10项候选。
- `GET /api/v1/sources/{sourceId}/entities/{kind}/resolve?ids=A1,A2`：1至50个标识，规范化并批量回显；不存在的实体不伪造名称，前端保留原值并提示。
- 两者均要求 SOURCE_READ 权限和启用的 OpenAlex 来源，不占用数据库事务执行外部请求；Crossref 不开放本次名称查询。
- 官方 autocomplete 用于搜索，`ids.openalex` 过滤用于回显。认证与限流沿用既有来源配置，门控等待最多2秒、连接最多3秒、响应最多8秒、响应体最多256KB，均不放宽来源原有限制。前端防抖300毫秒，请求预算15秒，取消过期请求并防止迟到响应覆盖；查询不自动重试。
- 超时、限流、配额不足和无效响应返回503及 SOURCE_LOOKUP_UNAVAILABLE；无结果返回空列表。无新增依赖、配置或数据库迁移。

依据：[OpenAlex 名称补全](https://help.openalex.org/api/autocomplete/)、[成果关键词检索](https://help.openalex.org/api/searching/)、[官方作者过滤定义](https://github.com/ourresearch/openalex-docs/blob/main/api-entities/authors/filter-authors.md)。

### 本轮验证

前端目录 `systems/crawler/frontend`：

- `npm.cmd run test -- src/components/business/SourceEntitySelect.test.ts src/views/CrawlTasksView.test.ts src/services/business.test.ts`：最终18项通过，覆盖同名区分、多选标识、旧URL回显、缺失名称保留、未选择阻止提交、过期响应、网络失败与空响应。
- `npm.cmd run test`：37个文件181项通过；随后补充异常空响应测试并调整提示和字段对齐，上述18项定向测试重跑通过。
- `npm.cmd run test:e2e -- e2e/crawl-entity-names.spec.ts`：3项通过，覆盖1366px桌面、390px窄屏和旧任务编辑。截图检查后修正同行控件被长说明拉伸、完成选择后旧提示残留，再次3项通过。接口全部模拟，没有访问业务库。
- `npm.cmd run build -- --base=/crawler/`：最终类型检查与构建通过。首次构建暴露测试包装器类型及不完整任务夹具，已修复后重跑；图谱、图表原有大分块警告仍存在。

后端目录 `systems/crawler/backend`：

```powershell
..\mvnw.cmd -o -B '-Dtest=SourceEntityServiceTests,OpenAlexEntityLookupTests,OpenAlexQuotaTests,OpenAlexAuthenticationTests,OpenAlexDataSourceAdapterTests,OpenAlexPageItemReaderTests,CrawlTaskServiceTests' '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/course_design/.local/integration-runtime/crawler/tmp' test
```

最终7类48项通过，包括权限、参数边界、禁用和不支持来源、查询编码、同名候选、批量回显、限流和超时、响应校验、关键词与日期/作者/机构组合，以及原采集适配器和读取器回归。

首次执行 `..\mvnw.cmd -o -B '-Dtest=SourceEntityServiceTests,OpenAlexEntityLookupTests,OpenAlexQuotaTests,OpenAlexAuthenticationTests,OpenAlexDataSourceAdapterTests' test` 时，新代码的 Jackson 导入与项目3版本不符，已改用 tools.jackson。其后26项中8项因 Windows Java 临时Socket路径报 `Unable to establish loopback connection`，使用项目已有的短临时目录参数重跑通过。没有新增或升级依赖，没有跳过有效测试。

仓库根目录执行 `node scripts/check-source.mjs`，relation、crawler 的来源及适配哈希通过；`git -c core.safecrlf=false diff --check -- systems/crawler docs/development.md docs/integration-baseline.md docs/crawl-reliability.md docs/source-adaptations.json` 通过。25个本轮文件的 UTF-8、无 BOM、尾随空白及 Markdown 本地链接检查通过。

未执行真实外部名称查询、全量后端集成套件或运行中后端的替换；模拟接口验证不能替代本机来源连接可用性验证。已有其他未提交改动完整保留。

### 生效步骤

本轮已构建 `/crawler/` 前端，后端源码需重新打包并启动后提供新接口。以下从仓库根目录执行，会短暂停止采集服务；先确认可中断当前采集，再操作：

```powershell
.\scripts\Stop-Integration.ps1 -System crawler
.\systems\crawler\mvnw.cmd -o -f .\systems\crawler\backend\pom.xml -DskipTests package
.\scripts\Start-Integration.ps1 -System crawler -Mode Demo
```

如果原先使用 Development 模式，最后一条保持 `-Mode Development`。随后刷新采集任务页面。不要使用初始化或重置数据库命令；本次名称功能无需迁移。此前尚未应用的其他迁移仍由项目既有 Flyway 启动流程处理。

### 本轮变更文件

- [docs/crawl-reliability.md](crawl-reliability.md)：本轮说明、验证结果与变更清单。
- [docs/development.md](development.md)：同步名称查询接口、限制和兼容行为。
- [docs/integration-baseline.md](integration-baseline.md)：同步名称查询接口、限制和兼容行为。
- [docs/source-adaptations.json](source-adaptations.json)：仅同步本轮变更的来源适配哈希。
- [systems/crawler/README.md](../systems/crawler/README.md)：用户可见的名称选择及关键词语义。
- [systems/crawler/backend/src/main/java/com/aacv/system/shared/domain/ErrorCode.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/domain/ErrorCode.java)：名称服务不可用错误码。
- [systems/crawler/backend/src/main/java/com/aacv/system/shared/infrastructure/web/ApiExceptionHandler.java](../systems/crawler/backend/src/main/java/com/aacv/system/shared/infrastructure/web/ApiExceptionHandler.java)：以503问题详情返回名称查询错误。
- [systems/crawler/backend/src/main/java/com/aacv/system/source/api/SourceEntityController.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/api/SourceEntityController.java)：新增名称搜索、批量回显 GET 接口。
- [systems/crawler/backend/src/main/java/com/aacv/system/source/application/SourceEntityLookupException.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/application/SourceEntityLookupException.java)：安全的名称服务错误。
- [systems/crawler/backend/src/main/java/com/aacv/system/source/application/SourceEntityService.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/application/SourceEntityService.java)：来源启用与类型检查、权限控制、参数和数量校验。
- [systems/crawler/backend/src/main/java/com/aacv/system/source/application/port/SourceEntityLookup.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/application/port/SourceEntityLookup.java)：名称查询端口，隔离应用层与外部来源。
- [systems/crawler/backend/src/main/java/com/aacv/system/source/domain/SourceEntity.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/domain/SourceEntity.java)：候选数据及作者、机构种类与标识校验。
- [systems/crawler/backend/src/main/java/com/aacv/system/source/infrastructure/openalex/OpenAlexEntityLookup.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/infrastructure/openalex/OpenAlexEntityLookup.java)：解析官方候选与批量实体响应，限制响应大小和交互时限。
- [systems/crawler/backend/src/main/java/com/aacv/system/source/infrastructure/openalex/OpenAlexHttpTransport.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/infrastructure/openalex/OpenAlexHttpTransport.java)：固定官方名称查询地址与编码、批量回显。
- [systems/crawler/backend/src/main/java/com/aacv/system/source/infrastructure/openalex/OpenAlexRequestGate.java](../systems/crawler/backend/src/main/java/com/aacv/system/source/infrastructure/openalex/OpenAlexRequestGate.java)：为交互查询提供有界等待，保留原采集等待行为。
- [systems/crawler/backend/src/test/java/com/aacv/system/source/application/SourceEntityServiceTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/source/application/SourceEntityServiceTests.java)：新增或补充名称查询与选择相关回归测试。
- [systems/crawler/backend/src/test/java/com/aacv/system/source/infrastructure/openalex/OpenAlexEntityLookupTests.java](../systems/crawler/backend/src/test/java/com/aacv/system/source/infrastructure/openalex/OpenAlexEntityLookupTests.java)：新增或补充名称查询与选择相关回归测试。
- [systems/crawler/frontend/e2e/crawl-entity-names.spec.ts](../systems/crawler/frontend/e2e/crawl-entity-names.spec.ts)：新增或补充名称查询与选择相关回归测试。
- [systems/crawler/frontend/src/components/business/SourceEntitySelect.test.ts](../systems/crawler/frontend/src/components/business/SourceEntitySelect.test.ts)：新增或补充名称查询与选择相关回归测试。
- [systems/crawler/frontend/src/components/business/SourceEntitySelect.vue](../systems/crawler/frontend/src/components/business/SourceEntitySelect.vue)：来源名称搜索、同名区分、旧值回显、取消请求与失败重试。
- [systems/crawler/frontend/src/services/business.test.ts](../systems/crawler/frontend/src/services/business.test.ts)：新增或补充名称查询与选择相关回归测试。
- [systems/crawler/frontend/src/services/business.ts](../systems/crawler/frontend/src/services/business.ts)：只读名称搜索及批量回显请求封装。
- [systems/crawler/frontend/src/types/api.ts](../systems/crawler/frontend/src/types/api.ts)：名称候选及实体种类类型。
- [systems/crawler/frontend/src/views/CrawlTasksView.test.ts](../systems/crawler/frontend/src/views/CrawlTasksView.test.ts)：新增或补充名称查询与选择相关回归测试。
- [systems/crawler/frontend/src/views/CrawlTasksView.vue](../systems/crawler/frontend/src/views/CrawlTasksView.vue)：名称多选接入、关键词说明、保存校验与字段对齐。
