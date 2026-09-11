# 采集子系统体验与复杂度改进记录

日期：2026-09-10。范围：`systems/crawler`，以及该子系统的来源校验和项目说明。对应用户对使用体验、过度工程化的评审及随后授权的修复。

## 修复结果

| 原问题 | 当前行为 | 验证依据 |
| --- | --- | --- |
| 默认首页偏向展示，日常工作入口不集中 | `/` 为工作台，提供成果搜索、最近 6 个新建任务和需关注的最近运行；原大屏保留在 `/dashboard` | 工作台桌面、窄屏及权限导航浏览器回归 |
| 图谱样式页面容易被误认为业务实体管理 | 导航改为“节点样式”“关系样式”，新地址为 `/graph/settings/nodes`、`/graph/settings/edges`；旧地址保留别名 | 样式管理和旧路由回归 |
| 采集表单参数多，难区分常用项与高级项 | 关键词、时间范围及名称选择保持直达；高级参数默认折叠；历史复查提供日期预设及范围说明 | 任务、名称选择、历史复查回归 |
| 列表看不到最近运行、下次执行 | 每页批量读取两个摘要接口，显示运行编号、状态及下次执行时间；错误与尚无运行分别呈现 | 批量 SQL、参数边界、方法权限测试 |
| 数字 ID 和 UUID 混用 | 用户入口统一使用运行数字编号；UUID 放入可展开的技术标识；工作台可直达运行详情 | 任务触发、历史、工作台链接回归 |
| 采集上限被当成总量，百分比容易误导 | 使用实际读取、解析、新增、更新、重复、失败及请求计数；明确来源耗尽、限额、暂停和取消等原因 | 上限、额度等待和状态测试 |
| 返回详情或刷新后丢失筛选、分页 | 目录条件写入 URL，预览与详情保留返回参数；刷新恢复同一查询 | 同名作者、详情返回、分页及刷新浏览器回归 |
| 模糊检索与导出条件不同，同名实体可能选错 | 候选保留选中 ID，自由文本继续模糊匹配；导出使用已经显示的结果条件；后端列表和导出复用筛选 SQL | 实际 MyBatis XML 生成的 SQL、旧 JSON 兼容及导出浏览器回归 |
| 图谱本地筛选也重复请求接口 | 关键词、节点和关系类型直接过滤已读取图；保留当前缩放、拖拽位置；中心切换和刷新继续发请求 | store 请求次数与真实画布交互测试 |
| 两套图谱引擎重复维护 | 概览、高级查询、路径分析统一使用 Cytoscape；保留单击、双击、右键、缩放、拖动、详情及减少动画偏好 | 共享画布单元测试、生产构建像素和交互回归 |
| 数据源同时使用两套表单校验 | 统一为 Element Plus 表单规则，保留字段错误、焦点、服务端冲突和重复提交保护 | 数据源桌面/窄屏校验及提交回归 |
| 大型采集页面和闲置代码增加维护成本 | 拆出任务、计划、运行三个弹窗；移除不可达运维页及无调用服务/工具；移除四项直接依赖 | 类型检查、完整前端单元测试、既有路由回归及依赖引用搜索 |

最后审查同时修复了两项交互边界：高级图谱查询替换全部节点时重新布局；暂停/取消操作作废先前轮询，防止迟到响应覆盖新状态。两者均有直接回归测试。

保留已有深蓝主题、采集检查点、调度、权限、后端运维与数据治理能力。没有新增框架、数据库迁移或部署组件。

## 复杂度变化与接口兼容

- 移除直接依赖 `vis-network`、`vis-data`、`vee-validate`、`@vee-validate/zod`，同步锁文件；`zod` 继续用于实际存在的响应校验。
- 以本次开始时的 `HEAD` 比较 `frontend/src` 中有变化的 `.vue` / `.ts` 文件，排除 `.test.ts`，计入新增与删除：5,325 行变为 4,537 行，净减少 788 行。此数包含空行和样式，不是性能指标。
- 删除 `OperationsView.vue`、`services/health.ts`、`stores/preferences.ts`、`utils/motion.ts` 及仅针对已删除模块的测试。旧画布测试迁移到共享画布与 `e2e/graph-overview.spec.ts`，保留真实绘制与交互覆盖。
- 新增只读接口 `/api/v1/crawl/tasks/latest-runs?taskIds=...` 和 `/api/v1/crawl/tasks/schedules?taskIds=...`，一次接收 1–100 个正整数 ID，分别沿用 `CRAWL_RUN_READ`、`CRAWL_SCHEDULE_MANAGE`。不为每一行发起独立摘要请求。
- 目录新增可选 `authorId`、`organizationId`、`venueId`、`topicId`。同一维度同时提供文本和 ID 时，以 ID 为准。
- 导出新增可选 `author`、`organization`、`venue`、`topic`、`sourceCode`。原 ID、`sourceType`、年份区间、旧构造函数和旧 JSON 仍兼容。
- 集成模式的工作台地址为 `/crawler/`，原展示页为 `/crawler/dashboard`；门户和账号管理边界保持。
- 来源校验支持显式 `adaptedBlob: null` 删除记录，仍拒绝未记录删除、篡改、重复及过期记录；不跳过历史来源检查。

## 验证结果

下列结果均为实际执行，前端浏览器业务接口使用合成响应，不等于真实数据库或外部来源验收。

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 前端最终单元测试 | `npm.cmd test -- --reporter=dot` | 36 个文件，193 项通过 |
| 前端最终生产构建 | `npm.cmd run build` | `vue-tsc` 与 Vite 构建通过；保留 ChartFrame 639.43 kB 的体积提示 |
| 完整生产构建浏览器回归 | `$env:AACV_E2E_PREVIEW = '1'; node.exe node_modules/@playwright/test/cli.js test --max-failures=5` | Edge，123 项通过，无重试 |
| 最后控制轮询修复后的定向浏览器回归 | `$env:AACV_E2E_PREVIEW = '1'; node.exe node_modules/@playwright/test/cli.js test e2e/optimization-crawl.spec.ts e2e/crawl-entity-names.spec.ts e2e/ux-workflows.spec.ts` | 重新构建后，10 项通过 |
| 后端定向测试 | `.\mvnw.cmd -f backend/pom.xml '-Dtest=CatalogExportFilterSqlTests,ExportFilterTests,CrawlTaskServiceTests,CrawlActivitySecurityTests' test -q` | 22 项通过：SQL 4、导出筛选 3、采集任务 13、方法权限 2 |
| 来源校验辅助测试 | `node.exe --test --test-isolation=none scripts/lib/source-adaptations.test.mjs` | 2 项通过 |
| 差异检查 | `git diff --check` | 通过，仅有 Windows 换行转换提示 |
| 整仓来源检查 | `node.exe scripts/check-source.mjs` | relation、crawler 的来源树、全部文件与适配哈希通过；随后因历史 scholar 对象缺失退出 1，整条命令未通过 |
| 历史对象核对 | `git cat-file -e 'f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d^{tree}'` | 对象不存在；`docs/import-records.json` 无本轮改动 |
| Docker 可用性 | `docker info --format '{{.ServerVersion}}'` | `dockerDesktopLinuxEngine` 管道不存在，无法执行容器数据库集成验收 |

工作目录：前端命令在 `systems/crawler/frontend`，Maven 命令在 `systems/crawler`，来源和差异命令在仓库根目录。

完整浏览器回归之后只补充了采集控制与轮询的竞争修复；随后重新运行全部 193 项单元测试、生产构建和上述 10 项受影响链路。未把这 10 项重复计为新的独立覆盖数量。

过程中发现并修正了旧测试对导航数量、Element Plus 错误提示退场动画的等待方式，以及统计页面板数量的断言。统计页现有四个面板改为逐一核对标题；未更改统计页功能或关闭错误检查。

本机证据位于前端忽略目录 `node_modules/.tmp/ux-unit-complete.log`、`ux-build-complete.log`、`ux-preview-complete.log`、`ux-preview-crawl-final.log`，后端结果位于 `backend/target/surefire-reports`。工作台桌面和窄屏截图位于前端 `test-results/workbench-1440.png`、`workbench-390.png`；这些临时验证产物没有加入变更清单。

## 项目记忆与保护范围

实施前读取 `docs/integration-baseline.md`、`docs/development.md`，并对照当前 README、来源记录和源码。两份指定记忆现已同步工作台入口、筛选/导出语义、批量摘要接口、共享画布、依赖移除及验证边界；历史 vis-network 空白问题记录明确保留为历史背景。

`docs/source-adaptations.json` 仅更新 crawler 内容，保留原记录顺序与 relation 记录；原始来源 SHA 未改变。历史 scholar 对象缺失为本机现有来源材料问题，未通过改写来源 SHA、忽略检查或修改其他系统规避。

本轮开始前根目录 `11` 已被删除，该既有变更未触碰，不属于下列文件清单。没有提交、推送、切换分支、修改数据库或替换正在运行的系统。

## 风险、限制与后续操作

- 未验证真实 MySQL 查询执行、Neo4j 投影、外部 OpenAlex/Crossref 采集及门户集成运行；SQL 验证覆盖真实 Mapper 的生成语句与绑定参数，不能替代数据库执行。
- Docker 恢复后可在 `systems/crawler` 执行 `.\mvnw.cmd -f backend/pom.xml '-Dtest=SourceCrawlPersistenceTests,ExportPersistenceIntegrationTests,GraphQueryIntegrationTests' test`，补充隔离容器集成验证；此命令本轮未执行。
- 整仓来源检查仍需从可信原始仓库或历史备份恢复 scholar 原始 Git 对象。该系统已退出当前产品范围，本轮未改写其归档证据。
- ECharts 共享图表包仍有 500 kB 阈值警告。该提示不阻止构建，相关图表模块本轮未修改。
- 查看新页面需按现有项目启动/发布流程使用本轮构建和后端源码；本轮没有重启或部署当前环境。无需额外数据库迁移。

## 实际变更文件

以下路径均相对仓库根目录；仅列本轮新增、修改和删除的文件。

| 状态 | 文件 | 本轮改动 |
| --- | --- | --- |
| 新增 | `docs/crawler-ux-improvements.md` | 记录修复、验证证据、边界和完整变更清单 |
| 修改 | `docs/development.md` | 同步入口、共享实现、来源校验和验证限制 |
| 修改 | `docs/integration-baseline.md` | 同步当前架构、接口约定和验证边界 |
| 修改 | `docs/source-adaptations.json` | 同步 crawler 文件哈希和显式删除，保留原始来源及 relation |
| 修改 | `README.md` | 更新子系统技术栈、默认入口和改进记录链接 |
| 修改 | `scripts/check-source.mjs` | 将缺失文件纳入显式删除校验 |
| 新增 | `scripts/lib/source-adaptations.mjs` | 集中校验适配内容与删除记录 |
| 新增 | `scripts/lib/source-adaptations.test.mjs` | 覆盖删除、篡改、重复和过期记录 |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/catalog/api/CatalogController.java` | 目录查询新增可选实体 ID 参数 |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/catalog/domain/CatalogQuery.java` | 增加实体 ID 校验及共享年份/来源筛选读取方法 |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/api/CrawlTaskController.java` | 提供两个批量只读摘要接口 |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/CrawlTaskService.java` | 批量读取与参数、权限边界 |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/application/port/CrawlRepository.java` | 增加批量最新运行和计划存取约定 |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/persistence/CrawlMapper.java` | 声明两个批量查询 |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/crawl/infrastructure/persistence/MyBatisCrawlRepository.java` | 转换批量查询结果 |
| 修改 | `systems/crawler/backend/src/main/java/com/aacv/system/export/domain/ExportFilter.java` | 增加模糊文本字段，保留旧构造与 JSON 兼容 |
| 修改 | `systems/crawler/backend/src/main/resources/mapper/catalog/CatalogMapper.xml` | 统一文本、精确 ID、年份与来源过滤 |
| 修改 | `systems/crawler/backend/src/main/resources/mapper/crawl/CrawlMapper.xml` | 最新创建任务排序及两项批量 SQL |
| 修改 | `systems/crawler/backend/src/main/resources/mapper/export/ExportMapper.xml` | 复用目录条件，移除重复 SQL |
| 新增 | `systems/crawler/backend/src/test/java/com/aacv/system/catalog/infrastructure/persistence/CatalogExportFilterSqlTests.java` | 加载真实 XML 校验列表/导出 SQL 和批量参数绑定 |
| 新增 | `systems/crawler/backend/src/test/java/com/aacv/system/crawl/application/CrawlActivitySecurityTests.java` | 验证新增摘要接口的原有方法权限 |
| 修改 | `systems/crawler/backend/src/test/java/com/aacv/system/crawl/application/CrawlTaskServiceTests.java` | 验证批量 ID 边界与去重 |
| 修改 | `systems/crawler/backend/src/test/java/com/aacv/system/export/domain/ExportFilterTests.java` | 验证旧 JSON 和新增文本条件 |
| 修改 | `systems/crawler/frontend/e2e/fixtures/workbench.ts` | 合成夹具补充两个批量摘要接口 |
| 删除 | `systems/crawler/frontend/e2e/graph-overview-vis.spec.ts` | 迁移至 graph-overview.spec.ts，保留有效交互覆盖 |
| 新增 | `systems/crawler/frontend/e2e/graph-overview.spec.ts` | 以真实 Cytoscape 绘制验证图谱、交互和窄屏 |
| 修改 | `systems/crawler/frontend/e2e/graph-types.spec.ts` | 同步本轮导航、图谱、表单或导出行为的浏览器断言 |
| 修改 | `systems/crawler/frontend/e2e/migration-visual.spec.ts` | 同步本轮导航、图谱、表单或导出行为的浏览器断言 |
| 修改 | `systems/crawler/frontend/e2e/optimization-crawl.spec.ts` | 同步本轮导航、图谱、表单或导出行为的浏览器断言 |
| 修改 | `systems/crawler/frontend/e2e/optimization-navigation.spec.ts` | 同步本轮导航、图谱、表单或导出行为的浏览器断言 |
| 修改 | `systems/crawler/frontend/e2e/page-layout.spec.ts` | 同步本轮导航、图谱、表单或导出行为的浏览器断言 |
| 修改 | `systems/crawler/frontend/e2e/redesign-interactions.spec.ts` | 同步本轮导航、图谱、表单或导出行为的浏览器断言 |
| 修改 | `systems/crawler/frontend/e2e/research-dashboard.spec.ts` | 同步本轮导航、图谱、表单或导出行为的浏览器断言 |
| 修改 | `systems/crawler/frontend/e2e/research-theme.spec.ts` | 同步本轮导航、图谱、表单或导出行为的浏览器断言 |
| 修改 | `systems/crawler/frontend/e2e/stage7-export.spec.ts` | 同步本轮导航、图谱、表单或导出行为的浏览器断言 |
| 新增 | `systems/crawler/frontend/e2e/ux-workflows.spec.ts` | 新增同名筛选、返回分页、导出、工作台和表单回归 |
| 修改 | `systems/crawler/frontend/package-lock.json` | 移除四项不再使用的直接依赖并同步解析结果 |
| 修改 | `systems/crawler/frontend/package.json` | 移除四项不再使用的直接依赖并同步解析结果 |
| 修改 | `systems/crawler/frontend/playwright.config.ts` | 支持选择生产构建预览进行浏览器回归 |
| 修改 | `systems/crawler/frontend/src/components/business/AchievementPreview.vue` | 预览详情链接携带返回条件 |
| 修改 | `systems/crawler/frontend/src/components/business/AppSidebar.test.ts` | 更新工作台与大屏的导航契约 |
| 修改 | `systems/crawler/frontend/src/components/business/AppTopbar.vue` | 统一工作台返回入口 |
| 新增 | `systems/crawler/frontend/src/components/business/crawl/CrawlRunDialog.vue` | 独立运行详情、计数、结束原因与防旧响应轮询 |
| 新增 | `systems/crawler/frontend/src/components/business/crawl/CrawlScheduleDialog.vue` | 独立每日计划读取、保存、删除和版本冲突处理 |
| 新增 | `systems/crawler/frontend/src/components/business/crawl/CrawlTaskDialog.vue` | 独立任务编辑、名称选择与高级参数折叠 |
| 修改 | `systems/crawler/frontend/src/components/business/EntitySuggestInput.vue` | 保留选中 ID，编辑文字清除 ID，多候选回车不擅自选中 |
| 修改 | `systems/crawler/frontend/src/components/business/FormField.vue` | 同步单一表单校验的说明 |
| 删除 | `systems/crawler/frontend/src/components/business/graph-overview/GraphCanvas.test.ts` | 交互与清理覆盖迁移到共享画布测试 |
| 删除 | `systems/crawler/frontend/src/components/business/graph-overview/GraphCanvas.vue` | 删除第二套图谱画布 |
| 修改 | `systems/crawler/frontend/src/components/business/graph-overview/NodeDetail.test.ts` | 以直接节点数据验证安全文本和零值展示 |
| 修改 | `systems/crawler/frontend/src/components/business/graph-overview/NodeDetail.vue` | 直接读取 GraphNode，移除旧渲染 DTO |
| 新增 | `systems/crawler/frontend/src/components/business/GraphCanvas.test.ts` | 覆盖局部筛选坐标、查询更换、单双击及资源释放 |
| 修改 | `systems/crawler/frontend/src/components/business/GraphCanvas.vue` | 统一 Cytoscape 渲染、交互、布局与坐标保留 |
| 修改 | `systems/crawler/frontend/src/components/business/ModuleNavigation.vue` | 工作台和大屏分别提供正确导航 |
| 修改 | `systems/crawler/frontend/src/components/business/StatusPill.vue` | 补齐部分成功、暂停中、取消中和已取消状态 |
| 修改 | `systems/crawler/frontend/src/config/nav.ts` | 工作台、大屏和图谱样式入口 |
| 修改 | `systems/crawler/frontend/src/router/index.ts` | 新增工作台与大屏分路由、样式别名及目录工作区 |
| 修改 | `systems/crawler/frontend/src/services/business.ts` | 目录 ID 和两个批量任务摘要请求 |
| 删除 | `systems/crawler/frontend/src/services/health.test.ts` | 删除仅针对闲置健康服务的测试 |
| 删除 | `systems/crawler/frontend/src/services/health.ts` | 移除没有调用方的健康服务 |
| 修改 | `systems/crawler/frontend/src/stores/graph-overview.test.ts` | 验证本地筛选不增加网络请求 |
| 修改 | `systems/crawler/frontend/src/stores/graph-overview.ts` | 去掉筛选防抖请求及重复应用状态 |
| 删除 | `systems/crawler/frontend/src/stores/preferences.ts` | 删除没有调用方的偏好状态 |
| 修改 | `systems/crawler/frontend/src/types/api.ts` | 扩展可兼容的导出文本筛选类型 |
| 新增 | `systems/crawler/frontend/src/utils/catalog-query.ts` | 规范目录 URL 条件、分页和返回参数 |
| 修改 | `systems/crawler/frontend/src/utils/export-filter.test.ts` | 验证模糊条件、ID 和年份导出映射 |
| 修改 | `systems/crawler/frontend/src/utils/export-filter.ts` | 直接映射已显示查询，去掉实体反查 |
| 新增 | `systems/crawler/frontend/src/utils/graph-presentation.test.ts` | 迁移响应和展示定义验证 |
| 新增 | `systems/crawler/frontend/src/utils/graph-presentation.ts` | 保留共享图响应校验和默认展示定义 |
| 删除 | `systems/crawler/frontend/src/utils/graph-vis.test.ts` | 验证迁移至 graph-presentation.test.ts |
| 删除 | `systems/crawler/frontend/src/utils/graph-vis.ts` | 移除专用 vis DTO，保留必要逻辑于 graph-presentation.ts |
| 删除 | `systems/crawler/frontend/src/utils/motion.test.ts` | 删除仅针对闲置 motion 工具的测试 |
| 删除 | `systems/crawler/frontend/src/utils/motion.ts` | 移除没有调用方的动效工具 |
| 修改 | `systems/crawler/frontend/src/views/AchievementDetailView.vue` | 返回目录时保留查询条件 |
| 修改 | `systems/crawler/frontend/src/views/CatalogView.vue` | URL 恢复、实体 ID 和基于当前结果的导出 |
| 修改 | `systems/crawler/frontend/src/views/CrawlTasksView.test.ts` | 适配弹窗与摘要接口，补充取消后迟到轮询测试 |
| 修改 | `systems/crawler/frontend/src/views/CrawlTasksView.vue` | 列表与历史入口保留，拆出独立弹窗并显示最新运行/计划 |
| 修改 | `systems/crawler/frontend/src/views/GraphTypesView.vue` | 明确只管理节点和关系显示样式 |
| 修改 | `systems/crawler/frontend/src/views/GraphView.vue` | 改用共享 Cytoscape 画布 |
| 删除 | `systems/crawler/frontend/src/views/OperationsView.vue` | 删除不可达的运维页面，已有后端与日志入口保留 |
| 修改 | `systems/crawler/frontend/src/views/SourcesView.vue` | 统一 Element Plus 表单校验和提交保护 |
| 新增 | `systems/crawler/frontend/src/views/WorkbenchView.vue` | 新增搜索、最近任务和需关注运行工作台 |
| 修改 | `systems/crawler/README.md` | 更新工作台、筛选导出、运行与单一图谱引擎说明 |
