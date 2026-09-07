# 前端 Element Plus 重构迁移清单与实施计划

> 后续视觉升级：2026-09-07 在本清单记录的未提交 Element Plus 实现上增量改造，没有重做迁移或替换依赖。当前视觉构图、动效和验证证据见 [视觉升级验收](./frontend-redesign-acceptance.md)；以下内容保留为迁移阶段记录。

本文件是本轮前端重构（Reka UI/shadcn-vue + TanStack Table + 原生 fetch + Vue reactive 会话
→ Element Plus + Axios + Pinia）的**执行台账**：迁移清单、依赖迁移表、风险与验证方案。
每完成一批就更新"状态"列，最终随交付一并保留。

- 生成时间：2026-09-06
- 分支：`feature/Luo`
- 基线提交：`6d7fd60`（工作区另有 17 个已修改文件与多个未跟踪文件，全部保留，不通过重置或重新生成项目实施）

## 0. 基线记录（迁移前实测）

### 2026-09-06 接续核查

本次接手时工作区已经部分迁移；下表属于此前保留的历史记录，不是本次重新执行的结果。
接手复跑 Vitest：18 文件中 3 个失败，78 测试中 5 个失败、73 个通过，并有 1 个未处理导入错误。
构建在类型检查阶段失败：5 个页面仍导入已经删除的旧会话模块，并使用 TanStack 列类型。
首次沙箱执行另遇到 `spawn EPERM`，正常本机权限复跑后才取得上述测试结果。
已在原工作区增量完成，不恢复旧代码、不改变后端、不覆盖启动脚本与图片等用户文件。

B/C 契约增量验证：`npm --prefix .\frontend run test -- src/services/api.test.ts src/services/health.test.ts src/stores/session.test.ts`，43 项通过。
每个调用拥有覆盖等待 CSRF 的独立总期限；共享令牌请求单独有界，清除缓存取消旧等待者。
认证写入串行，退出立即清理内存与旧业务请求；迟到登录和旧账号 401 不得恢复或破坏当前账号状态。

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| 单元测试 | `npm --prefix .\frontend run test` | **18 个测试文件 / 43 个测试全部通过**，耗时 6.53s |
| 生产构建 | `npm --prefix .\frontend run build` | **成功**（`vue-tsc -b` 0 错误），72 个产物文件 |
| 构建体积 | — | dist 合计 **1,988,140 B**；JS **1,886,749 B**；CSS **99,194 B** |
| 体积告警 | — | 1 个 chunk > 500 kB：`ChartFrame`(636.60 kB / gzip 215.99 kB，ECharts)；`GraphWorkspace` 为 454.06 kB，低于阈值（纠正原台账计数） |
| E2E | `npm --prefix .\frontend run test:e2e` | **26 个测试全部通过**，耗时 40.7s（Playwright + msedge，单 worker，dev server 4173，接口由 `page.route` 模拟） |
| Lint | — | **项目未定义 lint 脚本**，本轮不声称运行过 lint |

原始日志：`.local/baseline-unit.log`、`.local/baseline-build.log`、`.local/baseline-e2e.log`。

## 1. 依赖迁移表

### 1.1 新增

| 依赖 | 版本（实测安装） | 用途 |
| --- | --- | --- |
| `element-plus` | 2.14.5 | 统一基础组件体系（表格、分页、表单、弹窗、抽屉、菜单、消息等） |
| `pinia` | 3.0.4 | 会话/权限/偏好等业务域共享状态 |
| `axios` | 1.20.0 | HTTP 传输层，替换原生 `fetch` |

### 1.2 保留

| 依赖 | 理由 |
| --- | --- |
| `vue` 3.5.42 / `vue-router` 5.3.0 / `typescript` 6.0.2 / `vite` 8.2.2 | 现有版本满足要求，任务明确禁止机械降级 |
| `tailwindcss` / `@tailwindcss/vite` | 继续作为布局工具（DESIGN.md §5） |
| `@vueuse/core` | `useColorMode`、`useStorage`、`useMediaQuery` 等生命周期能力 |
| `lucide-vue-next` | 图标体系（不引入 `@element-plus/icons-vue` 作为业务图标源；它作为 element-plus 自身依赖存在，仅供组件内部使用） |
| `echarts` 6.1.0 / `cytoscape` 3.34.2 | 图表与图谱引擎，锁定版本不变 |
| `zod` / `vee-validate` / `@vee-validate/zod` | 现有 schema 与校验规则保留，Element Plus 只负责呈现（DESIGN.md §6.2） |

### 1.3 已完成的旧依赖清理

下表保留迁移前引用位置；最终业务引用已归零，前五项依赖和对应旧组件已移除。

| 依赖 | 移除条件 | 引用核查结果 |
| --- | --- | --- |
| `reka-ui` | 所有 `components/ui/**` 与直接使用 reka-ui 的业务组件迁移完成 | 迁移前引用：40 个业务/页面文件通过 `@/components/ui/*` 间接依赖 |
| `@tanstack/vue-table` | `DataTable`、`AuditLogTable`、`GraphWorkspace` 及 11 个视图的 `ColumnDef` 全部替换为项目自有列类型 | 迁移前引用：14 个文件（`DataTable.vue`、`AuditLogTable.vue`、`GraphWorkspace.vue`、`types/tanstack.d.ts` + 11 个视图仅 `import type { ColumnDef }`） |
| `vue-sonner` | 7 处 `toast.*` 全部替换为 `ElMessage`/`ElNotification`，`App.vue` 移除 `<Toaster/>` | 迁移前引用：`App.vue`、`BusinessLayout.vue`、`CrawlTasksView.vue`、`GovernanceView.vue`、`OperationsView.vue`、`SourcesView.vue`、`UsersView.vue` |
| `class-variance-authority` | 仅 `components/ui/{button,toggle,alert,badge}` 使用，随这些文件移除 | 迁移前引用：4 个 ui 文件 |
| `tw-animate-css` | 仅 `components/ui/**` 使用 `animate-in/out`、`fade-in-0`、`zoom-in-95`、`slide-in-from-*`；改由 CSS/Element Plus 过渡承担 | 迁移前引用：`styles/index.css` 的 `@import` + 8 个 ui 文件 |
| `clsx` / `tailwind-merge` | **保留**：`lib/utils.ts` 的 `cn()` 仍被 Tailwind 布局类合并广泛使用 | — |

> 清理方式：逐个依赖经 `rg` 确认运行代码的导入与样式引用归零后，再从 `package.json` 移除并更新锁文件；历史说明中的名称不作为运行依赖。
> **不通过整目录删除来省略引用核查**。

## 2. 请求层迁移契约（Axios）

对外契约保持不变：`apiRequest<T>` / `api.get|post|put` 签名、`ApiError{status,code,traceId,fieldErrors}`、
`toErrorMessage`、`clearCsrfToken`、`setUnauthorizedHandler`。内部实现由 `fetch` 换成 Axios。

| 必须保留并验证的行为 | 验证方式 |
| --- | --- |
| `/api` 与 `/actuator` 代理与路径原样，不重复添加 `/api/v1` | 单元测试断言 URL 字面量；`vite.config.ts` 代理不变 |
| HttpOnly Cookie 会话（`withCredentials`/`same-origin`），不改为前端保存 JWT | 单元测试断言 `withCredentials: true`；无任何 token 落 localStorage |
| 非安全方法先取 CSRF，再用服务端返回的 `headerName`+`token` | 单元测试断言请求头名称来自响应 |
| 登录前/登录后/退出/会话过期的 CSRF 缓存清理 | `session.test.ts` + 新增 store 测试 |
| 默认 12s 预算，覆盖 CSRF + 业务请求总时限 | 单元测试（fake timers）断言总预算而非每段各 12s |
| 调用方 `AbortSignal`、已取消信号、超时、网络错误四者区分 | 单元测试断言 `REQUEST_CANCELLED` / `REQUEST_TIMEOUT` / `NETWORK_ERROR` |
| `application/problem+json`、`status`、`code`(`errorCode`)、`traceId`、`fieldErrors` | 单元测试 |
| 204 空响应 / 正常 JSON / Blob 下载 / 下载失败时的错误解析 | 单元测试 |
| 401 会话失效、403 无权限、409 冲突的既有文案 | 单元测试 + `stage6.spec.ts` |
| 健康检查 HTTP 503 仍解析有效 `DOWN`，且不改变普通业务请求错误判定 | `health.test.ts` + 新增用例 |
| 不虚构 `status === 1`/`results` 等通用响应结构 | 代码审查：没有添加响应包裹或拦截器，状态判断集中于 `api.ts` |
| 不自动重试写操作；拦截器不递归请求 CSRF | 代码审查 + 单元测试断言 CSRF 请求次数 |
| 并发失败不造成重复退出/提示轰炸；主动取消不显示为操作失败 | store 单元测试（并发 401 只触发一次） |
| 请求层不依赖页面或 Element Plus 弹窗 | 依赖审查：`services/**` 不 import `element-plus` |
| 不输出含密码/Cookie/CSRF/请求体的原始 Axios 配置日志 | 代码审查：无 `console.*` 打印 config |

## 3. Pinia 迁移契约

| Store | 职责 | 持久化 |
| --- | --- | --- |
| `stores/session.ts` | 会话状态机（`unknown`/`loading`/`authenticated`/`anonymous`）、当前用户、权限判定、登录/登出/恢复、失效标记 —— **唯一权威状态源** | 否（认证信息与权限快照一律不持久化） |
| `stores/preferences.ts` | 主题模式、侧栏折叠 | 是（`localStorage`，非敏感） |
| `stores/graph.ts` | 图结果、焦点、加载/错误与跨路由待执行查询；序号阻止旧响应覆盖 | 否；按账号保存的常用查询仍使用既有独立键 |

要求：
- 路由守卫内通过 `useSessionStore()` 获取 store，**不在模块加载期提前调用**；
  `createPinia()` 必须在 `router` 首次导航前安装（`main.ts` 中 `app.use(pinia)` 先于 `app.use(router)`，
  且 `router/index.ts` 的默认导出不得在模块顶层访问 store）。
- 避免 `router ↔ store ↔ service` 循环依赖：`services/api.ts` 通过 `setUnauthorizedHandler` 回调解耦，
  store 不 import router，导航由守卫与页面完成。
- 并发读取会话只发一次请求（in-flight Promise 复用）；登录/登出交错与迟到响应不得恢复已失效状态（请求序号/代际校验）。
- 注销或会话失效时清理与当前用户关联的数据、请求与轮询。
- 临时筛选与页码由页面组件持有；日志分类、图中心等显式深链参数从路由派生，不以两套监听重复请求。
- 迁移完成后删除 `services/session.ts` 中重复维护的 `reactive` 会话状态。

## 4. 逐页迁移清单

图例：⬜ 未开始 / 🔄 进行中 / ✅ 已完成并验证

| # | 路由 | 名称 | 权限 | 视图文件 | 主要请求 | 关键操作与状态 | 现有测试 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `/login` | 登录 | public | `LoginView.vue` | `POST /auth/login`、`GET /auth/csrf` | 用户名/密码校验（`请输入用户名和密码`）、redirect 回跳、`autocomplete` 契约 | `LoginView.test.ts`、`stage6`、`login-rendering` | ✅ |
| 2 | `/session-expired` | 会话过期 | public | `SessionExpiredView.vue` | — | 401 后由 `App.vue` 跳转；返回登录 | `stage6` | ✅ |
| 3 | `/forbidden` | 无权访问 | public | `ForbiddenView.vue` | — | 守卫 `hasPermission` 失败跳转 | `stage6` | ✅ |
| 4 | `/:pathMatch(.*)*` | 404 | public | `NotFoundView.vue` | — | 返回工作台 | — | ✅ |
| 5 | `/` | 工作台 | 登录即可 | `OverviewView.vue` | `analytics.overview/trends/distributions/collaboration`、`operations.overview`、`crawl.tasks`、`operations.audits` | 按权限分区加载与降级（`ANALYTICS_READ`/`CRAWL_TASK_READ`/`OPERATIONS_READ`）、指标自增、排行榜 FLIP、图表替代表格 | — | ✅ |
| 6 | `/catalog` | 成果目录 | `CATALOG_READ` | `CatalogView.vue` | `catalog.achievements`、`catalog.entities`、`exports.create/get/download` | 8 项筛选、导出 CSV/JSON（`EXPORT_CREATE`）、轮询导出票据、Blob 下载、空态 | `stage6`、`stage7-export` | ✅ |
| 7 | `/catalog/achievements/:id` | 成果详情 | `CATALOG_READ` | `AchievementDetailView.vue` | `catalog.achievement` | 分来源学术指标、撤稿/开放获取"未知≠0"、版本关系 DOI 链接、进入图谱（`GRAPH_READ`）、字段血缘 | `optimization-catalog-evidence` | ✅ |
| 8 | `/catalog/:collection(authors\|organizations\|venues\|topics)` | 实体目录 | `CATALOG_READ` | `CatalogEntitiesView.vue` | `catalog.entities`、`catalog.entityEvidence`、`catalog.relatedAchievements` | 名称检索、证据抽屉（窄屏可读）、署名观测区间与"年份未知"、关联成果分页 | `optimization-catalog-evidence` | ✅ |
| 9 | `/sources` | 数据源 | `SOURCE_READ` | `SourcesView.vue` | `source.page/create/update/setEnabled/probe` | 创建/编辑弹窗、启停（`SOURCE_MANAGE`）、探测（`SOURCE_PROBE`）、固定 `OPENALEX`/`CROSSREF`、乐观锁 version | — | ✅ |
| 10 | `/crawl` | 采集任务 | `CRAWL_TASK_READ` | `CrawlTasksView.vue` | `crawl.tasks/createTask/updateTask/trigger/schedule/run/failures/control` | 新建/编辑任务、每日计划、历史复查日期范围（`#dateFrom`/`#dateTo`/`#taskName`）、运行查询与追踪、进度百分比语义、额度延后与停止自动恢复、暂停/恢复/取消/重试失败、失败明细分页 | `optimization-crawl` | ✅ |
| 11 | `/governance` | 数据治理 | `GOVERNANCE_READ` | `GovernanceView.vue` | `governance.candidates/candidate/comparison/accept/reject/revertDecision/overrideField/revertOverride` | 候选筛选、证据对照（`role=table` + `aria-label`）、必须显式选择规范实体、版本关系阻止合并、比较失败不沿用旧详情、迟到响应不覆盖新候选、字段人工修正与撤销（`GOVERNANCE_MANAGE`） | `optimization-governance` | ✅ |
| 12 | `/quality` | 质量指标 | `GOVERNANCE_READ` | `QualityView.vue` | `quality.page/detail` | 按来源/运行/指标筛选、问题样本限量查看、指标代码保留英文 | — | ✅ |
| 13 | `/graph` | 节点与关系浏览 | `GRAPH_READ` | `GraphView.vue` | `graph.subgraph/path/syncStatus`、`catalog`（名称检索） | 条件区+画布区+详情区、名称检索候选明确选择、直接输入业务 ID、节点上限 300/去重/无悬空关系、截断建议、traceId、进入业务详情、节点表/关系表无障碍替代、常用查询抽屉（`details/summary`）、窄屏详情转抽屉 | `stage7-graph` | ✅ |
| 14 | `/graph/path` | 路径分析 | `GRAPH_READ` | `GraphPathView.vue` | `graph.path`、`graph.subgraph` | 起点/终点选择、最大 6 跳、路径画布与文字替代 | `stage7-graph`(部分) | ✅ |
| 15 | `/graph/queries` | 常用查询 | `GRAPH_READ` | `GraphQueriesView.vue` | 本机 `localStorage` | 最多 10 条、按账号隔离、应用后跳转图谱 | — | ✅ |
| 16 | `/analytics` | 统计分析 | `ANALYTICS_READ` | `AnalyticsView.vue` | `analytics.overview/trends/distributions/collaboration` | 年份区间筛选（`YearPicker`）、覆盖率面板（`75.0%`）、"各分类数量不能相加"、图表 + 同源表格替代、权威来源 `MYSQL`、合作排行 | `stage7-analytics` | ✅ |
| 17 | `/users` | 账号管理 | `USER_LIST` | `UsersView.vue` | `user.page/statistics/create/update/setEnabled/resetPassword/replaceRoles` | 用户类型环形图、最近 10 条登录活动、新增/统一编辑抽屉、姓名必填、用户名只读、密码 ≥12 位、管理员自我保护（不能停用自己/移除自己管理员角色/移除最后一个管理员）、409 冲突保留输入并提供"重新加载并替换表单"、统计 503 降级不影响列表、离开页面忽略迟到响应 | `account-management`(6) | ✅ |
| 18 | `/logs` | 日志管理 | `AUDIT_READ` | `LogsView.vue` | `operations.audits` | 操作/登录日志标签页与路由 `category` 同步、账号/时间范围/结果/操作类型筛选、本地时间→ISO UTC、请求取消（AbortController）、详情弹窗（IP/浏览器/traceId）、分页文案 `显示 X–Y，共 Z 条` | `LogsView.test.ts`、`account-management` | ✅ |
| 19 | `/operations` | 运行监控 | `OPERATIONS_READ` | `OperationsView.vue` | `actuator/health/{liveness,readiness,graph}`、`graph.syncStatus`、`operations.overview/alerts/acknowledgeAlert/graphEvents/replayGraphEvent/maintenanceRuns/startBackfill/startReconciliation/startRebuild/audits` | 健康检查 503 仍解析 `DOWN`、Neo4j 降级不影响 MySQL 区域、告警确认（`ALERT_MANAGE`）、事件重放、图运维（`GRAPH_SYNC_MANAGE`）、全量重建确认值、多标签页分区加载 | `stage7-operations` | ✅ |

### 4.1 布局与全局组件

| 组件 | 职责 | 迁移要点 | 状态 |
| --- | --- | --- | --- |
| `layouts/BusinessLayout.vue` | 侧栏 + 顶栏 + 主区 + 命令面板 + 移动抽屉 | 折叠状态改由 `preferences` store；移动抽屉换 `el-drawer`；退出登录改 `ElMessage`；保留 Ctrl/⌘+K、Ctrl/⌘+B、跳到主内容、页面过渡 | ✅ |
| `components/business/AppSidebar.vue` | 四组导航 + 分组标题 + 子导航 | 保留 `role="navigation" aria-label="业务导航"`、分组 `role="heading"`、`RouterLink`、折叠按钮可访问名 `折叠侧栏`/`展开侧栏` | ✅ |
| `components/business/AppTopbar.vue` | 移动菜单、面包屑、命令面板入口、主题、账号 | `打开导航菜单` 按钮名保留 | ✅ |
| `components/business/CommandPalette.vue` | 全局命令面板 | 保留 `命令面板搜索` textbox 名、`role="option"` 计数、Esc 关闭后 `role="dialog"` 数量为 0 | ✅ |
| `components/business/UserMenu.vue`、`ThemeToggle.vue`、`Breadcrumb.vue` | 账号菜单、主题切换、面包屑 | 换 `el-dropdown`；主题三态（浅色/深色/跟随系统） | ✅ |
| `components/business/DataTable.vue` | 服务端分页表格 | 换 `el-table` + `el-pagination`，自有列类型替换 `ColumnDef`，0 基页码转换，`#cell-*` 插槽、`row-key`、空态、加载态、排序事件保留 | ✅ |
| `components/business/AuditLogTable.vue`、`GraphWorkspace.vue` | 审计表、图谱工作区（含节点/关系表） | 同上；`GraphWorkspace` 保留 `节点表`/`关系表` 切换按钮名 | ✅ |
| `PageHeader`/`PanelSection`/`FilterBar`/`FilterField`/`StatCard`/`StatusPill`/`EmptyState`/`ErrorState`/`LoadingSkeleton`/`JsonEvidence`/`EntityLinks`/`LiveLogPanel`/`ConfirmDialog`/`AnalyticsCoveragePanel`/`ScholarlySourcePanel`/`CatalogEntityEvidencePanel`/`CandidateComparisonPanel`/`EntitySuggestInput`/`GraphEntityPicker`/`GraphPathForm`/`GraphSavedQueries`/`YearPicker`/`UserProfileFields`/`UserRoleChart`/`GraphCanvas`/`EChartCanvas`/`ChartFrame`/`CountUpNumber` | 业务抽象 | 保留抽象与对外 props/事件，内部换 Element Plus；证据/对照面板保留语义 `<table>` + `aria-label` | ✅ |

### 4.2 基础组件替换映射

| 现（`components/ui/*`，基于 reka-ui） | 目标（Element Plus） | 备注 |
| --- | --- | --- |
| `button/Button`（CVA variants：default/outline/ghost/destructive/secondary/link；size：sm/default/lg/icon；`as-child`） | `el-button`（`type`/`plain`/`text`/`link`/`size`/`circle`） | `as-child` 的 13 处改为 `RouterLink` 包裹或 `@click="router.push"` |
| `input/Input` | `el-input` | `type/placeholder/maxlength/readonly/disabled/autocomplete` 透传到内部 `<input>` |
| `textarea/Textarea` | `el-input type="textarea"` | — |
| `select/*`（8 处） | `el-select` + `el-option` | 内部 `<input role="combobox">`，`el-option` 为 `role="option"`，与现有 E2E 定位一致 |
| `checkbox`、`radio-group`、`switch`、`toggle`、`toggle-group` | `el-checkbox`、`el-radio-group`、`el-switch`、`el-radio-button`/`el-segmented` | — |
| `dialog/*`（8 处）、`alert-dialog`（`ConfirmDialog`） | `el-dialog`、`ElMessageBox` | 保留 `role="dialog"`、Esc 关闭、焦点恢复 |
| `sheet/*`（3 处） | `el-drawer` | 移动导航、图谱详情、账号编辑 |
| `dropdown-menu/*`（2 处） | `el-dropdown` | 主题切换、账号菜单；注意不锁 body 滚动（历史缺陷：主区收窄） |
| `popover`、`tooltip/*` | `el-popover`、`el-tooltip` | `YearPicker`、导航提示；实体候选仍为组合式业务组件 |
| `tabs/*`（3 处） | `el-tabs` + `el-tab-pane` | `role="tab"` + `aria-selected` |
| `alert/*`（15 处） | `el-alert` | `role="alert"` |
| `badge/*`（9 处） | `el-tag` | 主题标签、类型标签 |
| `progress` | `el-progress` | 采集运行进度（保留"不表示数据覆盖率"说明） |
| `skeleton/*`（3 处） | `el-skeleton` / 自有 `LoadingSkeleton` | 骨架微光 |
| `table/*` | `el-table` | 仅经 `DataTable` 使用 |
| `pagination/Pagination` | `el-pagination` | zh-cn 语言包提供 `上一页`/`下一页` 可访问名 |
| `scroll-area`、`separator`、`avatar`、`breadcrumb`、`label`、`form/*`、`collapsible`、`sonner/*` | 原生语义与 Tailwind / Element Plus Avatar、Breadcrumb、Message / 业务 FormField 与既有 details | `form/*` 保留 vee-validate 为唯一校验源 |

## 5. 测试迁移与补充

### 5.1 保留并适配（不删除有效断言）

| 测试 | 适配点 |
| --- | --- |
| `services/api.test.ts` | `vi.stubGlobal('fetch')` → 注入 Axios adapter；断言 URL、CSRF 头、`withCredentials`、204、Blob、超时、401/403/409 全部保留 |
| `services/health.test.ts` | 换 adapter；**保留 503 仍解析 `DOWN`** 与无效格式判定 |
| `services/business.test.ts`、`services/session.test.ts` | 会话改由 store 承载，断言语义保留 |
| `router/index.test.ts` | 增加 `createPinia()` + `setActivePinia`；4 个守卫断言保留 |
| `components/business/*.test.ts`（6 个）、`views/LoginView.test.ts`、`views/LogsView.test.ts` | 定位器随组件结构更新；`utils/*.test.ts`（5 个）不受影响 |
| E2E 11 个 spec / 26 个测试 | 仅更新因组件结构变化而失效的定位器与交互方式（如原生 `<select>.selectOption` → `el-select` 点击选择）；断言强度不降低 |

### 5.2 新增（覆盖迁移风险）

| 主题 | 用例 |
| --- | --- |
| CSRF | 写请求先取 token 并使用服务端 `headerName`；缓存复用；403 写请求后清缓存；CSRF 请求失败不递归重试 |
| 并发会话 | 多个并发 `ensureSession` 只发一次 `/auth/me`；并发 401 只触发一次登出/跳转 |
| 401/403/409 | 文案与状态码映射；409 保留表单输入 |
| 超时 | 12s 总预算（含 CSRF 段）→ `REQUEST_TIMEOUT` |
| 取消 | 调用方已取消的信号立即拒绝且不提示失败；`REQUEST_CANCELLED` 与 `NETWORK_ERROR` 区分 |
| Blob | 成功返回 `Blob`；下载失败时解析 `problem+json` 而非返回错误 Blob |
| 健康检查降级 | 503 + `{"status":"DOWN"}` 正常返回；非健康检查路径的 503 仍抛错 |
| 分页转换 | UI 1 基 ↔ 后端 0 基双向转换；越界夹取；`显示 X–Y，共 Z 条` 边界 |
| 日期序列化 | `YYYY-MM-DD`、`datetime` 本地时间 → ISO UTC；缺失值不显示 1970 |
| 表单校验 | Zod schema 为唯一校验源；密码 ≥12 位；姓名必填 |
| 卸载清理 | 定时器、`AbortController`、`ResizeObserver`、rAF、Cytoscape/ECharts 实例在 `onBeforeUnmount` 释放 |
| 权限隔离 | 无权限页面不渲染入口；守卫跳转 `/forbidden`；store 不持久化权限快照 |
| 状态隔离 | 注销后与用户关联的数据/轮询被清理；迟到响应不恢复失效状态 |

## 6. 风险与对策

| 风险 | 影响 | 对策 |
| --- | --- | --- |
| Element Plus 与 Tailwind preflight 冲突（按钮背景、边框色、`svg` 显示、列表样式） | 视觉错乱 | 保留 Tailwind 作为布局工具；在 `element-plus.css` 中用变量对齐，必要时对 Element Plus 容器做**局部**样式修正；不使用全局 `!important` |
| 挂载到 `body` 的浮层（下拉、日期面板、消息、弹窗）在深色主题下取不到变量或层级过低 | 深色下白块、被顶栏遮挡 | 通过 `html.dark` 覆盖 `--el-*`；核对 `z-index`（顶栏 30，浮层 ≥2000）；实测截图验证 |
| `el-select`/`el-date-picker` 的可访问名与原生控件不同 | E2E 定位失败 | 保留 `FilterField` 的 `<label>` 包裹形成隐式关联；必要处显式 `aria-label`；E2E 只更新交互方式，不降低断言强度 |
| 原生 `<select>.selectOption` 在 `el-select` 上不可用 | `account-management` 日志筛选用例失败 | 改为点击展开 + 选择 `role="option"`；仍断言筛选后 `显示 1–1，共 1 条` |
| `el-pagination` 的总数文案与现有 `显示 X–Y，共 Z 条` 不同 | 断言失败 | DataTable 保留自有区间文案，`el-pagination` 只负责翻页控件 |
| Element Plus 全量引入导致体积上升 | 首屏变慢 | 采用具名按需 import；实测构建体积并与基线对比；不通过提高 `chunkSizeWarningLimit` 掩盖问题 |
| 12 秒预算被 CSRF 段消耗后业务段立即超时 | 写操作偶发失败 | 用统一 deadline 计算剩余预算，CSRF 与业务请求共享同一截止时间 |
| Pinia 与路由初始化顺序 | 首屏白屏或守卫抛错 | `main.ts` 中先 `app.use(pinia)` 再 `app.use(router)`；守卫内调用 `useSessionStore()`；`router/index.ts` 模块顶层不访问 store |
| 循环依赖（router ↔ store ↔ service） | 打包警告或运行时 undefined | `api.ts` 仅暴露 `setUnauthorizedHandler` 回调；store 不 import router |
| 图表/图谱实例进入持久化 store | 内存泄漏、SSR/测试报错 | 图实例、DOM、`AbortController`、定时器句柄只保存在组件/composable 作用域，不入 store |
| 未跟踪的用户工作被覆盖 | 用户数据丢失 | 不使用 `git checkout/reset/clean`；所有改动均为增量编辑；未跟踪文件（`GraphPathView.vue`、`GraphQueriesView.vue`、`GraphWorkspace.vue`、`GraphPathForm.vue`、`EntitySuggestInput.vue`、`YearPicker.vue`、`filter-options.ts`、`images/`、`stop.bat`、`tools/development/Stop-All.ps1`、`.agents/`）保留原有工作并增量迁移；`images/` 与启动/停止脚本未修改 |

## 7. 分批执行与验收关卡

| 批次 | 内容 | 验收关卡 | 状态 |
| --- | --- | --- | --- |
| A | 现状清单、测试基线、`DESIGN.md` | 单元 43/43、构建成功、E2E 26/26 基线记录在案 | ✅ |
| B | Axios 迁移 + 请求契约测试 | `npm run test` 全绿（含新增契约用例）；`npm run build` 成功 | ✅ |
| C | Pinia 迁移（会话、偏好）+ 路由守卫 + 状态隔离测试 | 单元全绿；`stage6` E2E（登录/403/会话过期）通过 | ✅ |
| D | Element Plus 主题变量、应用布局、基础业务组件（DataTable/FilterBar/PageHeader/StatusPill/弹窗/消息等） | 单元全绿；`optimization-navigation` E2E 通过；构建成功 | ✅ |
| E | 代表页面：工作台、成果目录、图谱 | 真实浏览器截图（1440×900、1920×1080、390 窄屏；浅色/深色/减少动效）；`stage7-export`、`stage7-graph` E2E 通过 | ✅ |
| F | 按清单完成其余 16 个页面 | 全部 E2E 26+ 用例通过；逐路由真实浏览器检查 | ✅ |
| G | 清理旧体系与依赖、全量回归、文档同步、最终审查 | 引用核查为 0 后移除依赖；`test`+`build`+`test:e2e` 全绿；构建体积与基线对比记录；README/系统设计/开发交接/已知限制同步 | ✅ |

代表页面是内部验证关卡，不是任务终点。

## 8. 验证边界

- E2E 全部基于 `page.route` 模拟接口，**不构成真实后端联合验收**；本轮不启动后端、不修改数据库与部署设施。
- 项目未定义 lint 脚本，本轮不运行、也不声称运行过 lint。
- 真实浏览器视觉检查使用 Playwright（msedge 通道）截图与本地 dev server，不代表生产部署环境。

## 9. 清理与视觉证据

旧组件清理前，`rg` 已确认 `frontend/src`（排除旧 UI 子树）及 `frontend/e2e` 没有引用旧 UI；
`git status --short -- frontend/src/components/ui` 为空。随后验证每个文件均位于工作区、已被 Git 跟踪且未修改，逐文件删除 134 个文件，未递归删除目录。
额外移除 `types/tanstack.d.ts` 与 Vite 测试预构建中的旧模块。`npm uninstall` 移除 17 个含传递关系的包；保留包的版本与 HEAD 锁文件逐项比对没有变化。
核查清单保留于 `.local/ui-removal-audit.json`；正式交付的逐文件列表见本文件附录。

图形截图使用真实 Microsoft Edge，当前截图数据由 `migration-visual.spec.ts` 的本机合成接口提供。
历史截图来自用户既有 `images/verify/`，当时环境未重新验证；用于比较信息层级和控件，不比较业务数值或声称同数据 A/B。

| 页面 | 迁移前（历史截图） | 迁移后（合成接口） |
| --- | --- | --- |
| 工作台浅色 | [迁移前](./frontend-migration-evidence/before-overview-light.png) | [迁移后](./frontend-migration-evidence/after-overview-light.png) |
| 成果目录深色 | [迁移前](./frontend-migration-evidence/before-catalog-dark.png) | [迁移后](./frontend-migration-evidence/after-catalog-dark.png) |
| 图谱深色 | [迁移前](./frontend-migration-evidence/before-graph-dark.png) | [迁移后](./frontend-migration-evidence/after-graph-dark.png) |

额外保存 [图谱窄屏](./frontend-migration-evidence/after-graph-light-390.png)、[图谱详情抽屉](./frontend-migration-evidence/after-graph-drawer-dark-390.png) 与 [数据源表单错误](./frontend-migration-evidence/after-source-validation-390.png)。
全部路由截图保留在 `.local/migration-visual/all/`，该目录为忽略的本机证据；公开随源码交付的代表截图位于本节链接目录。

视觉复核发现并修复：长表单错误在窄屏滚动后不可见、运维隐藏标签表格触发 ResizeObserver 循环、桌面移动按钮的 CSS 层叠冲突、窄屏图谱工具栏挤压导致 traceId 竖排、窄屏面包屑多行挤压、图谱详情抽屉、命令面板首次聚焦、数据源保存后的焦点恢复。
正文、表格、错误和按钮在双主题下可读，窄屏表格使用内部滚动；图谱与统计提供同源表格或文字视图。自动检查不替代读屏软件或 WCAG 全项认证。

## 10. 最终验证与交付记录

所有命令从仓库根目录执行；当前结果取自最后一轮执行日志，历史基线没有重新伪造。

| 命令 | 实际结果 |
| --- | --- |
| `npm --prefix .\frontend run test` | 22 个文件、89 个测试通过 |
| `npm --prefix .\frontend run build` | vue-tsc 与 Vite 构建成功，保留 ECharts 体积告警 |
| `npm --prefix .\frontend run test:e2e` | 44 项通过；Microsoft Edge，单 worker，本机模拟接口 |
| `npm --prefix .\frontend run test:e2e -- stage7-operations.spec.ts --repeat-each=3` | 3 次通过，验证隐藏表格卸载后无运行错误 |
| `npm --prefix .\frontend run test:e2e -- migration-visual.spec.ts --grep '数据源表单'` | 1 项通过，窄屏提交错误位于视口内，409 保留输入且成功后恢复焦点 |
| `npm --prefix .\frontend ls --depth=0` | 依赖树正常，无无效依赖 |
| `git -c core.safecrlf=false diff --check` | 无空白错误（命令参数仅控制检查输出，未修改 Git 配置） |
| `node .local/verify-migration-delivery.mjs` | 本机交付核查通过：94 个变更文本为 UTF-8 无 BOM、46 个本地文档链接有效、132 张全路由截图与 12 张归档截图齐全、287 个保留包版本未变 |

最新日志位于忽略的本机目录：`.local/migration-final-unit.log`、`.local/migration-final-build.log`、`.local/migration-final-e2e.log`。
初次运行的沙箱 `spawn EPERM` 经正常本机权限复跑解决；测试定位器、隐藏表格尺寸循环与长表单错误可见性问题已修复并复验。

全路由矩阵覆盖 19 类路由、22 个具体地址，1440×900、1920×1080 与 390px 窄屏、浅深两主题，共 132 张页面截图。业务窄屏高度 844px，登录窄屏高度 900px。
检查包含初始重复请求、文档横向溢出、页面错误事件、控制台错误、主题切换、减少动效、关键弹窗与键盘焦点；不把无控制台错误当作所有业务或无障碍场景的证明。

### 10.1 构建体积

| 类型 | 历史基线（B） | 当前（B） | 差异 |
| --- | ---: | ---: | ---: |
| 全部产物 | 1988140 | 2565004 | +576864（+29.02%） |
| JavaScript | 1886749 | 2133945 | +247196（+13.10%） |
| CSS | 99194 | 428773 | +329579（+332.26%） |

当前 76 个产物文件；仅 ChartFrame/ECharts chunk 超过默认 500 kB（636665 B）。Element Plus 默认与深色样式是 CSS 增长的主要来源；组件使用具名导入和现有路由懒加载，未提高告警阈值。当前记录是原始文件体积比较，不是首屏网络耗时或性能达标结论。

### 10.2 项目记忆同步

读取并核对 README、系统设计、开发交接、已知限制、授权矩阵、OpenAPI、DESIGN 和本清单；没有发现独立 MEMORY 文件，也没有新建其他记忆体系。
已同步 README、`docs/system-design.md`、`docs/development-handoff.md`、`docs/known-limitations.md`、DESIGN 与本清单，记录当前组件栈、唯一会话来源、请求总期限、取消边界、账号隔离、主题层叠、日期和弹窗/表格生命周期。
通过包清单、源代码、单元测试及真实浏览器复验，纠正旧 Reka/TanStack、fetch/reactive 会话和样式层叠描述；阶段6/7原有历史记录明确标为历史，未冒充当前验证。授权矩阵与 OpenAPI 契约未改变，因此无需修改。

后端、数据库、部署设施、原始 `images/`、已有停止脚本与其他用户文件未修改；未创建提交、分支、合并或发布。

## 附录：实际变更文件

以下按最终 Git 工作区状态列出本轮迁移相关文件，包含接续前已有的迁移工作；README 与开发交接保留用户已有停止说明。

| 文件 | 变更 | 用途 |
| --- | --- | --- |
| `README.md` | 修改 | 同步设计、交付证据或当前前端架构说明 |
| `docs/development-handoff.md` | 修改 | 同步设计、交付证据或当前前端架构说明 |
| `docs/known-limitations.md` | 修改 | 同步设计、交付证据或当前前端架构说明 |
| `docs/system-design.md` | 修改 | 同步设计、交付证据或当前前端架构说明 |
| `frontend/e2e/account-management.spec.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/e2e/stage7-analytics.spec.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/e2e/stage7-export.spec.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/e2e/stage7-graph.spec.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/e2e/stage7-operations.spec.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/package-lock.json` | 修改 | 新增目标依赖并移除无引用旧依赖 |
| `frontend/package.json` | 修改 | 新增目标依赖并移除无引用旧依赖 |
| `frontend/src/App.vue` | 修改 | 接入统一布局、导航或应用初始化与测试依赖 |
| `frontend/src/components/business/AppSidebar.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/AppTopbar.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/AuditLogTable.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/Breadcrumb.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/CommandPalette.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/ConfirmDialog.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/DataTable.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/EmptyState.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/ErrorState.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/FilterBar.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/GraphEntityPicker.test.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/components/business/GraphEntityPicker.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/GraphSavedQueries.test.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/components/business/GraphSavedQueries.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/LoadingSkeleton.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/PageHeader.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/PanelSection.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/ScholarlySourcePanel.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/StatCard.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/ThemeToggle.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/UserMenu.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/UserProfileFields.vue` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/index.ts` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/types.ts` | 修改 | 迁移复用业务组件及其类型 |
| `frontend/src/components/ui/alert-dialog/AlertDialog.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert-dialog/AlertDialogAction.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert-dialog/AlertDialogCancel.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert-dialog/AlertDialogContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert-dialog/AlertDialogDescription.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert-dialog/AlertDialogFooter.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert-dialog/AlertDialogHeader.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert-dialog/AlertDialogTitle.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert-dialog/AlertDialogTrigger.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert-dialog/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert/Alert.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert/AlertDescription.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert/AlertTitle.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/alert/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/avatar/Avatar.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/avatar/AvatarFallback.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/avatar/AvatarImage.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/avatar/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/badge/Badge.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/badge/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/breadcrumb/Breadcrumb.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/breadcrumb/BreadcrumbItem.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/breadcrumb/BreadcrumbLink.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/breadcrumb/BreadcrumbList.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/breadcrumb/BreadcrumbPage.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/breadcrumb/BreadcrumbSeparator.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/breadcrumb/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/button/Button.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/button/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/button/variants.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/card/Card.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/card/CardAction.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/card/CardContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/card/CardDescription.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/card/CardFooter.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/card/CardHeader.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/card/CardTitle.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/card/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/checkbox/Checkbox.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/checkbox/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/collapsible/Collapsible.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/collapsible/CollapsibleContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/collapsible/CollapsibleTrigger.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/collapsible/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dialog/Dialog.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dialog/DialogClose.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dialog/DialogContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dialog/DialogDescription.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dialog/DialogFooter.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dialog/DialogHeader.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dialog/DialogTitle.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dialog/DialogTrigger.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dialog/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dropdown-menu/DropdownMenu.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dropdown-menu/DropdownMenuContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dropdown-menu/DropdownMenuGroup.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dropdown-menu/DropdownMenuItem.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dropdown-menu/DropdownMenuLabel.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dropdown-menu/DropdownMenuSeparator.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dropdown-menu/DropdownMenuShortcut.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dropdown-menu/DropdownMenuTrigger.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/dropdown-menu/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/form/FormDescription.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/form/FormItem.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/form/FormLabel.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/form/FormMessage.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/form/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/input/Input.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/input/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/label/Label.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/label/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/pagination/Pagination.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/pagination/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/popover/Popover.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/popover/PopoverContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/popover/PopoverTrigger.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/popover/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/progress/Progress.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/progress/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/radio-group/RadioGroup.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/radio-group/RadioGroupItem.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/radio-group/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/scroll-area/ScrollArea.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/scroll-area/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/select/Select.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/select/SelectContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/select/SelectGroup.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/select/SelectItem.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/select/SelectLabel.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/select/SelectSeparator.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/select/SelectTrigger.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/select/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/separator/Separator.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/separator/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sheet/Sheet.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sheet/SheetClose.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sheet/SheetContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sheet/SheetDescription.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sheet/SheetFooter.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sheet/SheetHeader.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sheet/SheetTitle.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sheet/SheetTrigger.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sheet/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/skeleton/Skeleton.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/skeleton/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sonner/Sonner.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/sonner/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/switch/Switch.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/switch/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/table/Table.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/table/TableBody.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/table/TableCaption.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/table/TableCell.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/table/TableHead.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/table/TableHeader.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/table/TableRow.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/table/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tabs/Tabs.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tabs/TabsContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tabs/TabsList.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tabs/TabsTrigger.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tabs/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/textarea/Textarea.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/textarea/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/toggle-group/ToggleGroup.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/toggle-group/ToggleGroupItem.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/toggle-group/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/toggle/Toggle.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/toggle/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tooltip/Tooltip.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tooltip/TooltipContent.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tooltip/TooltipProvider.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tooltip/TooltipTrigger.vue` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/components/ui/tooltip/index.ts` | 删除 | 引用核查完成后移除旧基础组件 |
| `frontend/src/composables/useChartTheme.ts` | 修改 | 统一主题与资源清理生命周期 |
| `frontend/src/composables/useTheme.ts` | 修改 | 统一主题与资源清理生命周期 |
| `frontend/src/config/nav.ts` | 修改 | 接入统一布局、导航或应用初始化与测试依赖 |
| `frontend/src/layouts/BusinessLayout.vue` | 修改 | 接入统一布局、导航或应用初始化与测试依赖 |
| `frontend/src/main.ts` | 修改 | 接入统一布局、导航或应用初始化与测试依赖 |
| `frontend/src/router/index.test.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/router/index.ts` | 修改 | 接入统一布局、导航或应用初始化与测试依赖 |
| `frontend/src/services/api.test.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/services/api.ts` | 修改 | 统一 HTTP、取消与会话清理边界 |
| `frontend/src/services/business.test.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/services/health.test.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/services/health.ts` | 修改 | 统一 HTTP、取消与会话清理边界 |
| `frontend/src/services/session.test.ts` | 删除 | 会话测试迁至 Pinia store 并扩充竞争场景 |
| `frontend/src/services/session.ts` | 删除 | 以 Pinia 唯一会话源替换旧响应式模块 |
| `frontend/src/styles/index.css` | 修改 | 统一组件令牌、主题和响应式层叠 |
| `frontend/src/types/tanstack.d.ts` | 删除 | 移除失去用途的 TanStack 类型扩展 |
| `frontend/src/utils/graph-query.ts` | 修改 | 明确日期、筛选或跨路由查询数据边界 |
| `frontend/src/views/AchievementDetailView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/AnalyticsView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/CatalogEntitiesView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/CatalogView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/CrawlTasksView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/ForbiddenView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/GovernanceView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/GraphView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/LoginView.test.ts` | 修改 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/views/LoginView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/LogsView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/NotFoundView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/OperationsView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/OverviewView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/QualityView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/SessionExpiredView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/SourcesView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/src/views/UsersView.vue` | 修改 | 迁移页面控件、状态与交互 |
| `frontend/vite.config.ts` | 修改 | 接入统一布局、导航或应用初始化与测试依赖 |
| `DESIGN.md` | 新增 | 同步设计、交付证据或当前前端架构说明 |
| `docs/frontend-migration-evidence/after-catalog-dark.png` | 新增 | 归档迁移后真实浏览器代表截图 |
| `docs/frontend-migration-evidence/after-catalog-light.png` | 新增 | 归档迁移后真实浏览器代表截图 |
| `docs/frontend-migration-evidence/after-graph-dark.png` | 新增 | 归档迁移后真实浏览器代表截图 |
| `docs/frontend-migration-evidence/after-graph-drawer-dark-390.png` | 新增 | 归档迁移后真实浏览器代表截图 |
| `docs/frontend-migration-evidence/after-graph-light-390.png` | 新增 | 归档迁移后真实浏览器代表截图 |
| `docs/frontend-migration-evidence/after-graph-light.png` | 新增 | 归档迁移后真实浏览器代表截图 |
| `docs/frontend-migration-evidence/after-overview-dark.png` | 新增 | 归档迁移后真实浏览器代表截图 |
| `docs/frontend-migration-evidence/after-overview-light.png` | 新增 | 归档迁移后真实浏览器代表截图 |
| `docs/frontend-migration-evidence/after-source-validation-390.png` | 新增 | 归档迁移后真实浏览器代表截图 |
| `docs/frontend-migration-evidence/before-catalog-dark.png` | 新增 | 归档已有迁移前历史截图副本 |
| `docs/frontend-migration-evidence/before-graph-dark.png` | 新增 | 归档已有迁移前历史截图副本 |
| `docs/frontend-migration-evidence/before-overview-light.png` | 新增 | 归档已有迁移前历史截图副本 |
| `docs/frontend-migration-inventory.md` | 新增 | 同步设计、交付证据或当前前端架构说明 |
| `frontend/e2e/migration-visual.spec.ts` | 新增 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/components/business/DataTable.test.ts` | 新增 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/components/business/EntitySuggestInput.test.ts` | 新增 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/components/business/EntitySuggestInput.vue` | 新增 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/FormField.vue` | 新增 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/GraphPathForm.vue` | 新增 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/GraphWorkspace.vue` | 新增 | 迁移复用业务组件及其类型 |
| `frontend/src/components/business/YearPicker.vue` | 新增 | 迁移复用业务组件及其类型 |
| `frontend/src/composables/useSessionCleanup.ts` | 新增 | 统一主题与资源清理生命周期 |
| `frontend/src/services/http.ts` | 新增 | 统一 HTTP、取消与会话清理边界 |
| `frontend/src/services/session-scope.ts` | 新增 | 统一 HTTP、取消与会话清理边界 |
| `frontend/src/stores/graph.test.ts` | 新增 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/stores/graph.ts` | 新增 | 承载会话、偏好或图谱共享状态 |
| `frontend/src/stores/preferences.ts` | 新增 | 承载会话、偏好或图谱共享状态 |
| `frontend/src/stores/session.test.ts` | 新增 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/stores/session.ts` | 新增 | 承载会话、偏好或图谱共享状态 |
| `frontend/src/styles/element-plus.css` | 新增 | 统一组件令牌、主题和响应式层叠 |
| `frontend/src/test/http-stub.ts` | 新增 | 提供 Axios adapter 测试夹具 |
| `frontend/src/utils/date.test.ts` | 新增 | 验证迁移契约、组件交互或浏览器流程 |
| `frontend/src/utils/date.ts` | 新增 | 明确日期、筛选或跨路由查询数据边界 |
| `frontend/src/utils/filter-options.ts` | 新增 | 明确日期、筛选或跨路由查询数据边界 |
| `frontend/src/views/GraphPathView.vue` | 新增 | 迁移页面控件、状态与交互 |
| `frontend/src/views/GraphQueriesView.vue` | 新增 | 迁移页面控件、状态与交互 |
