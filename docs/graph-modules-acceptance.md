# 知识图谱子模块与类型配置验收

核对日期：2026-09-07。本记录对应图一的菜单样式、MySQL 类型配置和 Canvas 作者关系展示。用户已确认审核针对类型配置，作者合作按 Neo4j 共同作品推导。

## 默认展开布局调整

2026-09-07：按“默认全部散开”的要求调整概览默认布局。原实现把全部新节点放在半径140的同一圆周上，初始节点数增多时位置过密；初始布局与100次稳定化也不足以保证默认展开效果。

- 首次加载和切换范围时，按节点数量及画布宽高比生成二维网格，初始中心间距至少180布局单位，并适配较大节点的尺寸。普通刷新继续保留已拖动的位置，合作详情的专用坐标保持优先。
- 布局迭代上限调为400，关系弹簧长度220、斥力参数-8000、中心吸引0.03、节点避让1；布局后适配整个网络。没有修改查询范围、业务数据、节点类型配置或依赖。
- 实际修改：`frontend/src/components/business/graph-overview/GraphCanvas.vue`、同目录 `GraphCanvas.test.ts`、`frontend/e2e/graph-overview-vis.spec.ts`、`DESIGN.md` 和本记录。
- 验证命令（在 `frontend` 目录）：`npm run test -- src/components/business/graph-overview/GraphCanvas.test.ts`，6项通过；`npm run test:e2e -- e2e/graph-overview-vis.spec.ts`，6项通过；`npm run build`，类型检查和生产打包通过，保留已有大分块告警。项目根目录 `git diff --check` 通过。
- 单元测试确认300节点初始坐标唯一且两两间距至少180，并验证范围切换与合作坐标优先级。浏览器以80节点、120关系检查稳定后的80个独立节点圆面及画布覆盖范围，普通动画与减少动画模式均通过；原有拖动、缩放、详情、导航、刷新和重复进入流程通过。已查看截图 `frontend/test-results/graph-vis-default-spread-no-preference.png`，测试使用合成数据，不代表所有真实数据拓扑的标签无重叠保证。
- 已读取 DESIGN、系统设计、已知限制及本验收记录并对照画布代码；更新 DESIGN 和本记录以同步新的默认布局规则。系统设计的实例复用、请求和数据边界未变化，无需修改；没有新增项目记忆体系。未提交、发布或部署。

## 概览 vis-network 重构

2026-09-07：仅重构 `/graph` 概览。先读取用户提供的 AGENTS 规则、README、DESIGN、系统设计、已知限制、交接文档和本记录，核对现有路由、Pinia、Axios、Element Plus、前后端图谱契约与未提交改动，再建立计划并实现。以下为本轮结果，下方保留原有实施历史。

### 实现与边界

- 新增固定运行依赖 `vis-network@9.1.9`、`vis-data@7.1.9`，采用 peer 导入并显式加载 CSS；7.1.9 满足 network 的 vis-data peer 范围。参考[官方导入及生命周期文档](https://visjs.github.io/vis-network/docs/network/)。保留既有 Cytoscape 依赖和高级查询、路径分析页面，未修改后端、认证、权限或数据库。
- 新画布通过 props 接收数据、emit 报告交互；shallowRef 持有 Network，DataSet 只作渲染副本。实例单次挂载创建一次，全图、两跳和合作详情切换时复用，普通更新保留位置与视角。处理尺寸变化、KeepAlive、隐藏、减少动画、卸载以及临时回调释放。
- 圆形节点、短标签、完整名称提示、有向关系与类型配置样式已接入；合作依照既有派生语义保持无向虚线及共同作品依据。完整业务 `label` 映射到画布 `name` / `title`，不会回写缩写；本项目 `type` 表示节点类型。扩展 JSON 必须安全解析为对象，详情和 tooltip 使用纯文本。
- 独立 store 管理图、筛选、选择和历史。筛选约200ms防抖，输入变化立即取消并失效旧请求；成功时同时提交图、筛选与导航，失败保留结果。双击节点请求现有 subgraph，固定双向两跳及300节点上限；历史节点可截断后续记录，“返回”回到上一级，“全部”恢复受限概览。
- 校验重复节点/关系 ID、缺失端点、类型、空名称、非法扩展 JSON、类型配置以及300节点上限，异常不静默过滤。关系沿用后端 ID；颜色和尺寸缺失有默认值，合法零值保留。选择已移除的节点或关系时同步清理选择。
- 单击和右键菜单提供详情与两跳入口，保留同源节点表、关系表和合作作品清单。没有可持久化接口的新增、编辑、删除及拖动建立关系入口均明确禁用，不展示假保存成功；未新增不可提交的节点/关系表单。

### 已核实接口与缺口

| 能力 | 当前实际接口或所需补充 |
| --- | --- |
| 概览 | `GET /api/v1/graph/overview`，最多300节点；“全部”不表示无限全图 |
| 双向两跳 | `GET /api/v1/graph/subgraph`，现有 Cypher 的无向路径同时覆盖入/出边；不从当前局部数据计算两跳 |
| 类型配置 | 图响应携带 `typeDefinitions`；现有 `/api/v1/graph/types` 只维护类型，不是业务节点编辑接口 |
| 全局搜索/类型/关系组合筛选 | overview 暂不支持这些查询参数；当前对返回范围作筛选，跨全库按名称定位仍通过高级查询。需要后端定义范围、筛选顺序及截断语义 |
| 全局图统计 | 没有专用统计契约；当前只展示当前视图统计，不从局部结果推断全库总量 |
| 节点新增、编辑、删除 | 需补充以 MySQL 权威数据为边界的业务写入契约：允许字段、完整名称、类型、扩展字段、版本冲突、权限与删除关联处理；成功返回稳定 ID 及规范记录 |
| 关系新增、删除 | 需补充真实关系 ID、端点、类型、幂等性、自连接/重复规则、版本冲突和权限；合作为派生关系，不能默认直接增删 |

独立画布的 `startRelation()` 受 `allowCreateRelation`（默认 false）控制，`manipulation.addEdge` 仅 emit `{ from, to }`，不改业务数据。未来接入时，页面需在端点事件后协调关系表单，成功后由 store 更新 props，再调用 `finishRelation()` 以 null 结束临时回调；取消、失败后关闭、换图和卸载也需结束回调。当前页面不启用此流程，未把测试注入关系描述为真实持久化完成。没有调用参考 `/api/support/confront_map/` URL。

### 本轮修改文件

| 文件 | 变更 |
| --- | --- |
| `frontend/src/views/GraphView.vue` | 概览页面协调、导航、右键菜单、能力说明与详情 |
| `frontend/src/components/business/graph-overview/GraphCanvas.vue` | 独立 vis-network 画布、增量数据与生命周期 |
| `frontend/src/components/business/graph-overview/NodeDetail.vue` | 完整名称与扩展字段详情 |
| `frontend/src/stores/graph-overview.ts` | 概览状态、防抖、取消、导航和选择 |
| `frontend/src/services/graph-overview.ts` | 复用现有概览与两跳接口 |
| `frontend/src/utils/graph-vis.ts` | 严格数据校验、默认配置与画布映射 |
| `frontend/src/utils/graph-overview.ts` | 可保留两跳返回的其他受支持类型，原默认行为不变 |
| `frontend/src/utils/graph-vis.test.ts` | 非法数据、名称、默认值和扩展字段测试 |
| `frontend/src/stores/graph-overview.test.ts` | 竞态、失败保留、导航、账号清理测试 |
| `frontend/src/services/graph-overview.test.ts` | 路径、参数与取消信号测试 |
| `frontend/src/components/business/graph-overview/GraphCanvas.test.ts` | 实例复用、交互、临时回调和资源清理测试 |
| `frontend/src/components/business/graph-overview/NodeDetail.test.ts` | 完整名称、零值和纯文本安全测试 |
| `frontend/e2e/graph-overview-vis.spec.ts` | 真实 Canvas 交互、导航、空态、异常、窄屏与重复进入测试 |
| `frontend/package.json`、`frontend/package-lock.json` | 固定两项直接依赖及所需 peer 包 |
| `README.md`、`DESIGN.md`、`docs/system-design.md`、`docs/known-limitations.md`、`docs/graph-modules-acceptance.md` | 同步实际行为、模块边界、接口缺口与验证 |

### 验证记录

在 `frontend` 目录执行：

```powershell
npm run test -- src/utils/graph-vis.test.ts src/utils/graph-overview.test.ts src/utils/graph-cooperation.test.ts src/utils/graph.test.ts src/stores/graph-overview.test.ts src/stores/graph.test.ts src/services/graph-overview.test.ts src/components/business/graph-overview
npm run test:e2e -- e2e/graph-overview-vis.spec.ts e2e/graph-types.spec.ts e2e/stage7-graph.spec.ts
npm run build
```

- 最终单元回归：9个测试文件、40项通过；包含新模块和原有图谱数据回归。
- 最终 Microsoft Edge 回归：17项全部通过。新用例验证实际 Canvas 的节点拖动、平移、缩放、完整名称 tooltip、节点/关系右键、双击两跳、历史截断、失败保留、非法数据、空态、窄屏、重复进出与同一画布复用；既有合作作品及高级查询流程通过。接口使用合成夹具，不写业务库。
- 最终构建：类型检查和 Vite 打包成功。概览 JS 683.82kB / gzip206.90kB，CSS224.31kB / gzip34.85kB；保留分块大于500kB的真实告警。
- 中间验证发现并修复两处 vis 类型定义差异和 `editNode: undefined` 配置错误；关系曲线定位测试补充选中颜色，窄屏导航测试等待路由提交，连续拖动按库源码50ms双 Hammer 合并窗口安排独立手势，未放宽业务断言。初次 Vite 构建因沙箱 `spawn EPERM` 失败，经工具权限流程重跑通过。
- 已查看桌面浅色与390px深色截图，确认布局、完整名称入口和缩放后可读性。图片为 `frontend/test-results/graph-vis-desktop.png`、`frontend/test-results/graph-vis-mobile-dark.png`；测试产物位于现有忽略目录，未加入源码。
- 在项目根目录运行 `git diff --check`，未发现空白错误。未运行后端测试、真实节点/关系写入或部署；这些不属于本次实现范围。
- 最终工作区哈希对比：本轮修改9个已有文件、新增11个文件，无文件删除；其余742个基线文件未变化。将本轮新增的两项直接依赖和八个新增包记录从锁文件的内存副本移除后，内容哈希与实施前完全一致，确认已有锁定依赖未升级或被改写。20个本轮文件均通过 UTF-8 无 BOM 检查。

### 记忆同步与限制

读取既有 README、DESIGN、系统设计、已知限制、交接和本记录；更新上述表格中的五份文档，没有新增记忆体系。核对源代码后确认旧“概览合作切换重建画布”描述已不适用，已改为 Network/DataSet 复用；高级查询的 Cytoscape 行为保持不变。交接步骤未变化，无需更新交接文档。

未完成项是业务写入及全局查询/统计接口，不是前端模拟补齐。未验证长名称编辑保存、真实新增关系后删除、删除级联/权限/版本冲突、自连接和重复关系业务规则、生产性能及跨浏览器兼容。依赖安装提示 `uuid@9.0.1` 已弃用；固定参考版本的 peer 约束仍沿用，没有擅自升级不兼容主版本。未提交 Git、发布、部署或执行数据库迁移。

## 合作作品展示修复

2026-09-07 本轮针对“图谱无法体现作者之间合作创作了什么作品”。已核对现有接口包含 `sharedWorkIds`，原因在前端：合作线只写关系名，合作筛选还移除了作品和创作边。实现与数据核对顺序为：先读取设计、系统设计、已知限制及本验收文档，记录工作区基线，建立修复计划，再修改、测试和同步文档。以下是本轮记录，下方保留历次实施历史。

- 新增统一合作证据解析，只展示后端合作边中列出且有双方实际 `AUTHORED` 连线的作品；去重重复 ID，排除个人作品、第三作者与无效证据，缺失依据给出明确提示。
- 合作线显示共同作品数量及首部作品摘要。“合作作品”列出每对作者和完整作品名；点击合作线或“在图中查看”，显示双方作者、共同作品节点及双方创作连线，旁边提供完整标题与作品详情链接。两位作者并排置顶、作品逐行居中，避免沿用全网坐标造成标签重叠；窄屏详情位于画布下方。
- 概览和高级查询的合作筛选都保留作品依据。概览合作模式可按作者或作品名称搜索，停用冲突的节点类型筛选；退出单组合作后恢复此前筛选。画布、列表、关系表均使用同一响应，没有新增关系或补造作品。
- 延续 Neo4j 拓扑与 MySQL 类型样式的既有分工；本轮没有修改后端、接口、数据库、依赖、权限或启动配置。

本轮实际变更文件：

| 文件 | 变更 |
| --- | --- |
| `frontend/src/utils/graph-cooperation.ts` | 新增合作证据核对、聚焦子图与作品摘要 |
| `frontend/src/utils/graph-cooperation.test.ts` | 新增多作品、去重、单方或缺失证据及配置名称回归 |
| `frontend/src/utils/graph.ts` | 合作摘要、证据高亮 ID 和保留作品的关系视图 |
| `frontend/src/utils/graph.test.ts` | 合作视图保留作品及创作边断言 |
| `frontend/src/utils/graph-overview.ts` | 合作搜索与筛选保持完整证据 |
| `frontend/src/utils/graph-overview.test.ts` | 按作品搜索合作和保留双方创作证据 |
| `frontend/src/components/business/GraphCanvas.vue` | 合作证据高亮、摘要换行及单组合作独立布局 |
| `frontend/src/components/business/GraphWorkspace.vue` | 高级查询使用完整响应映射作品摘要 |
| `frontend/src/views/GraphView.vue` | 合作列表、完整作品详情、聚焦画布及窄屏布局 |
| `frontend/e2e/graph-types.spec.ts` | 多作品、个人作品排除、筛选、窄屏和既有流程回归 |
| `DESIGN.md` | 合作线、作品详情、证据保留和布局规范 |
| `docs/system-design.md` | 合作证据解析和展示职责、纠正旧查询边界文字 |
| `docs/known-limitations.md` | 当前网络证据范围及最新验证入口 |
| `docs/graph-modules-acceptance.md` | 本轮变更、验证和记忆同步记录 |

在项目根目录实际执行：

| 命令 | 结果 |
| --- | --- |
| `npm --prefix frontend run test` | 28个文件、113项通过 |
| `npm --prefix frontend run build` | Vue 类型检查和生产构建通过；既有 ChartFrame 大于500kB警告保留 |
| `npm --prefix frontend run test:e2e -- graph-types.spec.ts stage7-graph.spec.ts` | Edge 13项通过，含两部共同作品、个人作品排除、合作筛选、390px深色布局及既有高级查询 |
| `git -c core.safecrlf=false diff --check` | 无空白错误 |

第一轮浏览器回归12项通过、1项因关闭动画期间重复匹配文字而失败；将断言限定为实际合作详情区域，同时避免关闭抽屉时渲染多余详情内容后，完整13项通过。视觉复核发现少量节点沿用全网位置时拥挤，调整为合作专用布局，再次构建和完整浏览器回归均通过。未改弱业务断言。测试通过已有工具授权运行，没有安装或替换依赖。

已查看合成数据截图 `frontend/test-results/cooperation-works-desktop.png`（1500px）与 `cooperation-works-mobile.png`（390px深色），截图保存在忽略目录。另在正在运行的 `/graph` 业务页面只读操作“合作作品 → 在图中查看”：原网络20节点、38关系，选中的两位作者拥有2部共同作品，画布对应4节点、5关系；完整标题、双方创作箭头和合作线已可见。没有写入业务数据或重启服务。本轮不重复执行后端集成测试，也不把这些结果解释为全库性能验收。

项目记忆继续使用 `DESIGN.md`、`docs/system-design.md`、`docs/known-limitations.md` 和本记录，均已读取并同步。未新增记忆体系。核对路由、请求及服务代码后发现系统设计查询边界仍把2跳初始中心查询称为“图谱概览”，与现有自动概览不一致，已明确为高级查询，并区分局部查询100节点与概览300节点默认上限。已有未提交工作按任务开始时的文件哈希基线核对并保留。

使用时在图谱概览点击合作虚线，或打开“合作作品”并选择“在图中查看”；当前开发页面已热更新，无需重启或迁移。作品依据仍仅覆盖当前返回网络，不能据此认定全库合作总量；缺失依据时使用高级查询进一步核对。

## 作者作品概览与管理页参考图调整

2026-09-07 后续调整以五张新参考图为依据。图一与图二的概览相同；实体和关系采用用户指定的作者、作品及创作、合作领域，不使用参考图中的其他领域类型。

- `/graph` 改为紧凑16px标题、搜索/类型/关系工具栏和占据剩余视口的 Canvas；浮动按钮、当前结果统计、两色图例和抽屉表格/详情与画布共用数据。搜索保留匹配名称的一跳邻居，筛选移除悬空边；刷新失败保留既有图，空图有明确提示。侧栏父项、子项为14px，与其他模块字号一致。
- `GET /api/v1/graph/overview` 沿用 GRAPH_READ、受管标识及3秒事务超时，只读 Neo4j Author / Achievement 和方向正确的 AUTHORED，优先边再孤立节点。默认及最大300节点、候选路径最多 `nodeLimit*4+1`；合作证据仍由展示服务按返回作品计算，MySQL 样式配置不变。空网络返回200，超限明确截断。
- 实体管理仅显示 AUTHOR / ACHIEVEMENT 两类及各自 MySQL 颜色；关系管理仅显示 AUTHORED（作者→作品）/ COAUTHORED（作者↔作者）。支持名称搜索、表头审核筛选、详情、编辑和逐项版本校验的批量审核。批量中首次失败会停止，并明确成功数量。固定投影类型不提供任意新增/删除，不直接写 Neo4j。
- 原 GraphView 中的五类中心查询完整保留为 GraphExploreView，入口为 `/graph/explore`；旧中心参数和常用查询从 `/graph` 自动转入。路径及常用查询原有地址继续可用。本轮未改变数据库迁移、依赖或业务权限。

本轮变更文件（以下路径均相对于项目根目录）：

| 文件 | 变更 |
| --- | --- |
| `frontend/src/views/GraphView.vue` | 大画布概览、自动加载、下拉筛选、结果统计和详情 |
| `frontend/src/views/GraphExploreView.vue` | 承接原中心查询页面及全部既有查询能力 |
| `frontend/src/views/GraphTypesView.vue` | 作者/作品与创作/合作两类表格、详情、批量审核 |
| `frontend/src/components/business/GraphCanvas.vue` | 新增概览多簇力导向布局选项，原布局不变 |
| `frontend/src/components/business/AppSidebar.vue` | 知识图谱父项和子项字号统一14px |
| `frontend/src/router/index.ts` | 高级查询路由 |
| `frontend/src/services/business.ts` | 概览读取接口 |
| `frontend/src/utils/graph-overview.ts` | 有界结果搜索及节点、关系筛选 |
| `frontend/src/utils/graph-overview.test.ts` | 空值、域限制、悬空边和合作证据测试 |
| `frontend/e2e/graph-types.spec.ts` | 自动图、两类表格、字号、批量审核、窄屏和失败恢复 |
| `frontend/e2e/stage7-graph.spec.ts` | 原查询用例转向高级查询，旧链接兼容仍验证 |
| `backend/src/main/java/com/aacv/system/graph/api/GraphController.java` | 新只读概览接口与参数边界 |
| `backend/src/main/java/com/aacv/system/graph/application/GraphQueryService.java` | 受管作者作品查询、上限及空图 |
| `backend/src/test/java/com/aacv/system/graph/GraphQueryIntegrationTests.java` | 概览空图、权限、合作证据和节点上限 |
| `DESIGN.md` | 概览构图、两类表格、字号及布局规范 |
| `README.md` | 概览、高级查询和两类管理入口 |
| `docs/system-design.md` | 新查询边界、路由兼容和批量部分成功语义 |
| `docs/openapi.yaml` | 概览接口契约 |
| `docs/authorization-matrix.md` | 概览沿用图谱读取权限 |
| `docs/known-limitations.md` | 受限网络、筛选范围和业务联调限制 |
| `docs/graph-modules-acceptance.md` | 本轮实施、验证及记忆同步记录 |

本轮实际验证：

- `npm --prefix frontend run test`：27个文件、109项通过。
- `npm --prefix frontend run build`：Vue 类型检查和生产构建通过；原有 ChartFrame 大于500kB提示保留。
- `npm --prefix frontend run test:e2e -- graph-types.spec.ts stage7-graph.spec.ts`：最终 Edge 11项通过，包含批量审核部分冲突回归。
- `.\mvnw.cmd -f backend/pom.xml '-Dtest=GraphQueryIntegrationTests' '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/AACV_System/backend/target' test`：隔离 MySQL / Neo4j 10项通过，含既有查询、配置权限、CSRF及版本回归。

另以 Python / PyYAML 验证376个 OpenAPI 本地引用，核对21个变更文件与本轮清单一致、UTF-8无BOM；原查询页面除标题外与修改前内容一致。`git -c core.safecrlf=false diff --check` 通过。视觉复核发现实体/关系切换会复用旧表格列状态，已按类型重建表格，并补充状态列和取消选择断言。

浏览器截图使用明确合成的接口网络，已查看 `frontend/test-results/graph-overview-reference.png`（1600px）、`graph-entities-reference.png`、`graph-relations-reference.png`（1440px）及 `graph-overview-mobile.png`（390px深色）。截图和测试输出位于忽略目录，没有覆盖其他模块证据。

验证中修正了 Element Plus 泛型表格的行/实例类型；浏览器批量选择最初定位到隐藏原生 checkbox 而超时，改为点击可见包装元素并断言原生选中状态后通过，未削弱业务断言。

本轮读取的项目记忆为 README、DESIGN、系统设计、交接、已知限制和本验收文档，继续沿用已有记忆体系。已同步大画布、固定两类管理、自动概览接口、原查询迁移位置与错误语义。通过当前路由和查询代码核对，旧文档“概览不自动加载”及“首次只有同心布局”不再适用，已在系统设计和 DESIGN 中修正。本页下方保留前一轮实施历史，其验证记录不代表本轮又执行全部迁移测试。

本轮未连接业务数据库，也未重启正在运行的业务后端。使用更新后的前后端即可访问新概览接口；如果此前 V15 尚未应用，仍需按下文原有备份/升级流程处理。功能与浏览器验证不代表全库性能或真实账号联调验收。

## 前一轮任务总结

- 知识图谱父项成为可独立折叠的按钮，箭头跟随状态，支持键盘操作；子项为“图谱概览、实体管理、关系管理”，统一缩进、52px 行高、10px 圆角和中性灰选中背景。进入子路由自动展开。原路径分析、常用查询地址保留，概览提供入口。
- Neo4j 继续读取五类节点及五类实际关系；MySQL V15 新表保存类型名称、颜色、尺寸、审核状态与版本号。沿用现有 Vue / Element Plus / Pinia / Axios / Cytoscape、MyBatis、权限、CSRF 和审计机制，未增加依赖。
- 类型管理支持名称搜索、审核筛选、颜色选择和带版本保存。`GRAPH_READ` 可查看，`GRAPH_SYNC_MANAGE` 可编辑；冲突保留表单并提示刷新。新配置默认待审核；审核不代替逐条实体治理，也不改变业务可见性。
- 局部图可请求 `includeCoauthors=true`，后端按返回的 `AUTHORED` 边生成最多1000条 `COAUTHORED`；去重作者对和共同作品，保留作品依据并标注当前子图范围。前端可切换作者—作品、作者—作者与全部关系，画布、图例和表格共用响应中的 `typeDefinitions`。重新查询恢复全部关系视图。
- 新增 V15 后，原初始化、备份、恢复脚本的版本白名单会拒绝新版本。因此仅同步这三处版本检查与提示文字，保留数据库、路径、凭据和恢复保护。

## 变更文件

以下仅列本任务实际修改或新增的文件，不包含进入任务前已有的其他未提交改动。

| 文件 | 本次变更 |
| --- | --- |
| `frontend/src/components/business/AppSidebar.vue` | 折叠按钮、箭头、子项样式与自动展开 |
| `frontend/src/components/business/AppSidebar.test.ts` | 父按钮及三项折叠回归 |
| `frontend/src/config/nav.ts` | 图一对应的三个模块入口 |
| `frontend/src/router/index.ts` | 实体管理和关系管理路由，概览标题 |
| `frontend/src/router/index.test.ts` | 新路由沿用图谱权限的验证 |
| `frontend/src/views/GraphView.vue` | 图谱概览、数据来源说明、旧功能入口和合作关系请求 |
| `frontend/src/views/GraphTypesView.vue` | 实体/关系类型管理、筛选与编辑表单 |
| `frontend/src/services/graph-types.ts` | 类型配置读取和保存接口 |
| `frontend/src/services/business.ts` | 图查询的可选合作关系参数 |
| `frontend/src/stores/graph-types.ts` | 配置请求、保存、冲突及失效响应处理 |
| `frontend/src/stores/graph-types.test.ts` | 账号切换与冲突保存测试 |
| `frontend/src/types/api.ts` | 类型配置和派生合作关系响应类型 |
| `frontend/src/utils/graph.ts` | 配置映射、关系视图筛选、合作证据增量合并与上限 |
| `frontend/src/utils/graph.test.ts` | 配置映射、关系筛选和证据去重测试 |
| `frontend/src/components/business/GraphCanvas.vue` | 从元素数据读取颜色和尺寸，审核线型及无向合作虚线 |
| `frontend/src/components/business/GraphWorkspace.vue` | 关系视图切换、配置图例、类型审核和共同作品详情 |
| `frontend/e2e/graph-types.spec.ts` | 菜单、类型配置、Canvas 像素、合作依据、冲突与窄屏流程 |
| `frontend/e2e/stage7-graph.spec.ts` | 沿用既有查询流程并更新概览标题 |
| `frontend/e2e/migration-visual.spec.ts` | 更新概览标题断言，不重写历史截图 |
| `backend/src/main/java/com/aacv/system/graph/api/GraphController.java` | 为图响应附加类型配置，局部图可选合作关系 |
| `backend/src/main/java/com/aacv/system/graph/api/GraphTypeController.java` | 类型配置 GET / PUT 边界 |
| `backend/src/main/java/com/aacv/system/graph/application/GraphQueryService.java` | 适配包含展示配置的响应结构，实际查询逻辑保留 |
| `backend/src/main/java/com/aacv/system/graph/application/GraphPresentationService.java` | 组合 Neo4j 结果与 MySQL 配置，计算有界合作证据 |
| `backend/src/main/java/com/aacv/system/graph/application/GraphTypeService.java` | 字段校验、权限、乐观并发和事务审计 |
| `backend/src/main/java/com/aacv/system/graph/domain/GraphView.java` | 配置列表及派生关系输出 |
| `backend/src/main/java/com/aacv/system/graph/domain/GraphTypeDefinition.java` | 类型配置领域模型 |
| `backend/src/main/java/com/aacv/system/graph/infrastructure/persistence/GraphTypeMapper.java` | 类型配置持久化边界 |
| `backend/src/main/resources/mapper/graph/GraphTypeMapper.xml` | 批量读取和按版本更新 SQL |
| `backend/src/main/resources/db/migration/V15__create_graph_type_definitions.sql` | 新类型配置表、约束及11项待审核初始配置 |
| `backend/src/main/java/com/aacv/system/operations/domain/AuditAction.java` | 新增类型配置更新审计动作 |
| `backend/src/test/java/com/aacv/system/graph/application/GraphTypeServiceTests.java` | 字段边界、未知类型、版本冲突和审计测试 |
| `backend/src/test/java/com/aacv/system/graph/application/GraphPresentationServiceTests.java` | 合作去重、异常端点、1000条上限、缺失配置测试 |
| `backend/src/test/java/com/aacv/system/graph/GraphQueryIntegrationTests.java` | 真实隔离 MySQL / Neo4j、接口权限、CSRF、版本和证据测试 |
| `backend/src/test/java/com/aacv/system/infrastructure/database/FlywayMigrationTests.java` | V15 约束、版本计数及历史数据保留回归 |
| `tools/development/Initialize-RenderingSampleData.ps1` | 允许完整 V14 / V15 开发库 |
| `tools/stage8/New-Stage8DatabaseBackup.ps1` | 备份版本白名单包含 V15 |
| `tools/stage8/Test-Stage8BackupRecovery.ps1` | 恢复版本白名单包含 V15 |
| `DESIGN.md` | 菜单及数据库驱动画布设计规范 |
| `README.md` | 模块、数据分工和 V15 升级说明 |
| `docs/system-design.md` | 类型配置、派生合作边与接口职责 |
| `docs/openapi.yaml` | 类型配置接口、响应和查询参数契约 |
| `docs/authorization-matrix.md` | 新配置操作的现有权限映射 |
| `docs/development-handoff.md` | 当前迁移范围更新至 V15 |
| `docs/known-limitations.md` | 图谱验证范围及脚本验证限制 |
| `docs/graph-modules-acceptance.md` | 本验收记录 |

## 验证结果

在项目根目录执行：

| 实际执行命令 | 结果 |
| --- | --- |
| `npm --prefix frontend run test` | 26个文件、105项测试通过 |
| `npm --prefix frontend run build` | `vue-tsc -b` 和 Vite 生产构建通过 |
| `npm --prefix frontend run test:e2e -- graph-types.spec.ts stage7-graph.spec.ts` | Edge 6项通过 |
| `.\mvnw.cmd -f backend/pom.xml '-Dtest=GraphQueryServiceTests,GraphTypeServiceTests,GraphPresentationServiceTests' test` | 后端7项单元测试通过 |
| `.\mvnw.cmd -f backend/pom.xml '-Dtest=GraphQueryIntegrationTests' '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/AACV_System/backend/target' test` | 隔离 MySQL / Neo4j 8项集成测试通过 |
| `.\mvnw.cmd -f backend/pom.xml '-Dtest=FlywayMigrationTests' '-DargLine=-Djdk.net.unixdomain.tmpdir=F:/Program/Java/AACV_System/backend/target' test` | 隔离 MySQL 5项迁移测试通过 |

另使用 PowerShell `System.Management.Automation.Language.Parser.ParseFile` 检查三份变更脚本，并通过原生 Windows PowerShell 5.1 复核，无语法错误；脚本原有 UTF-8 BOM 保留，以维持5.1对中文字符串的读取方式。使用 Python / PyYAML 解析 `docs/openapi.yaml`，370个本地 `$ref` 均可解析。未执行初始化、备份或恢复脚本正文。

浏览器验证含 Canvas 实际像素颜色、图例与审核字段、作者—作品和作者—作者切换、共同作品详情、冲突保留编辑值、科研用户只读，以及390px深色视图。1440px检查三个导航子项左边界相同且高度均为52px。截图是合成接口数据，位于本地忽略目录 `frontend/test-results/graph-menu-reference.png`、`graph-overview-canvas.png`、`graph-coauthors.png`、`graph-mobile-dark.png`，未覆盖既有截图。

过程中出现并已解决的验证问题：Cytoscape 类型不接受显式 renderer 字段，因此保留默认 Canvas；MockMvc 需显式接入 Spring Security 测试上下文；409提示沿用项目统一错误文案；Element Plus 下拉通过键盘打开验证；父菜单由链接改为按钮后同步导航语义断言。前端沙箱出现 `spawn EPERM`，Docker 管道访问被拒；经工具授权后在沙箱外重跑通过，未安装或替换依赖。

## 项目记忆

读取并核对了 `README.md`、`DESIGN.md`、`docs/system-design.md`、`docs/development-handoff.md` 和 `docs/known-limitations.md`。没有独立命名的项目记忆文件，继续使用现有设计、交接和验收文档，没有新增记忆体系。

更新了上述文档以及 OpenAPI、权限矩阵与本记录，记录类型配置的数据归属、审核含义、合作证据范围、权限、迁移版本和验证命令。实施前的菜单与前端硬编码样式符合旧文档；引入 V15 后发现旧交接文档和辅助脚本仍限定 V14，通过读取迁移目录及脚本版本检查确认，并同步修正。

## 风险、限制与假设

- 本次未连接业务库执行迁移，未使用实际账号完成端到端联调；后端验证使用隔离容器，浏览器使用合成接口。
- 合作关系只反映已返回子图中的共同作品，深度或节点上限会影响可见证据；类型审核不等于逐条作者、作品或关系审核。
- 保留300个节点与1000条派生合作边上限，未将功能测试解释为大规模图谱性能验收。
- Vite 保留原有大于500kB的图表 chunk 警告；未调整阈值或依赖。三份脚本只完成静态检查，未据此声称业务备份恢复已验收。
- 未提交、推送、部署或重启业务服务；任务开始前的未提交工作保留。

## 用户需执行的操作

1. 按现有流程备份业务 MySQL。下一次启动新版后端会应用 V15，请在启动前完成备份。
2. 停止旧后端后，从项目根目录运行 `.\tools\development\Start-Development.ps1 -Component Backend`，确认启动及 Flyway 成功；此命令尚未由本任务执行。
3. 刷新已运行的前端，进入“知识图谱 → 图谱概览”，选择作者或作品并加载图谱。查询作者时使用默认2跳以读取共同作者；有管理权限的账号可在实体管理和关系管理中保存类型配置，再重新加载图谱。
