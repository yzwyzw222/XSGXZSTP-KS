# 独立学术图谱与研究页面调整

## 当前行为

本轮修改 `systems/crawler`，沿用 Vue、Element Plus、Cytoscape、Spring 和 MyBatis；没有增加依赖、改变鉴权规则或修改数据库结构。

- 学术关系、学术成果、学术背景分别是独立主导航模块，集成路径为 `/crawler/academic-relations`、`/crawler/academic-achievements`、`/crawler/academic-background`。切换保留作者，原 `/crawler/graph/relations`、`achievements`、`background` 地址保留查询参数重定向。画布默认占满可用宽高，内容清单按需展开。
- 实体编目新增 `/catalog/patents`、`/catalog/master-theses`、`/catalog/doctoral-theses`。作者、机构、期刊、专利、指导硕论、指导博论均显示内部标识和中文类型；作品详情复用成果预览。主题编目继续保留。
- 新作品编目按规范成果去重，遵循有效的标题与类型人工修订。指导论文必须存在真实 `achievement_advisor`，合并成员的指导关系也参与判断，多位导师不会重复计数。只有本人署名而没有指导记录的学位论文不会进入指导编目。
- 成果目录在右上角选择题名、作者、机构、期刊或主题搜索，日期列表头提供年份日历。已删除类型与来源筛选，旧 URL 中的这两个条件也不再提交。右上角小型导出按钮保留原 CSV/JSON 异步任务、进度与下载，导出条件来自已提交的列表查询。
- 统计分析合并重复分类导航，压缩指标和说明区域。搜索字段沿用现有 API 支持的机构、主题，必须从候选中选定规范 ID；年份范围位于年度图操作区、趋势表头或其他分类的摘要区，起始年晚于结束年不能提交。
- 统计导出为当前已加载聚合结果的 CSV/JSON，包含统计口径和已应用条件；合作排行上限二十项，不是全量原始成果导出。沿用 `EXPORT_CREATE` 权限，加载中或部分统计失败时禁止导出，CSV 文本做公式注入防护。

## 验证记录

以下验证于 2026-09-11 执行。前端浏览器用可复现的接口响应覆盖布局、点击、筛选和下载；后端使用隔离测试数据库验证真实 SQL，不把浏览器模拟数据视为本机业务数据验收。

在 `systems/crawler/frontend` 执行：

```powershell
npm.cmd test -- src/utils/catalog-query.test.ts src/utils/analytics-export.test.ts src/utils/export-filter.test.ts src/components/business/AppSidebar.test.ts src/router/index.test.ts src/utils/academic-graph.test.ts src/composables/useAuthorGraph.test.ts
npm.cmd run build -- --outDir=../../../.local/compact-research-review/dist --base=/crawler/
```

七个单元测试文件共 60 项通过。随后修正侧栏测试中的类型调用并再次运行该文件，13 项通过；生产构建中的 `vue-tsc -b` 和 Vite 构建通过。现有图表分包超过 500 kB 的构建提示仍存在，本轮未变更依赖或拆包规则。

在 `systems/crawler` 执行：

```powershell
.\mvnw.cmd -o -f backend/pom.xml '-Dtest=CatalogWorkEntitiesIntegrationTests,CatalogExportFilterSqlTests' test
```

八项通过，无失败、错误或跳过。覆盖三种新编目、导师与本人署名区分、规范合并去重、多导师、人工修订、分页、非法输入和权限，以及现有导出筛选 SQL。

随后运行 `.\mvnw.cmd -o -f backend/pom.xml '-Dtest=OpenApiDocumentTests' test`，OpenAPI 契约检查一项通过。两批后端验证共九项通过。

浏览器回归在 `systems/crawler/frontend` 分批执行：

```powershell
npm.cmd run test:e2e -- e2e/academic-graph.spec.ts e2e/graph-types.spec.ts e2e/stage7-analytics.spec.ts e2e/optimization-navigation.spec.ts --output=../../../.local/compact-research-review/browser-regression
npm.cmd run test:e2e -- e2e/compact-research.spec.ts e2e/academic-graph.spec.ts e2e/stage7-export.spec.ts e2e/stage7-analytics.spec.ts e2e/optimization-navigation.spec.ts e2e/optimization-catalog-evidence.spec.ts e2e/author-import.spec.ts --output=../../../.local/compact-research-review/browser-final
npm.cmd run test:e2e -- e2e/author-import.spec.ts e2e/optimization-navigation.spec.ts --output=../../../.local/compact-research-review/browser-followup
```

首批 17 项中 16 项通过，命令面板定位受到新增原生字段选择选项影响；已将定位限定在命令面板。后批 20 项中 18 项通过，作者导入测试仍断言旧路由及默认展开的清单；按新需求修改对应断言，最后复验这两个文件共五项全部通过。图谱回归包含真实画布作者、连线与成果节点点击，独立导航、权限和旧入口兼容；成果与统计覆盖日期筛选、详情、异步导出、CSV 内容、空结果、失败重试和窄屏。

集成路径下使用同一生产产物额外执行紧凑页面用例：

```powershell
$env:AACV_E2E_BASE = '/crawler'
try {
    npm.cmd run test:e2e -- --config ../../../.local/compact-research-review/playwright-production.config.ts e2e/compact-research.spec.ts --output=../../../.local/compact-research-review/browser-production
} finally {
    Remove-Item Env:AACV_E2E_BASE -ErrorAction SilentlyContinue
}
```

七项通过，覆盖 1440 和 390 宽度的统计、1440/2549/390 宽度的目录、六类实体与权限错误路径。随后补充与需求截图同宽的 2549 像素统计用例，使用同一生产配置和 `AACV_E2E_BASE`，执行 `npm.cmd run test:e2e -- --config ../../../.local/compact-research-review/playwright-production.config.ts e2e/compact-research.spec.ts --grep 2549px --output=../../../.local/compact-research-review/browser-production-wide`，目录与统计两项均通过；生产产物共覆盖八种用例。生产路径配置和产物位于 Git 忽略的 `.local/compact-research-review/`，可按现有 Playwright 配置启动相同的 Vite preview 复现。

沙箱内 Vite 子进程曾报 `spawn EPERM`，获得本机测试权限后原构建命令通过。测试早期的 TypeScript 定位 API、MockMvc 后的鉴权上下文断言问题均已修正为现有框架的实际契约。桌面统计图高度校验曾暴露重复导航占位，合并导航后通过，未降低布局验收阈值。

## 项目记忆与范围

实施前读取并对照 `development.md`、`integration-baseline.md` 和 `academic-graphs.md`，本轮更新三份文件的独立主导航、旧路由兼容、作品编目、紧凑检索及统计导出边界。旧父菜单和子路由描述与新要求不一致，已由当前导航、路由、SQL 和实际测试核对后同步；首轮构建、启动记录保留为历史事实。

同时将基线中“退役来源仍可校验”的旧描述修正为本机实际缺失 scholar 来源对象的限制，依据为本轮完整来源命令的真实失败结果。

`source-adaptations.json` 已同步本轮源码散列，`node .local/compact-research-review/sync-adaptations.mjs` 核对 relation、crawler 两个有效系统的全部文件与适配记录通过。完整 `node scripts/check-source.mjs` 在这两个系统通过后，仍因此前已存在的退役 scholar 源对象缺失而退出 1：`unknown revision`，对象为 `f11b0b8d99f37e8e971aa86661d0ba59a21fbd9d^{tree}`。没有为绕过该历史问题而修改导入记录或 Git 历史。

之前工作区的作者导入、图谱接口和其他用户修改均保留，没有提交或切换分支。本轮文件变化按实施前散列快照核对，下面仅列本轮新增或修改，未将已有修改归入本轮。

收尾执行 `git -c core.safecrlf=false diff --check` 通过。实施前散列快照与当前文件逐一比较，文档列出的四十个本轮变更完全匹配，942 个原有文件保持原散列，没有删除原有文件；新增代码注释使用简体中文，未加入调试输出。

## 变更文件

以下路径相对仓库根目录。构建、截图、运行备份和诊断日志均保存在 Git 忽略的目录，没有加入源码。

| 文件 | 本轮修改 |
| --- | --- |
| `docs/academic-graphs.md` | 独立模块入口、兼容链接与历史验证边界。 |
| `docs/compact-research-ui.md` | 当前使用方式、验证、范围与本机启用记录。 |
| `docs/development.md` | 同步研究页布局、编目和筛选契约。 |
| `docs/integration-baseline.md` | 同步独立入口及接口、导出边界。 |
| `docs/source-adaptations.json` | 更新本轮源码适配散列。 |
| `systems/crawler/backend/src/main/java/com/aacv/system/catalog/api/CatalogController.java` | 接入三种新增编目集合。 |
| `systems/crawler/backend/src/main/java/com/aacv/system/catalog/domain/CatalogEntityKind.java` | 增加专利、指导硕博类型。 |
| `systems/crawler/backend/src/main/resources/mapper/catalog/CatalogMapper.xml` | 规范作品类型、实际指导关系、人工修订及分页查询。 |
| `systems/crawler/backend/src/test/java/com/aacv/system/catalog/CatalogWorkEntitiesIntegrationTests.java` | 新编目 SQL、分页、导师语义和权限集成测试。 |
| `systems/crawler/docs/openapi.yaml` | 记录编目集合及语义，保留已有接口。 |
| `systems/crawler/frontend/src/config/nav.ts` | 独立主导航及新增编目子入口。 |
| `systems/crawler/frontend/src/router/index.ts` | 独立路由、旧地址兼容及新编目路由。 |
| `systems/crawler/frontend/src/router/index.test.ts` | 路由权限、兼容查询参数及页面生命周期。 |
| `systems/crawler/frontend/src/components/business/AppSidebar.vue` | 独立入口切换保留作者。 |
| `systems/crawler/frontend/src/components/business/AppSidebar.test.ts` | 独立导航和新编目入口验证。 |
| `systems/crawler/frontend/src/components/business/CommandPalette.vue` | 命令面板切换保留作者。 |
| `systems/crawler/frontend/src/components/business/ModuleNavigation.vue` | 独立主导航并移除重复第二层导航。 |
| `systems/crawler/frontend/src/components/business/CompactFieldSearch.vue` | 紧凑字段选择、文本和实体候选搜索。 |
| `systems/crawler/frontend/src/components/business/DataTable.vue` | 支持列标题筛选插槽。 |
| `systems/crawler/frontend/src/components/business/YearPicker.vue` | 兼容已有用法，增加紧凑年份按钮。 |
| `systems/crawler/frontend/src/components/business/YearRangeFilter.vue` | 年份范围弹层、清除和边界校验。 |
| `systems/crawler/frontend/src/services/author-import.ts` | 导入后的图谱入口指向独立成果模块。 |
| `systems/crawler/frontend/src/styles/research.css` | 缩小主导航高度，容纳新增独立入口。 |
| `systems/crawler/frontend/src/types/api.ts` | 编目集合类型补齐。 |
| `systems/crawler/frontend/src/utils/catalog-query.ts` | 紧凑检索 URL 契约，移除已删除筛选。 |
| `systems/crawler/frontend/src/utils/catalog-query.test.ts` | URL、非法参数和旧筛选兼容验证。 |
| `systems/crawler/frontend/src/utils/export-filter.test.ts` | 导出采用实际已应用的目录条件。 |
| `systems/crawler/frontend/src/utils/analytics-export.ts` | 当前聚合统计 CSV 与公式注入防护。 |
| `systems/crawler/frontend/src/utils/analytics-export.test.ts` | 聚合值、空数据与特殊文本导出验证。 |
| `systems/crawler/frontend/src/views/AcademicGraphView.vue` | 压缩操作栏，画布填满，清单按需展开。 |
| `systems/crawler/frontend/src/views/AnalyticsView.vue` | 紧凑搜索与指标、日历范围、单行分类和统计导出。 |
| `systems/crawler/frontend/src/views/CatalogEntitiesView.vue` | 新编目、内部标识、中文类型与成果详情。 |
| `systems/crawler/frontend/src/views/CatalogView.vue` | 列表填满、右上角搜索导出、日期表头筛选。 |
| `systems/crawler/frontend/e2e/academic-graph.spec.ts` | 独立切换、按需清单与画布空间回归。 |
| `systems/crawler/frontend/e2e/compact-research.spec.ts` | 三种宽度、六类编目、日历、下载、错误与权限。 |
| `systems/crawler/frontend/e2e/author-import.spec.ts` | 导入后新路由及清单展开交互。 |
| `systems/crawler/frontend/e2e/graph-types.spec.ts` | 独立主导航下保留图谱工具行为。 |
| `systems/crawler/frontend/e2e/optimization-navigation.spec.ts` | 新主导航数量与命令面板选项定位。 |
| `systems/crawler/frontend/e2e/stage7-analytics.spec.ts` | 紧凑统计导航、年份和口径说明。 |
| `systems/crawler/frontend/e2e/stage7-export.spec.ts` | 新搜索和日历条件下的导出任务、CSRF 及下载。 |

## 本机试用

2026-09-11 22:23 已通过本机安全输入窗口完成旧 JAR 与前端产物备份，使用既有 `scripts/Stop-Integration.ps1 -System crawler` 停止所属后端，再在 `systems/crawler` 执行 `.\mvnw.cmd -o -f backend/pom.xml -DskipTests package`，观察到 `BUILD SUCCESS`。该命令仅用于打包，测试结果以上面的独立测试为准。

已将 `/crawler/` 生产产物复制到 `systems/crawler/frontend/dist`，逐文件 SHA256 比较 132 个文件全部匹配；旧散列资源保留供已经打开的页面使用。通过 `.local/Start-LocalProject.ps1 -System crawler` 启用新后端，未初始化账号。密码只在交互启动进程内使用，不写入记录或配置。

本轮运行检查：

- `Invoke-RestMethod http://127.0.0.1:18083/crawler/actuator/health`、`.../health/readiness` 和 `.../health/graph` 均返回 `UP`。
- Flyway 日志确认 `course_crawler` 仍是 V17，`No migration necessary`，本轮没有执行新迁移。
- crawler PID 更新为 122616；门户 66972、relation 73020 保持原进程，现有数据保留。
- 三个图谱、新增编目、目录和统计八个匿名入口均返回登录页，符合既有认证行为；该检查不等同于登录后的业务数据验证。

试用入口：[学术关系图谱](http://127.0.0.1:18000/crawler/academic-relations)。使用原统一账号登录；已打开页面可强制刷新。真实业务数据下的交互留给用户试用，模拟浏览器回归及隔离后端测试不替代这一步。
