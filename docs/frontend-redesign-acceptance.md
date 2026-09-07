# 中文学术工作台视觉与动效升级验收

本次在任务开始时已经存在的 Element Plus 未提交实现上增量改造，保留学术蓝、中文业务含义、路由与权限。界面调整涵盖共享外壳、筛选和表格、浮层、反馈，以及工作台、目录、成果详情、图谱、统计、采集六个重点页面。全部接口检查使用项目已有合成夹具；未写业务数据库，未进行真实后端联调。

## 实施范围与结果

| 范围 | 本轮实现 |
| --- | --- |
| 外壳与公共组件 | 60px 顶栏、240/64px 侧栏、1600px 内容上限；14px 正文、24px 标题、等宽数字；单层面板和连续指标带；窄屏下拉框 44px、分页按钮 40px，以页码输入保留任意页跳转 |
| 工作台 | 趋势主区与合作排行、采集摘要相邻；合作网络与领域条形图辅助阅读；保留同源数据展开和审计摘要，移除无实际轮询支撑的“实时”标识 |
| 成果目录 | 将编目入口、筛选和结果整合为一个工作区；保留全部筛选、分页、详情与 CSV/JSON 导出；题名完整换行，DOI 独立呈现 |
| 成果详情 | 规范记录、作者与引用为主区，来源指标为证据栏；保留来源观测时间、未知状态、版本关系、轨迹与字段状态 |
| 统计分析 | 总量和趋势优先；覆盖率与趋势并排，显示分子、分母和口径；分布图按实际条目调整高度，保留合作表格及各图同源摘要 |
| 图谱 | 收紧查询区；稳定 ID 增量同步，保留既有节点位置、缩放与平移；节点表/关系表与画布选择一致，提供明确的聚焦、重置及取消选择操作 |
| 采集 | 运行编号查询收为工具条；任务名称与编号分层；运行指标减少嵌套卡片，直接显示真实计数；任务控制、限额、历史复查与轮询契约保留 |
| 长表单 | 完整弹窗受视口高度约束，只滚动正文；数据源提交区固定在 footer，原生 form 提交、校验焦点、冲突原因与关闭后的焦点恢复均保留 |

动效集中为 120/200/280/320ms：普通反馈、外壳和浮层、新节点及镜头、图表更新。侧栏使用可取消的 Web Animations 位移；页面仅淡入新内容，旧页立即卸载。ECharts 合并稳定系列并清除消失的系列；Cytoscape 不再清空整图或随机重排。运行时修改减少动画偏好立即生效，卸载清理图实例、ResizeObserver、监听与排队帧。合作排行只对稳定主键的位置变化使用 TransitionGroup；指标不插值，日志不抢占历史阅读位置。

## 同夹具前后截图

前后均为本机真实 Edge、1440px、浅色、同一合成数据夹具，截图在本次修改前重新采集，并非沿用旧迁移阶段的历史画面。空数据保留为空，没有为截图补写业务记录。

| 页面 | 改造前 | 改造后 |
| --- | --- | --- |
| 工作台 | [查看](./frontend-redesign-evidence/before-overview.png) | [查看](./frontend-redesign-evidence/after-overview.png) |
| 成果目录 | [查看](./frontend-redesign-evidence/before-catalog.png) | [查看](./frontend-redesign-evidence/after-catalog.png) |
| 成果详情 | [查看](./frontend-redesign-evidence/before-detail.png) | [查看](./frontend-redesign-evidence/after-detail.png) |
| 知识图谱 | [查看](./frontend-redesign-evidence/before-graph.png) | [查看](./frontend-redesign-evidence/after-graph.png) |
| 统计分析 | [查看](./frontend-redesign-evidence/before-analytics.png) | [查看](./frontend-redesign-evidence/after-analytics.png) |
| 采集任务 | [查看](./frontend-redesign-evidence/before-crawl.png) | [查看](./frontend-redesign-evidence/after-crawl.png) |

深色、窄屏及关键状态的代表证据在同目录归档；完整全路由截图为本地检查产物 `.local/migration-visual/all/`。第一轮检查截图保留在 `.local/frontend-redesign/review/`，基线在 `.local/frontend-redesign/before/`。

| 场景 | 归档截图 |
| --- | --- |
| 工作台深色、大屏 | [深色 1440](./frontend-redesign-evidence/after-overview-dark-1440.png)、[浅色 1920](./frontend-redesign-evidence/after-overview-light-1920.png) |
| 窄屏重点页面 | [目录深色](./frontend-redesign-evidence/after-catalog-dark-390.png)、[图谱](./frontend-redesign-evidence/after-graph-light-390.png)、[统计](./frontend-redesign-evidence/after-analytics-light-390.png)、[详情](./frontend-redesign-evidence/after-detail-light-390.png) |
| 图谱抽屉、来源证据 | [深色抽屉](./frontend-redesign-evidence/after-graph-drawer-dark-390.png)、[原有丰富来源夹具的桌面视口](./frontend-redesign-evidence/after-detail-sources.png) |
| 长表单冲突状态 | [浅色 390](./frontend-redesign-evidence/after-source-validation-390.png)、[深色 390](./frontend-redesign-evidence/after-source-validation-dark-390.png) |
| 多页与异常状态 | [第 7 页底部操作区](./frontend-redesign-evidence/after-catalog-pagination-dark-390.png)、[加载](./frontend-redesign-evidence/after-catalog-loading-dark-390.png)、[空结果](./frontend-redesign-evidence/after-catalog-empty-dark-390.png)、[错误](./frontend-redesign-evidence/after-catalog-error-dark-390.png) |

共归档 26 张 PNG（约 2.17MB），均为页面截图，没有生成装饰资产或向业务页面注入展示数据。

## 测试基线与执行记录

| 实际执行命令 | 修改前基线 | 当前验证 |
| --- | --- | --- |
| `npm --prefix .\frontend run test` | 22 个文件、89 项通过 | 25 个文件、99 项通过 |
| `npm --prefix .\frontend run build` | vue-tsc 与 Vite 通过 | 类型检查和生产构建通过；保留原有大 chunk 告警 |
| `npm --prefix .\frontend run test:e2e` | 44 项通过 | 52 项通过（3.7 分钟），Edge 单 worker，模拟接口 |
| `npm --prefix .\frontend run test:e2e -- --grep '数据源表单校验\|窄屏下拉框'` | 不适用 | 收尾修复的 5 项直接测试通过（17 秒），随后再运行上述完整回归 |
| `git diff --check` | 本轮收尾检查 | 无空白错误；Git 提示工作树 LF/CRLF 转换，不涉及文件重写 |
| `git -c core.autocrlf=false diff --check` | 不适用 | 返回 2；禁用项目换行处理后将既有 CRLF 行尾的 CR 计为尾随空白 |
| `git -c core.autocrlf=false -c core.whitespace=cr-at-eol diff --check` | 不适用 | 返回 0；显式识别 CRLF 后无空白错误，按项目原设置的上项检查也返回 0 |
| 下方记录的 `impeccable.cmd detect` | 不适用 | 对当时本轮修改的前端目标执行一次，返回 `[]`；未重复检测 |

初始普通沙箱执行 Vitest 因进程创建 `spawn EPERM` 及原生模块读取限制失败，使用审批允许的执行方式后建立通过基线；这是环境限制。实施中首次类型检查发现 Cytoscape 集合收窄与 ECharts 缓动字面量类型问题，已修正。第一轮 Edge 为 46 通过、1 失败：新字体栈的单引号不符合 Cytoscape 样式解析器要求，已转换为兼容双引号并复验通过，保留原断言，没有跳过或降低标准。

最终交付检查验证了验收记录的 71 个本地链接、44 个变更文本文件的严格 UTF-8 解码，以及 23 个依赖/配置/接口/会话文件与初始 SHA-256 快照一致。上述 CRLF 空白误报通过读取规则校正，没有修改 Git 配置、批量重写文件或弱化实际测试。

新增验证覆盖图元素实例、拖拽位置、选择和镜头保留，删除及悬空边处理，新增节点与端点更新；真实指标终值；图表系列更新、减少动画和卸载；键盘选择与画布同步；连续侧栏折叠；深浅主题主操作/说明文字对比度和窄屏按钮尺寸；目录加载、空、错误及迟到响应。收尾增加长表单正常、校验失败、服务端冲突的完整可见性检查（1440/390、深浅主题），以及 240 条、12 页合成夹具的查询、下一页和任意页跳转；没有依赖自动滚动点击来证明浮层布局合格。

机械检查实际命令如下；路径数组来自任务开始快照的差异清单，检测结果保存在 `.local/frontend-redesign/design-detect.json`：

```powershell
$redesignTargets = @(Get-Content -Raw .local/frontend-redesign/changed-existing.json | ConvertFrom-Json) | Where-Object { $_ -like 'frontend/src/*' }
$redesignTargets += 'frontend/src/styles/workbench.css'
$redesignTargets += 'frontend/src/composables/useMotion.ts'
$redesignTargets += 'frontend/src/utils/graph-rendering.ts'
& 'C:/Users/likecandy/.codex/skills/impeccable/scripts/impeccable.cmd' detect --json @redesignTargets | Tee-Object -FilePath .local/frontend-redesign/design-detect.json
exit $LASTEXITCODE
```

浏览器矩阵覆盖全部现有路由的 1440×900、1920×1080、390×844，深浅主题与减少动画；另验证系统主题即时跟随、普通动效切换、图谱抽屉、数据源冲突弹窗、质量样本弹窗、权限、会话过期、导出与任务控制。检查浏览器运行时错误、页面横向溢出、重复初始请求、键盘焦点约束与关闭恢复。截图不能证明动画质量，动画连续性和清理由独立交互断言补充。

## 独立收尾审阅

按 Impeccable Operate 的流程，先集中检查并修复，再确认截图，运行一次机械检测；随后由独立上下文的审阅者读取六页前后图、桌面/大屏/窄屏、浮层和源码/测试证据。首次结论为 `fix`，仅要求下列三项材料修复。修复后的相同 16 张截图与 3 张补充截图均经有效性检查，再交回同一审阅者做限定范围确认。

| 首次发现 | 实施与确认 | 最终评分 |
| --- | --- | --- |
| 窄屏长弹窗的保存动作超出视口 | 完整高度约束、正文滚动、固定 footer；直接检查正常/校验失败/冲突和关闭焦点 | `resolved` |
| 窄屏 Select 与分页目标偏小 | 实际命中框映射为 44px/40px，保留页码输入；多页查询及跳页检查通过 | `resolved` |
| 旧文档与当前令牌/动效不一致 | 独立文档同步，DESIGN 对照最终代码，旧验收标记历史 | `resolved` |

最终判定为 `disposition: ship`，其范围是上述三项修复均已解决，没有将本次限定确认表述为全系统或真实后端验收。独立文档同步仅修改既有 `DESIGN.md` 与 `design-qa.md`，未建立新记忆体系。

## 本轮变更文件

以下按任务开始时的工作区快照分类，包含当时已未跟踪但已有内容的文件；不把此前组件迁移的删除或依赖修改计入本轮。

| 文件 | 本轮类型 | 说明 |
| --- | --- | --- |
| [DESIGN.md](../DESIGN.md) | 修改 | 同步学术工作台构图、颜色/字号令牌、公共组件、浮层、分页和动效规范。 |
| [design-qa.md](../design-qa.md) | 修改 | 标记旧视觉验收为历史，说明旧动效记录不再指导当前实现。 |
| [docs/system-design.md](./system-design.md) | 修改 | 更新当前前端职责、图谱增量渲染、动效与生命周期及浮层/分页约定。 |
| [docs/frontend-migration-inventory.md](./frontend-migration-inventory.md) | 修改 | 保留迁移历史并指向本轮升级验收。 |
| [docs/known-limitations.md](./known-limitations.md) | 修改 | 补充模拟接口验证及密集图谱布局边界。 |
| [docs/frontend-redesign-acceptance.md](./frontend-redesign-acceptance.md) | 新增 | 记录改造范围、逐文件清单、截图、基线、最终验证、审阅与限制。 |
| [frontend/src/styles/tokens.css](../frontend/src/styles/tokens.css) | 修改 | 调整学术蓝、表面/状态色、中文字号、层级、间距和动效令牌。 |
| [frontend/src/styles/index.css](../frontend/src/styles/index.css) | 修改 | 接入公共工作台样式，统一正文和响应式内容宽度。 |
| [frontend/src/styles/element-plus.css](../frontend/src/styles/element-plus.css) | 修改 | 将库控件尺寸映射到令牌，校正深色按钮、长表单高度、抽屉和窄屏交互。 |
| [frontend/src/styles/workbench.css](../frontend/src/styles/workbench.css) | 新增 | 统一页面标题、单层面板、连续指标带、筛选表格、空态/错误和工具条。 |
| [frontend/src/layouts/BusinessLayout.vue](../frontend/src/layouts/BusinessLayout.vue) | 修改 | 侧栏折叠使用可取消位移，页面只淡入新内容，统一外壳和导航抽屉。 |
| [frontend/src/composables/useMotion.ts](../frontend/src/composables/useMotion.ts) | 新增 | 共享 CSS 动效时长并监听运行时减少动画偏好。 |
| [frontend/src/utils/graph-rendering.ts](../frontend/src/utils/graph-rendering.ts) | 新增 | 按稳定标识同步图元素与有效关系，仅为新增节点确定位置。 |
| [frontend/src/components/CountUpNumber.vue](../frontend/src/components/CountUpNumber.vue) | 修改 | 保留入口，立即展示实际终值与无效值状态。 |
| [frontend/src/components/EChartCanvas.vue](../frontend/src/components/EChartCanvas.vue) | 修改 | 复用实例与稳定系列，统一主题、更新时长及尺寸/卸载清理。 |
| [frontend/src/components/business/AppSidebar.vue](../frontend/src/components/business/AppSidebar.vue) | 修改 | 品牌区与分组导航排版，细选中标识及短过渡。 |
| [frontend/src/components/business/AppTopbar.vue](../frontend/src/components/business/AppTopbar.vue) | 修改 | 60px 顶栏、明确表面与紧凑命令搜索入口。 |
| [frontend/src/components/business/PageHeader.vue](../frontend/src/components/business/PageHeader.vue) | 修改 | 统一中文标题、描述及操作区的排版和窄屏换行。 |
| [frontend/src/components/business/PanelSection.vue](../frontend/src/components/business/PanelSection.vue) | 修改 | 单层面板与分隔线，保留完整标题和插槽。 |
| [frontend/src/components/business/FilterBar.vue](../frontend/src/components/business/FilterBar.vue) | 修改 | 统一筛选区域与操作区，减少重复边框。 |
| [frontend/src/components/business/FilterField.vue](../frontend/src/components/business/FilterField.vue) | 修改 | 统一标签层级和说明对比，保留表单关联。 |
| [frontend/src/components/business/DataTable.vue](../frontend/src/components/business/DataTable.vue) | 修改 | 统一表头/单元格/加载状态，窄屏分页提供页码输入。 |
| [frontend/src/components/business/StatCard.vue](../frontend/src/components/business/StatCard.vue) | 修改 | 中性指标排版，配合连续指标带，保留真实数值和未知状态。 |
| [frontend/src/components/business/StatusPill.vue](../frontend/src/components/business/StatusPill.vue) | 修改 | 紧凑状态标签，取消持续脉冲，保留中文文本与语义色。 |
| [frontend/src/components/business/EmptyState.vue](../frontend/src/components/business/EmptyState.vue) | 修改 | 清晰描边图标、适度留白及中文原因。 |
| [frontend/src/components/business/ErrorState.vue](../frontend/src/components/business/ErrorState.vue) | 修改 | 紧凑错误行，保留 traceId、原因及重试操作。 |
| [frontend/src/components/business/AnalyticsCoveragePanel.vue](../frontend/src/components/business/AnalyticsCoveragePanel.vue) | 修改 | 以连续行展示覆盖比例、分子/分母和统计口径。 |
| [frontend/src/components/business/ScholarlySourcePanel.vue](../frontend/src/components/business/ScholarlySourcePanel.vue) | 修改 | 来源证据采用响应式分组与分隔线，减少卡片嵌套。 |
| [frontend/src/components/business/GraphCanvas.vue](../frontend/src/components/business/GraphCanvas.vue) | 修改 | 稳定增量图形、邻域聚焦、选择、镜头控制及可中断动画和清理。 |
| [frontend/src/components/business/GraphWorkspace.vue](../frontend/src/components/business/GraphWorkspace.vue) | 修改 | 同步画布/表格选择，提供聚焦/重置/取消和响应式检查器。 |
| [frontend/src/components/business/LiveLogPanel.vue](../frontend/src/components/business/LiveLogPanel.vue) | 修改 | 取消逐行入场，只在末尾跟随新增日志，保留历史阅读位置。 |
| [frontend/src/views/OverviewView.vue](../frontend/src/views/OverviewView.vue) | 修改 | 趋势主区与排行/采集摘要分组，真实指标、稳定图形及最新请求保护。 |
| [frontend/src/views/CatalogView.vue](../frontend/src/views/CatalogView.vue) | 修改 | 合并目录入口/筛选/结果构图，完整题名与 DOI 分层，防止迟到筛选覆盖。 |
| [frontend/src/views/AchievementDetailView.vue](../frontend/src/views/AchievementDetailView.vue) | 修改 | 规范记录与来源证据分栏，改善正文和来源核对顺序。 |
| [frontend/src/views/GraphView.vue](../frontend/src/views/GraphView.vue) | 修改 | 收紧中心查询区和预置说明，保留关系筛选与高级入口。 |
| [frontend/src/views/AnalyticsView.vue](../frontend/src/views/AnalyticsView.vue) | 修改 | 总量/趋势优先与覆盖率并列，分布高度适配和最新请求保护。 |
| [frontend/src/views/CrawlTasksView.vue](../frontend/src/views/CrawlTasksView.vue) | 修改 | 紧凑运行查询、任务名称与编号分层、真实运行指标分组。 |
| [frontend/src/views/SourcesView.vue](../frontend/src/views/SourcesView.vue) | 修改 | 固定提交区、原生 form 关联及校验后的字段聚焦。 |
| [frontend/src/components/CountUpNumber.test.ts](../frontend/src/components/CountUpNumber.test.ts) | 新增 | 验证首屏/更新的真实终值、零值、无效值与后缀。 |
| [frontend/src/components/EChartCanvas.test.ts](../frontend/src/components/EChartCanvas.test.ts) | 新增 | 验证系列合并/删除、减少动画、观察器和排队帧释放。 |
| [frontend/src/utils/graph-rendering.test.ts](../frontend/src/utils/graph-rendering.test.ts) | 新增 | 验证实例/位置/镜头保留、去重、空结果、悬空边与端点更新。 |
| [frontend/e2e/fixtures/workbench.ts](../frontend/e2e/fixtures/workbench.ts) | 新增 | 原样提取并复用现有全路由合成夹具，未定义接口明确失败。 |
| [frontend/e2e/migration-visual.spec.ts](../frontend/e2e/migration-visual.spec.ts) | 修改 | 复用夹具并加强长表单四种主题/视口场景的布局与焦点验证。 |
| [frontend/e2e/redesign-interactions.spec.ts](../frontend/e2e/redesign-interactions.spec.ts) | 新增 | 验证图谱/侧栏连续操作、减少动画、对比度、异步状态及多页窄屏操作。 |

另新增 `docs/frontend-redesign-evidence/` 中上述 26 张截图；`.local/frontend-redesign/` 保存初始快照和命令日志，`.impeccable/review/` 保存独立审阅输入。这些是本轮验证证据，不属于应用运行代码。

## 工程边界与项目记忆

- 开始前读取用户提供的 AGENTS 指令、README、DESIGN、系统设计当前前端章节、迁移清单和已知限制，并检查实际 Git 状态、已有 diff、路由、组件、设计令牌及测试入口。
- 同步检查另读取旧 `design-qa.md` 及相关交接上下文；这些记录与代码有冲突时按最终代码、令牌和测试校正，不以历史验收替代当前验证。
- `.local/frontend-redesign/initial-hashes.json` 与 `baseline-files/` 保留任务开始时相关文件的散列和副本；本轮 diff 以该快照为基准复核，避免把先前迁移的修改与删除计入本次成果。
- `package.json`、锁文件、Vite/Playwright 配置、路由、服务、API 类型和共享会话实现保持任务开始时内容；未修改后端、数据库和部署，未创建分支、提交或 PR。
- `DESIGN.md` 是唯一设计权威；系统设计 §14.6 同步新的构图、时长、图谱增量更新、真实计数、浮层/分页和清理边界。旧文档中的 800ms 图谱、600ms 计数、持续脉冲等描述与此次实现不一致，已以代码、令牌和测试为依据同步；`design-qa.md` 明确标为历史验收，避免旧结果成为当前规范。
- 迁移清单保留历史记录并链接本次验收；已知限制补充模拟接口和图谱布局边界。未发现独立项目记忆文件，没有新建独立记忆系统。

## 风险与未验证事项

- 本轮不验证真实账号、Cookie/CSRF 轮换、MySQL/Neo4j、外部采集或部署；模拟通过不代表真实后端联调通过。
- 验证浏览器为当前主机 Microsoft Edge；未做 Firefox、Safari、真实移动设备和软键盘验证，也未测高并发、长时间运行或 300 节点密集网络性能。
- 采集任务全路由截图复用原有空列表夹具；既有非空采集流程测试已通过，但没有补做非空任务列表的窄屏截图矩阵。
- ECharts 对应 chunk 仍超过 Vite 默认 500kB 告警阈值，基线已有；没有改阈值、依赖或构建配置来隐藏告警。
- 增量图谱优先保留用户阅读位置，稠密或断开的网络可能仍需拖拽、缩放、重置和聚焦；不会以自动全图重排打断探索。
- 无 lint 脚本，未执行或声称执行 lint。项目其他已知限制继续以 [已知限制](./known-limitations.md) 为准。
