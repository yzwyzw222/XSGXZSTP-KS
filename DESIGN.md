# AACV System 前端设计规范（DESIGN.md）

本文件是 AACV 学术成果爬虫及可视化系统前端的**唯一设计规范来源**，与 `frontend/src/styles/tokens.css`
中的设计令牌一一对应。修改视觉表现时必须先更新本文件，再落地到令牌与组件。

- 适用技术栈：Vue 3 + TypeScript + Vite + Vue Router + Pinia + Axios + Element Plus；
  Tailwind CSS 仅作布局工具，VueUse、Lucide、ECharts、Cytoscape、Zod、vee-validate 继续保留；图谱概览使用 vis-network / vis-data。
- 目标产品形态：面向中文学术数据运营的**业务后台**，不是营销站点。

## 1. 设计参考与取舍

### 1.1 参考来源

下表保留首次迁移时的来源查阅记录。本轮升级依据本地令牌与组件核对，未重新查阅这些外部来源。

| 参考 | 版本/引用 | 实际读取情况 |
| --- | --- | --- |
| `awesome-design-md` 仓库索引 | https://github.com/VoltAgent/awesome-design-md | 已读取仓库结构 |
| Linear 设计分析文档 | https://github.com/VoltAgent/awesome-design-md/blob/main/design-md/linear.app/DESIGN.md（front-matter `version: alpha`，`name: Linear-design-analysis`） | 已逐节读取原始 Markdown（raw.githubusercontent.com 同源内容，549 行） |

### 1.2 从 Linear 文档中**实际采用**的规则

以下条目均来自该文档正文，不是从项目名称或截图推断：

1. **单一强调色，克制使用**（"Key Characteristics" / "Do"）：
   `colors.primary` 只用于品牌标识、主操作按钮、焦点环和链接强调；
   "Treat lavender as scarce"。→ AACV 采用单一学术蓝 `--primary`，禁止把它当作区块背景或卡片填充。
2. **表面阶梯承载层级，而不是阴影**（"Elevation & Depth"）：
   Linear 用 canvas → surface-1 → surface-2 → surface-3 四级表面加 hairline 边框表达层级，
   深色界面主要靠表面色阶和细边框表达深度，减少投影。
   → AACV 采用 `--background` / `--card` / `--muted` / `--popover` 四级表面 + 1px `--border` 细边框，
   公共卡片与面板不叠加常驻阴影（`--card-shadow: none`），阴影保留给浮层表达遮挡关系。
3. **细边框（hairline）优先**：`colors.hairline`(#23252a) / `hairline-strong` / `hairline-tertiary`
   三级边框。→ AACV 对应 `--border` / `--border-strong` / `--table-border`，统一 1px，不使用 2px 装饰边框。
4. **圆角尺度**（"Border Radius Scale"）：`xs 4px`（小标签）/`sm 6px`（行内标签）/`md 8px`（**所有按钮与表单输入**）
   /`lg 12px`（卡片）/`xl 16px`（大面板）/`pill 9999px`（仅状态药丸与分段切换）。
   并明确 "Don't pill-round CTAs"。→ AACV 令牌 `--radius-xs/sm/md/lg/xl/2xl/full` 与之对齐：
  按钮与输入 `--radius-sm`(6px)，卡片 `--radius-lg`(10px)，弹窗 `--radius-xl`(14px)，状态标签 `--radius-xs`(4px)。
5. **4px 基准间距**（"Spacing System"，`base unit: 4px`）与具体取值
   `xxs 4 / xs 8 / sm 12 / md 16 / lg 24 / xl 32 / xxl 48`；
   卡片内边距 24px，表单输入内边距 `8px 12px`，按钮内边距 `8px 14px`。
   → AACV 采用 `--space-1..--space-16`（4px 基准），卡片内边距 `--card-padding: 20px`（较 Linear 24px 收紧，见 1.3）。
6. **字重纪律**（"Principles"）：Display 600、Body 400，"Linear resists 700+ display weights"。
   → AACV 标题最大 `--font-semibold`(600)，正文 `--font-normal`(400)，仅数字指标可用 700。
7. **标题使用负字距，分类引导词使用正字距**：display `-3.0px@80px … -0.6px@28px`，
   eyebrow `+0.4px`。→ AACV `--tracking-tight: -0.015em` 用于 h1–h3；
   本轮只沿用标题负字距，不从该参考新增装饰性引导标题。
8. **响应式断点与折叠策略**（"Responsive Behavior"）：1440 默认桌面、1280 三列、1024 三列→两列、
   768 导航折叠为汉堡、480 单列；触摸目标 ≥40px（CTA）、≥44px（表单输入）。
   → AACV 断点沿用 Tailwind `sm 640 / md 768 / lg 1024 / xl 1280 / 2xl 1536`，
   在 `lg` 以下把固定侧栏换成抽屉；窄屏 390px 为验收视口。
9. **等宽字体只用于代码/标识上下文**（"mono only in code contexts"）。
   → AACV 的 `.mono-evidence` 只用于 DOI、ORCID、ROR、ISSN、traceId、任务 ID、JSON 证据。

### 1.3 明确**不采用**的部分及原因

| Linear 文档条目 | 不采用原因 |
| --- | --- |
| `canvas: #010102` 近纯黑营销画布；"Don't ship a light-mode marketing page" | AACV 是业务后台，必须提供浅色/深色/跟随系统三态；Linear 营销站没有浅色规范（文档 "Known Gaps" 自述） |
| `display-xl 80px`、`display-lg 56px`、`display-md 40px` 超大标题 | 营销 Hero 尺度。业务页面最大标题为 `--font-size-2xl`(24px) |
| `pricing-card` / `pricing-card-featured` / `cta-banner` / `testimonial-card` / `customer-logo-tile` | 定价卡、宣传横幅、客户证言、Logo 墙均不适用于业务页面 |
| `product-screenshot-card`（"Product UI screenshots dominate the page"） | 营销页把产品截图当主角；业务页面本身即产品，不需要截图装饰 |
| `top-nav height 56px` + 居中营销导航 | AACV 采用固定侧栏 + 顶栏（`--topbar-height: 60px`），承载面包屑、命令面板、主题与账号 |
| `section 96px` 区块间距 | 营销纵向节奏；业务页面区块间距为 `--space-5`(20px)～`--space-6`(24px) |
| 品牌色 `#5e6ad2` 薰衣草蓝、Linear Display/Text/Mono 专有字体 | 不引入未经许可的品牌字体与 Logo；AACV 使用学术蓝 `hsl(214 68% 43%)` 与本地中文字体回退 |
| `rounded.xxl 24px`、`pill` 用于 CTA | 业务后台不使用超大圆角与药丸按钮 |
| 卡片内边距 24px / 32px / 48px | 业务信息密度更高，统一收紧为 16–20px |

### 1.4 针对 AACV（中文学术数据系统）的调整

1. **信息密度优先**：基准字号 `--font-size-base: 14px`、正文 `--font-size-md: 14px`；
   表格单元格垂直内边距 12px，密集模式为 6px；横向留白由 `ElTable` 列规格承接。学术数据页面单屏需承载筛选区 + 结果表 + 分页。
2. **多语义色而非单色**：Linear 营销站只有一个语义色（success）。AACV 必须区分
   运行中/成功/警告/失败/降级/停服/未知等采集与治理状态，因此保留
   `--success` `--warning` `--destructive` `--info` 四组语义色，并把图谱节点色
   （成果/作者/机构/期刊/主题）与图表色板 `--chart-1..6` 独立成组。
   **强调色纪律仍然保留**：`--primary` 不用于大面积填充，语义色只用于状态标识与其 12% 透明底。
3. **中文字体栈**：不引入品牌字体，按可用本地字体回退
   `'Segoe UI Variable', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei UI', 'Microsoft YaHei', system-ui, sans-serif`；
   等宽 `'JetBrains Mono', 'Cascadia Code', Consolas, 'Courier New', monospace`。
4. **浅色工作主题适合长时间阅读**：长时间阅读中文密集表格，浅色 `--background: hsl(216 25% 97%)`
   比纯白更降低眩光；深色用于运维值守与图谱浏览。

### 1.5 中文学术工作台升级（2026-09-07，Operate）

以现有学术蓝身份和业务任务为依据扩展设计系统。界面优先支持定位范围、读取结果、执行操作和核对证据；业务工作区不采用营销构图、装饰纹理、渐变、悬浮卡片或页面入场编排。登录入口的静态学术主题点缀按 §1.6 单独约束。

- **表面与对比**：浅色采用冷灰纸面、白色工作表面；深色采用低饱和蓝灰画布和逐级提亮的表面。学术蓝调整为 `214 68% 43%`（浅色主色）和 `212 76% 70%`（深色主色）。次要文本及状态文本必须满足 4.5:1；图形分类色独立于状态文本色。
- **信息构图**：工作台采用独立纵向排列的主区与侧区；主区依次为成果趋势、并排的学者合作网络与研究领域分布、系统活动日志，侧区为合作排行与采集摘要。右侧列表高度不撑开主区模块间距，统一间隔 16px；1280px 以下按主区、侧区的文档顺序单列排列。统计先展示总量与趋势，再展示字段覆盖、分布和合作。目录把编目入口、筛选和结果放在同一个工作区域；详情采用正文与来源证据分栏；图谱让查询、画布和检查器紧邻；采集运行查询收为紧凑工具条。窄屏按自然阅读顺序单列排列。
- **表面纪律**：公共面板只用一层细边框，不同时叠加常驻阴影。面板内的表格不再包第二层圆角边框；成组指标以分隔线形成连续指标带，不使用彩色侧边条。浮层保留阴影表达层级。
- **中文排版**：页面标题 24px/600，正文与表格 14px，说明与标签 12–13px；中文标题字距为 `-0.015em`。数字统一等宽并保留真实终值，标识才使用等宽字体。成果题名不以省略号替代正文，段落按阅读宽度分组。
- **外壳**：桌面侧栏保留 240px/64px，顶栏收为 60px，内容最大宽度 1600px；移动端留白 16px。折叠时一次提交布局，用可中断的 FLIP 位移说明边界变化，不逐帧改变宽度反复触发表格与图表测量。
- **层级令牌**：`--z-topbar: 30`、`--z-sidebar: 40`、`--z-skip-link: 50`、`--z-overlay: 2000`，浮层内部仍由 Element Plus 管理。
- **状态真实性**：只在实际轮询进行时表达运行反馈；一次读取的合作和审计快照不标注“实时”。首次加载呈现匹配布局的骨架，刷新保留旧结果并明确“正在更新”；失败保留原因，不以空态或零值替代。筛选控件快速变化时最后一次操作必须生效。

### 1.6 登录入口布局与焦点（2026-09-07）

- 登录主面板最大宽度 1360px，桌面双栏最小高度 640px；品牌说明和表单按接近等宽分栏，内边距随视口在 32–64px 间调整。登录入口的品牌标题可使用已有 29–36px 令牌，登录标题为 24px，输入与主按钮高度 52px；这些尺寸仅用于登录页。
- 周边空白以成果、作者、机构、期刊与主题的静态关系线图点缀，使用既有主题令牌，不加载图谱引擎、不请求业务数据、不展示虚构统计或运行状态。装饰标记 `aria-hidden` 且不接收指针事件；1024px 以下收起关系图及品牌说明，保留紧凑品牌标识和单列表单。
- 登录页输入框只由 Element Plus 外层 wrapper 呈现边界。内部 input 不绘制额外 border、box-shadow 或 outline；键盘聚焦时将 wrapper 的单圈边框加粗为 2px，不叠加第二圈轮廓，保留清除、密码显隐、自动填充和原有登录校验。
- 登录布局和控件尺寸限定在 `LoginView.vue` 的局部样式；输入框的单层边界和焦点表现统一使用 `styles/element-plus.css` 的公共规则，浅色、深色和窄屏一致。

## 2. 主题行为

| 模式 | 触发 | 落地方式 |
| --- | --- | --- |
| 浅色 | 用户选择 `light`，或 `auto` 且系统为浅色 | `<html data-theme="light">` + 移除 `.dark` + `color-scheme: light` |
| 深色 | 用户选择 `dark`，或 `auto` 且系统为深色 | `<html data-theme="dark">` + `.dark` + `color-scheme: dark` |
| 跟随系统 | 默认值 `auto`，监听 `prefers-color-scheme` 变化实时切换 | 由 `useColorMode` 解析后写入上面两者之一 |

- 持久化键：`localStorage['aacv-theme']`，只保存模式字符串，不保存任何认证或权限信息。
- Element Plus 通过 `html.dark` 选择器切换暗色变量，与项目 `.dark` class 共用同一开关（见 §4）。
- 侧栏折叠状态持久化键：`localStorage['aacv-sidebar-collapsed']`。
- 应用启动时先同步 `data-theme`/`.dark` 再挂载，避免首屏主题闪烁。

## 3. 设计令牌

三层架构（Primitive → Semantic → Component），全部定义在 `frontend/src/styles/tokens.css`。

### 3.1 Primitive（原始值）

| 组 | 令牌 | 说明 |
| --- | --- | --- |
| 品牌蓝 | `--color-blue-50 … --color-blue-950` | 学术理性主色，`blue-600` 为浅色主色，`blue-400` 为深色主色 |
| 数据色 | `--color-cyan-*`（作者）、`--color-amber-*`（机构/警告）、`--color-violet-*`（期刊）、`--color-emerald-*`（主题/成功）、`--color-rose-*`（危险） | 图谱与图表语义 |
| 中性色 | `--color-slate-0 … --color-slate-950` | 表面阶梯与文本层级基底 |
| 圆角 | `--radius-xs 4px` / `sm 6px` / `md 8px` / `lg 10px` / `xl 14px` / `2xl 18px` / `full 9999px` | 对齐 Linear 圆角尺度并按业务收紧 |
| 间距 | `--space-1 4px` … `--space-16 64px`（4px 基准） | 对齐 Linear spacing |
| 字号 | `--font-size-2xs 10px` / `xs 11px` / `sm 12px` / `base 14px` / `md 14px` / `lg 16px` / `xl 19px` / `2xl 24px` / `3xl 29px` / `4xl 36px` | 独立命名空间，避免与 Tailwind `--text-*` 冲突；正文与表格统一 14px |
| 行高 | `--leading-tight 1.25` / `snug 1.4` / `normal 1.55` / `relaxed 1.7` | 中文正文用 `normal` 以上 |
| 字重 | `--font-normal 400` / `medium 500` / `semibold 600` / `bold 700` | 标题不超过 600 |
| 字距 | `--tracking-tight -0.015em` / `normal 0` / `wide 0.05em` / `wider 0.1em` | 中文标题克制收紧，不使用装饰性引导标题 |
| 阴影 | `--shadow-xs/sm/md/lg/xl` | 保留阴影尺度供浮层使用；公共卡片与面板不使用常驻阴影 |
| 动效曲线 | `--ease-standard cubic-bezier(.4,0,.2,1)` / `--ease-emphasized cubic-bezier(.16,1,.3,1)` / `--ease-decelerate cubic-bezier(0,0,0,1)` | — |
| 动效时长 | `--duration-fast 120ms` / `--duration-normal 200ms` / `--duration-slow 280ms` / `--duration-chart 320ms` | 见 §8 |
| 布局 | `--sidebar-width 240px` / `--sidebar-width-collapsed 64px` / `--topbar-height 60px` / `--content-max-width 1600px` | 内容宽度实际约束在 `.page-stack` |

### 3.2 Semantic（浅色 / 深色）

颜色令牌统一存储 **HSL 分量**（如 `214 68% 43%`），使用处写 `hsl(var(--x) / <alpha>)`，便于透明度组合。

| 语义 | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `--background` / `--foreground` | `slate-50` / `slate-900` | `slate-950` / `210 40% 96%` | 页面画布与主文本 |
| `--card` / `--card-foreground` | `slate-0` / `slate-900` | `slate-900` / 同上 | 一级表面（卡片、面板） |
| `--muted` / `--muted-foreground` | `slate-100` / `slate-500` | `slate-850` / `slate-400` | 二级表面、次要文本 |
| `--popover` / `--popover-foreground` | `slate-0` / `slate-900` | `slate-900` / 同上 | 三级表面（下拉、气泡、菜单） |
| `--primary`(+`-foreground`/`-hover`/`-active`) | `blue-600` | `blue-400` | 主操作、链接强调、焦点环 |
| `--secondary`(+`-foreground`) | `slate-100` / `slate-700` | `slate-800` / `slate-200` | 次操作 |
| `--accent`(+`-foreground`) | `blue-50` / `blue-700` | `213 60% 22%` / `blue-200` | 选中态、悬停态底色 |
| `--destructive`(+`-foreground`) | `354 67% 43%` | `rose-400` | 危险操作、错误；文本色与图形分类色独立 |
| `--success` / `--warning` / `--info` | `162 70% 28%` / `30 82% 31%` / `195 73% 31%` | `emerald-400` / `amber-400` / `cyan-400` | 状态文本语义，确保与浅色表面及状态底色的对比度 |
| `--success-subtle` / `--warning-subtle` | `160 72% 95%` / `40 96% 94%` | `160 45% 15%` / `38 45% 16%` | 成功与警告的辅助表面 |
| `--border` / `--border-strong` / `--input` / `--input-background` | `slate-200` / `slate-300` / `slate-200` / `slate-0` | `217 30% 24%` / `215 25% 33%` / `217 30% 26%` / `slate-925` | hairline 边框与输入表面 |
| `--ring` | `blue-400` | `blue-400` | 焦点环 |
| `--sidebar` / `--sidebar-foreground` / `--sidebar-accent`(+`-foreground`) / `--sidebar-border` / `--sidebar-active` | `216 24% 95%` / `slate-600` / `blue-50` / `blue-700` / `slate-200` / `blue-600` | `slate-925` / `slate-400` / `213 55% 20%` / `blue-200` / `217 30% 20%` / `blue-400` | 侧栏专用 |
| `--overlay` | `219 36% 11% / .45` | `222 47% 3% / .65` | 弹窗遮罩 |
| `--chart-1..6` / `--chart-grid` / `--chart-axis` / `--chart-tooltip-*` | `blue-500 emerald-400 amber-400 violet-400 cyan-400 rose-400` | 深色下整体提亮一档（`*-300`） | ECharts 色板 |
| `--graph-achievement/author/institution/venue/topic/edge/canvas` | 蓝/青/琥珀/紫/翠绿 + `slate-300` 边 + `slate-50` 画布 | 同色系提亮 | Cytoscape 节点与边 |
| `--table-header-bg` / `--table-header-fg` / `--table-row-hover` / `--table-border` | `slate-100` / `slate-500` / `blue-50` / `slate-200` | `slate-925` / `slate-400` / `213 50% 18%` / `217 30% 22%` | 表格 |
| `--status-running/idle/warning/error` | `emerald-500` / `slate-400` / `amber-500` / `rose-500` | `*-400` / `slate-500` | 状态点 |

### 3.3 Component（组件级）

| 令牌 | 值 | 说明 |
| --- | --- | --- |
| `--button-radius` | `--radius-sm` (6px) | 所有按钮，不做药丸 |
| `--button-height-sm/md/lg` | `32px / 36px / 44px` | `md` 为默认；1024px 以下统一覆盖为 `40px / 44px / 44px` |
| `--button-padding-x` | `--space-4` (16px) | 图标按钮为正方形 |
| `--button-font-weight` | `--font-medium` (500) | 对齐 Linear button 字重 |
| `--card-radius` / `--card-border` / `--card-shadow` / `--card-padding` | `--radius-lg` / `1px solid hsl(var(--border))` / `none` / `--space-5` | 卡片以表面阶梯与单层 hairline 表达层级 |
| `--input-radius` / `--input-height` / `--input-border` / `--input-bg` / `--input-placeholder` / `--input-focus-ring` | `--radius-sm` / `36px` / `1px solid hsl(var(--input))` / `hsl(var(--input-background))` / `hsl(var(--muted-foreground))` / `0 0 0 2px hsl(var(--ring)/.45)` | 表单输入 |
| `--table-radius` / `--table-cell-padding` / `--table-font-size` | `--radius-md` / `12px 16px` / `--font-size-base` | 表格 |
| `--dialog-radius` / `--dialog-border` / `--dialog-shadow` | `--radius-xl` / `1px solid hsl(var(--border))` / `--shadow-xl` | 弹窗与抽屉 |
| `--badge-radius` / `--badge-font-size` / `--badge-padding` | `--radius-full` / `--font-size-xs` / `2px 8px` | 基础徽标令牌；`StatusPill` 使用 §6.4 的 4px 状态标签规格 |
| `--nav-item-radius` / `--nav-item-height` / `--nav-item-padding` | `--radius-md` / `40px` / `8px 12px` | 侧栏条目，满足 ≥40px 触摸目标 |

## 4. Element Plus 变量与项目语义令牌的映射

Element Plus 使用 `--el-*` CSS 变量。项目**不复制色值**，而是把 `--el-*` 指向已有语义令牌，
保证"改一处令牌，两套体系同时生效"。映射集中在 `frontend/src/styles/element-plus.css`：

| Element Plus 变量 | 项目令牌 |
| --- | --- |
| `--el-color-primary`（及 `light-3/5/7/8/9`、`dark-2`） | 主色、`--primary-hover`、主色的 50%/30%/20%/10% 透明度、`--primary-active` |
| `--el-color-success` / `warning` / `danger` / `error` / `info` | `hsl(var(--success))` / `hsl(var(--warning))` / `hsl(var(--destructive))` / 同 danger / `hsl(var(--muted-foreground))` |
| `--el-bg-color` / `--el-bg-color-page` / `--el-bg-color-overlay` | `hsl(var(--card))` / `hsl(var(--background))` / `hsl(var(--popover))` |
| `--el-text-color-primary` / `regular` / `secondary` / `placeholder` / `disabled` | `hsl(var(--foreground))` / 同 / `hsl(var(--muted-foreground))` / 同 / `hsl(var(--muted-foreground) / .6)` |
| `--el-border-color` / `-light` / `-lighter` / `-dark` / `-darker` / `-extra-light` | `hsl(var(--border))` / 同 / `hsl(var(--border) / .7)` / `hsl(var(--border-strong))` / 同 / `hsl(var(--border) / .5)` |
| `--el-fill-color` / `-light` / `-lighter` / `-blank` / `-dark` / `-darker` | `hsl(var(--muted))` / `hsl(var(--muted) / .6)` / `hsl(var(--muted) / .3)` / `hsl(var(--input-background))` / `hsl(var(--accent))` / 同 |
| `--el-border-radius-base` / `-small` / `-round` / `-circle` | `var(--radius-sm)` / `var(--radius-xs)` / `var(--radius-full)` / `100%` |
| `--el-box-shadow` / `-light` / `-lighter` / `-dark` | `var(--shadow-lg)` / `var(--shadow-md)` / `var(--shadow-sm)` / `var(--shadow-xl)` |
| `--el-font-size-base` / `-extra-small` / `-small` / `-medium` / `-large` / `-extra-large` | `var(--font-size-md)` / `--font-size-sm` / `--font-size-base` / `--font-size-lg` / `--font-size-xl` / `--font-size-2xl` |
| `--el-font-family` | `var(--font-sans)` |
| `--el-component-size-small` / `--el-component-size` / `--el-component-size-large` | `var(--button-height-sm)` / `var(--button-height-md)` / `var(--button-height-lg)`，包含窄屏覆盖 |
| `--el-transition-duration` / `-fast` | `var(--duration-normal)` / `var(--duration-fast)` |
| `--el-menu-*`（侧栏） | `--sidebar*` 系列令牌 |
| `--el-table-*`（表头、边框、悬停、当前行） | `--table-*` 系列令牌 |
| `--el-mask-color` | `hsl(var(--overlay))` |
| `--el-disabled-*` | 由 `--muted-foreground` 与 `--muted` 派生 |

约束：
- 深色主题通过 `html.dark` 覆盖同一批 `--el-*` 变量，不新增第二套变量名。
- 定制优先通过变量和组件 props 完成；库内硬编码尺寸仅在有界组件类中映射回令牌。禁止大面积 `!important`、禁止 `:deep()` 深层结构覆盖、禁止全局标签选择器污染。
- 控件库硬编码的尺寸使用有界组件类映射回令牌：`.el-button` 默认 36px，小号 32px，大号 44px，窄屏分别为 44/40/44px；按钮组使用容器 `gap`，移除库默认相邻按钮外边距。图标与文字之间保留 6px。`aacv-empty` 下的 Lucide 图标保持描边，不继承库插图的填充色。
- `ElSelect` 的实际输入框最小高度映射到 `--input-height`（默认 36px、窄屏 44px），允许多选内容自然增高。分页尺寸映射到小号按钮令牌（默认 32px、窄屏 40px）；640px 以下使用上一页、页码输入、下一页，保留任意页跳转，避免将放大的页码列表挤出容器。
- 挂载到 `body` 的浮层（下拉、日期面板、消息、通知、弹窗、抽屉）必须显式指定 `popper-class` /
  使用 Element Plus 的 `append-to-body` 默认行为，并确认它们在 `html.dark` 下取到深色变量、
  `z-index` 高于顶栏（顶栏 `z-30`，浮层 ≥ `2000`）。

## 5. 页面布局规范

```
┌──────────────────────────────────────────────────────────────┐
│ 固定侧栏（lg+，可折叠 240/64px）│ 吸顶顶栏（60px，面包屑+工具区）│
│  · 系统名称与分组导航           ├──────────────────────────────┤
│  · 分组标题 role=heading        │ <main id="main-content">     │
│  · 折叠状态持久化               │  .page-stack                 │
│                                │   ├ PageHeader（标题+描述+操作）│
│                                │   ├ FilterBar（筛选，靠近表格） │
│                                │   ├ 页面级 Alert（错误/提示）   │
│                                │   └ PanelSection + DataTable  │
└──────────────────────────────────────────────────────────────┘
```

- **外壳**：侧栏 `fixed inset-y-0 left-0`，主区保留 240px/64px 边距；一次提交折叠布局，
  用 `transform` 的 FLIP 过渡说明内容边界移动，避免逐帧布局测量。`lg` 以下侧栏转为 `el-drawer`（左侧）。
- **顶栏**：`sticky top-0`，高度 `--topbar-height`，左侧移动菜单按钮，中间面包屑，右侧命令面板入口（Ctrl/⌘+K）、
  主题切换、账号菜单。顶栏背景使用 `--card` + 底部 hairline，滚动时保持不透明。
- **内容区**：`.page-stack` 纵向网格，`gap: 16px`，`lg+` 为 `gap: 20px`、内边距 `24px 32px 40px`；
  最大宽度 `--content-max-width`(1600px) 居中，`min-width: 0` 防止表格撑破。
- **PageHeader**：标题 `--font-size-2xl`(24px)/600/克制负字距，描述 `--font-size-md`/`--muted-foreground`；
  右侧 `#actions` 工具区（主操作在最右），底部可选 `divided` hairline。移动端标题与操作纵向堆叠。
- **面包屑**：非当前项为 `RouterLink`，当前项为纯文本 + `aria-current="page"`；640px 以下只显示当前页面，完整层级由导航抽屉提供。
- **导航分组**：可视化（工作台、成果目录、知识图谱、统计分析）、爬虫管理（数据源、采集任务、数据治理）、
  系统状态（质量指标、运行监控、日志管理）、用户管理（账号管理）。无权限页面与空分组不渲染。
  知识图谱带三个子入口（图谱概览 / 实体管理 / 关系管理）。父项为可独立折叠的按钮，右侧箭头表示展开状态；进入子页面自动展开。子项统一左缩进、52px 行高，浅色主题选中背景为中性浅灰、圆角 10px，不绘制树状竖线。深色主题沿用中性表面与文本令牌。
  路径分析与常用查询保留旧地址，并从图谱概览进入。实体管理和关系管理维护已有图谱类型的名称、颜色、尺寸与类型审核状态，不创建未经后端支持的新类型。
- **筛选位置**：单字段筛选放在对应表格上方的 `FilterBar`；跨字段搜索与批量操作放在工具栏；
  列表→详情→编辑→确认形成一致的操作路径（详情用独立路由，编辑用弹窗/抽屉，确认用 MessageBox）。
- **图谱页面**：条件区（左/上）+ 画布区（主）+ 详情区（右/下）；窄屏（<`xl`，1280px）详情区转为 `el-drawer`。
- **统计与运维页面**：按区域独立加载与降级，任一区域失败只降级该区域并给出文本原因，
  **不得用虚构零值掩盖失败**，其他区域继续可用。

## 6. 组件规范

### 6.1 表格（DataTable → `el-table` + `el-pagination`）

- 服务端分页、排序、筛选：**排序与分页状态由页面持有**，DataTable 只负责呈现与事件回传；
  不对当前页做局部排序冒充全量排序。
- 页码转换：后端 `page` 从 **0** 开始，`el-pagination` 的 `current-page` 从 **1** 开始；
  DataTable 内部完成 `page + 1` / `current - 1` 双向转换，`@update:page` 对外仍发出 **0 基**页码。
- 行标识：`row-key` 必须为稳定业务主键（如 `id`、`eventId`），保证分页与展开状态不串行。
- 保留列宽（`meta.width` → `width`/`min-width`）、单元格插槽（`#cell-<id>`）、行点击、空状态文案。
- 表头背景 `--table-header-bg`、表头文字 `--table-header-fg`、行悬停 `--table-row-hover`、
  边框 `--table-border`；`dense` 模式行高收紧。
- 加载态：首次加载显示骨架行；刷新时保留表格，内容降至 65% 不透明度并显示局部加载状态，**不逐行重复入场**、不闪烁。
- 分页条左侧固定文案 `显示 X–Y，共 Z 条`（`tabular-nums`），右侧为 `el-pagination`
  （默认 `layout="prev, pager, next"`；640px 以下改为 `prev, jumper, next`，保留任意页跳转）；
  页大小由页面持有，`prev`/`next` 的可访问名来自 zh-cn 语言包（上一页/下一页）。

### 6.2 表单与校验

- 控件只呈现一层边界：输入、数字、日期/时间和搜索选择由外层 wrapper 绘制边框，内部 input 不叠加 border、box-shadow 或 outline；键盘聚焦时将同一圈边框加粗为 2px，使用 `--ring`。多行文本框直接加粗自身边框，不叠加外圈。不得在 Element Plus 控件根节点额外添加边框、背景或内边距形成嵌套小框；命令面板搜索保持既有无边框布局。
- 校验源唯一：优先保留现有 Zod schema + vee-validate 规则，Element Plus 只负责呈现；
  **同一字段不得存在两套相互冲突的校验源**。使用 `el-form` 时其 `rules` 必须由同一 schema 派生，
  或直接关闭 `el-form` 内建校验、由 vee-validate 输出错误文本。
- 下拉选择统一使用 **搜索选择**（`el-select filterable`）：
  1. 长选项列表（成果类型、来源、告警类型、操作类型等）可直接键入过滤；
  2. Element Plus 在非 `filterable` 模式下会把 `role="combobox"` 的输入放进
     `opacity: 0; z-index: -1; position: absolute` 的包裹层，指针命中目标变成外层 wrapper，
     可聚焦目标与可点击目标不是同一个元素；开启 `filterable` 后该输入回到正常流，
     键盘、读屏与指针交互落在同一个 combobox 上。
- 错误提示：字段级错误显示在控件下方（`--font-size-sm`、`--destructive`），并关联 `aria-describedby`；
  弹窗级错误使用弹窗内顶部的 `el-alert type="error"`（`role="alert"`），**不使用页面级错误状态承接弹窗错误**；
  打开弹窗时清空对应错误，避免残留。
- 密码：最小 12 个字符（项目既有规范），使用 `el-input type="password" show-password`，
  `autocomplete="new-password"`（登录页为 `current-password`）。
- 日期/时间：`el-date-picker` 必须显式声明 `value-format` 与后端字段格式一致
  （日期 `YYYY-MM-DD`、日期时间 `YYYY-MM-DDTHH:mm:ss`）；仅日期值不进行时区转换，
  日期时间在提交前显式转换为 ISO-8601 UTC；`datetime-local` 语义为**本地时间**，转换必须写明方向。
  年份筛选（`publicationYear`）是**数字**而非日期，使用项目 `YearPicker`，不套用日期控件。
- 数字：`el-input-number` 用于 ID、页大小、跳数、节点上限等，必须声明 `:min`/`:max`/`:step`
  与后端硬限制一致（如子图节点上限 300、路径最大 6 跳、采集最多 5 页/500 条）。

### 6.3 弹窗、抽屉、气泡

| 场景 | 组件 | 规格 |
| --- | --- | --- |
| 表单编辑、证据审阅、确认危险操作 | `el-dialog` | 圆角 `--dialog-radius`，内边距 20–24px，遮罩 `--overlay`，动画 200ms；空闲时 `Esc` 可关闭，提交中按保存状态锁定关闭；关闭后焦点回到触发元素 |
| 移动端导航、图谱详情、宽表单 | `el-drawer` | 侧向滑入 200ms；导航宽 256px，图谱详情 `min(420px, 100vw)`，账号编辑 `min(576px, 100vw)`；窄屏把侧面板转为抽屉 |
| 破坏性二次确认 | `ElMessageBox.confirm` | 明确说明影响范围与不可逆性（如全量重建要求输入确认值 `REBUILD_AACV_MANAGED_GRAPH`） |
| 操作成功/失败的轻提示 | `ElMessage` | 3s 自动关闭，同一时刻最多一条同类消息，避免提示轰炸 |
| 需要保留的系统级通知 | `ElNotification` | 仅用于需要用户后续处理的告警类信息 |
| 图标释义、缩写展开 | `el-tooltip` | 延迟 200ms，深色背景 `--popover`，最大宽度 320px |
| 轻量操作菜单 | `el-popover` / `el-dropdown` | 菜单项高度 ≥32px，含图标与文本 |

浮层通用要求：焦点约束在浮层内部（focus trap），关闭后恢复触发元素焦点，
`z-index` 由 Element Plus 统一管理且高于顶栏与侧栏。

长表单以整个 `aacv-form-dialog` 为高度边界：上下留白 `clamp(16px, 6dvh, 48px)`，只滚动正文，标题和 footer 不收缩。数据源保存与取消放在 footer，通过原生 `form` 关联保持提交语义；校验失败将焦点移至首个无效字段，服务端错误留在标题区。正常、校验失败和服务端冲突状态均需在 390×844 视口验证关闭与保存动作可见。

### 6.4 状态标识（StatusPill）

状态**不能只依赖颜色**：每个状态标签由 `状态点 + 中文文本` 组成，并带 `aria-label`。

| 语义 | 覆盖的后端状态值 | 颜色令牌 |
| --- | --- | --- |
| 成功/正常 | `UP` `SUCCEEDED` `SUCCESS` `ACTIVE` | `--success` + `--status-running` |
| 进行中 | `RUNNING` `PROCESSING` | `--info` |
| 待处理/空闲/停用/过期 | `PENDING` `IDLE` `DISABLED` `EXPIRED` | `--muted-foreground` + `--status-idle` |
| 警告/降级/暂停/未知 | `DEGRADED` `WARNING` `PAUSED` `UNKNOWN` | `--warning` + `--status-warning` |
| 失败/异常 | `DOWN` `OUT_OF_SERVICE` `FAILED` `FAILURE` `DEAD` `CRITICAL` | `--destructive` + `--status-error` |

彩色状态使用对应语义色的 12% 底色和 35% 边框；中性状态使用 `--muted` 底色与 `--border`。
状态标签圆角为 `--radius-xs`（4px），字号为 12px（Tailwind `text-xs`）；状态点不循环脉冲或呼吸。

### 6.5 空态、加载、错误

| 状态 | 规范 |
| --- | --- |
| 正常 | 内容直接呈现 |
| 加载（首次） | 与结果布局匹配的 `ElSkeleton animated`（指标、卡片、表格行或文本）；必须有可感知文本（如"加载中"）或 `aria-busy="true"` |
| 加载（刷新） | 保留已渲染数据，以更新文案与 `aria-busy` 表达状态；表格内容为 65% 不透明度并配局部 `v-loading`，图表不模糊 |
| 空数据 | `el-empty` 风格空态：图标 + 中文标题 + 说明 + 可选主操作；文案必须说明"为什么为空"（无数据 / 无筛选结果） |
| 错误 | `el-alert type="error"`（`role="alert"`）+ 明确中文原因 + `traceId`（若有）+ 重试按钮；**不得静默失败或用零值替代** |
| 成功 | `ElMessage type="success"` 短提示；关键写操作后同时刷新对应列表 |
| 禁用 | 控件 `disabled` + `--muted-foreground` 文本 + `not-allowed` 光标；必要时用 tooltip 说明禁用原因（如"管理员不能停用自己"） |

### 6.6 图表与图谱

- 概览默认把节点按数量与画布比例铺开，再进行有界力导向布局；增加关系间距、减弱中心吸引并启用节点避让，避免所有节点从同一小圆周开始挤叠。首次加载与范围切换完成后适配整个网络；普通刷新保留用户拖动的位置，合作详情继续使用专用坐标。
- `/graph` 概览使用 vis-network 9.1.9 与 vis-data 7.1.9；高级查询与路径分析保留 Cytoscape。概览采用圆形节点、圆内短名称和完整名称提示，有向创作边与无向合作虚线沿用业务语义。单次挂载仅创建一个实例，范围切换和合作聚焦复用实例与 DataSet。
- 概览提供“全部 / 历史节点 / 返回上一级”和后端双向两跳查询；筛选约200ms防抖，仅作用于服务端返回的受限范围。统计明确标为当前视图，全局统计未接入。节点详情展示完整名称与扩展字段；缺少业务节点及关系写入接口，新增、编辑、删除和拖动建关系入口禁用并说明原因。
- 概览数据进入 store 前严格校验稳定 ID、重复项、端点和扩展 JSON；异常保留上次成功结果并报告，不静默丢弃。仅显示文字的悬停提示与详情不解析业务 HTML。
- 图谱概览采用参考图的紧凑标题（16px）和单行搜索、类型、关系工具栏，白色大画布占据剩余视口；画布左上提供视图操作，右上展示当前结果统计，底部提供两色图例。窄屏工具栏换行，详情和同源表格通过抽屉打开。
- 概览自动读取最多300个作者/作品节点及受管创作边，合作仍按本次返回作品计算；多簇网络使用无动画力导向布局。原五类节点中心查询保留在“高级查询”入口。
- 实体管理仅展示作者、作品两类配置；关系管理仅展示创作（作者→作品）和合作（作者↔作者）两类配置。采用紧凑搜索、序号、类型标识、状态及详情/编辑操作，实体表显示尺寸和圆点颜色。类型为既有投影契约，不提供任意新增/删除。
- 知识图谱父项及三个子项字号统一14px，与其他导航一致；保持统一缩进、52px子项行高和灰色选中背景。

- 图谱拓扑来自 Neo4j；类型名称、颜色、尺寸和类型审核状态由 MySQL `graph_type_definition` 提供。Canvas、图例、节点表与关系表使用同一响应中的配置。节点尺寸为直径 16–96px，关系尺寸为线宽 1–8px；默认待审核，状态以文字与线型表达，不仅依赖颜色。
- 作者合作关系由后端根据已返回的 Neo4j 作者—作品边计算，使用无箭头虚线，详情列出共同作品依据。它是当前受限子图内的派生关系，不是 Neo4j 中额外存储的边，也不是全库合作总数。最多附加 1000 条，截断时明确提示。
- 合作边直接标注共同作品数量和首部作品摘要；完整标题通过“合作作品”列表和画布内详情提供。选择合作时聚焦两位作者、共同作品及双方实际创作连线；两位作者并排置顶，共同作品逐行居中，避免沿用全网位置导致标签聚集。合作筛选保留作品依据，不再只留下作者之间的虚线。桌面详情与画布并列，窄屏详情位于画布下方。仅展示后端提供且能由双方 AUTHORED 边验证的作品，不按同名或前端猜测补合作关系。

- ECharts 色板取自 `--chart-1..6`，网格线 `--chart-grid`，坐标轴 `--chart-axis`，
  tooltip 使用 `--chart-tooltip-*`；主题切换时重设 option 而非重建实例。
- Cytoscape 节点和边优先使用响应中的 MySQL 配置；主题令牌用于文字、选中状态和兼容旧响应。中心查询画布使用 `--graph-canvas`，概览使用 `--card` 白色/深色工作表面。
- 图谱节点上限 **300**；概览拒绝重复 ID、悬空关系和超限响应并报告异常，高级查询的增量合并继续按 `id` 去重并移除未保留节点的关系。
- 每个图表必须提供**同源表格或文字替代视图**（`查看趋势表格` 之类切换），
  画布本身带 `role="img"` 与描述性 `aria-label`（含节点数与关系数）。

### 6.7 层叠与样式覆盖纪律（Tailwind 与 Element Plus 共存）

项目在 `styles/index.css` 声明 `theme → base → element-plus → components → utilities`，
并把 Element Plus 默认及深色样式导入 `element-plus` 层，避免库样式覆盖响应式布局。
主题变量与项目浮层样式在 `styles/element-plus.css` 集中维护。遵守以下规则：

1. **布局工具类优先放在容器上**，响应式显示、间距和排列可由 utilities 层覆盖组件默认布局；
   数字输入与固定规格控件的宽度可用显式 `style`，不依赖深层 DOM 结构。
2. **主题定制只走 `--el-*` 变量**：`styles/element-plus.css` 把 `--el-*` 指向项目语义令牌，
   深色主题在 `html.dark` 下覆盖同一批变量，不新增第二套变量名。
3. **弹窗/抽屉/浮层被 Teleport 到 `body`**，脱离组件 scoped 作用域；
   这类修正写在全局样式中，且只针对项目自有的 `aacv-*` 类名，
   优先使用组件提供的 `header-class` / `body-class` / `popper-class` 挂载点。
   scoped `:deep()` 仅允许用于组件自身子树内的排版微调，禁止用于结构覆盖。
   `!important` 只允许出现在 `prefers-reduced-motion` 媒体查询中（唯一例外）。

## 7. 中文排版规范

| 场景 | 规则 |
| --- | --- |
| 中文字体 | 本地回退栈，不引入品牌字体与网络字体；`font-synthesis: none` 避免伪粗体 |
| 正文 | `--font-size-md`(14px) / `--leading-normal`(1.55)；默认表格字体令牌为 `--font-size-base`(14px)，密集模式的单元格垂直内边距为 6px |
| 长标题 | 成果题名、机构名等使用 `text-wrap: balance`（标题）/ `pretty`（段落），允许 `overflow-wrap: anywhere` 断长英文串与 DOI；**不截断关键标识**，必要时 `el-tooltip` 展示全文 |
| 数字 | 一律 `font-variant-numeric: tabular-nums`（Tailwind `tabular-nums`），并 `toLocaleString('zh-CN')` 加千分位；计数不得逐帧跳动造成阅读困难（见 §8） |
| 日期 | 显示统一 `YYYY-MM-DD HH:mm:ss`（本地时区）；仅日期字段显示 `YYYY-MM-DD`；后端 ISO-8601 UTC 字符串在展示层转换，未知/缺失显示 `--` 或"未知"，**不得显示 1970 或空字符串** |
| 证据内容 | DOI、ORCID、ROR、ISSN、traceId、任务 ID、JSON 使用 `.mono-evidence`（等宽 + `--font-size-sm` + `overflow-wrap: anywhere`）；JSON 证据可折叠并保留缩进 |
| 未知值语义 | "未知"不等于 0 或 false。被引量、开放获取、撤稿状态等缺失值必须显示"尚未采集"/"未知" |
| 标点 | 中文语境使用全角标点；并列项分隔用 `；`（作者）或 `，`（主题） |
| 英文装饰文本 | 业务页面**不使用**纯装饰英文副标题。保留功能性标识：`MySQL` `OpenAlex` `Crossref` `DOI` `CSV` `JSON` `Neo4j` 及指标代码 |

## 8. 动效规范

| 类别 | 时长 | 曲线 | 说明 |
| --- | --- | --- | --- |
| 普通反馈（悬停、按压、焦点、颜色） | 120ms | `--ease-standard` | `--duration-fast` |
| 外壳位移、弹窗、抽屉、下拉展开 | 200ms | `--ease-emphasized` / `--ease-decelerate` | `--duration-normal`；外壳位移可取消 |
| 页面切换 | 120ms | `--ease-standard` | 仅新内容淡入，旧页即时卸载；无 out-in 等待，不延后请求与焦点 |
| 主题切换 | 200ms | `--ease-standard` | 只过渡 `background-color`/`color` |
| 骨架加载 | 组件默认循环 | Element Plus 默认 | `ElSkeleton animated`；仅实际加载时挂载，减少动画时关闭循环 |
| 状态反馈 | 120ms | `--ease-standard` | 颜色、文字即时更新；不循环呼吸，不以动效暗示不存在的实时连接 |
| 日志更新 | 即时 | 无位移 | 只在用户停留末尾时跟随；查看历史时保留滚动位置 |
| 数值更新 | 即时 | 无插值 | 接口终值立即可读，不生成中间指标；未知显示 `--` |
| 图表更新 | 320ms | ECharts `cubicOut` | 按稳定 series/data 标识合并更新；不重建实例，筛选期间保留图形并显示更新状态 |
| 排行榜重排 | 200ms | Vue `TransitionGroup` FLIP | 只对位置变化做位移过渡 |
| 图谱布局与镜头 | 首次即时；后续 280ms | Cytoscape `ease-out-cubic` | 首次采用无动画同心布局并适配视口；新增节点围绕关联节点展开，已有位置保留；镜头仅响应聚焦或重置；选中/悬停用 120ms 透明度说明邻域，连续操作先停止旧动画 |

动效焦点是图谱选中与关系聚焦的连续性；导航、抽屉、弹窗、加载与反馈只承担辅助说明。
侧栏位移、图谱布局和相机动画必须可取消；减少动画偏好改变时立即生效，组件卸载释放媒体监听、ResizeObserver、rAF、图实例及未完成动画。图表更新按同一系列标识替换旧系列，移除的数据不得残留；表格和普通列表不做逐行入场，仅合作排行的稳定 ID 位置改变使用 FLIP。

技术选型：**优先 CSS 过渡/动画、Vue `<Transition>`/`<TransitionGroup>` 与图表引擎已有能力，
不引入额外大型动画库。**

`prefers-reduced-motion: reduce` 时必须：
- 全局把 `animation-duration`/`transition-duration` 降到 `0.01ms`、`animation-iteration-count: 1`、`scroll-behavior: auto`；
- 数字在所有模式下均立即显示终值；图表关闭初始与更新动画；图谱新增节点与镜头直接到达目标位置，并结束正在进行的动画；
- 保留所有状态文本与非颜色提示，确保信息不因关闭动效而丢失。

表格刷新稳定性要求：**禁止**逐行重复入场动画、禁止持续闪烁、禁止无意义数字跳动。

## 9. 可访问性与键盘操作

- **焦点可见**：按钮、链接、菜单等使用 `outline: 2px solid hsl(var(--ring)); outline-offset: 2px`；表单输入与选择控件按 §6.2 加粗同一圈边框，内部输入元素不再绘制焦点小框。
- **跳转链接**：布局顶部提供"跳到主内容"（`.sr-only`，聚焦时可见），主区 `id="main-content"`。
- **键盘可达**：所有交互元素为原生可聚焦元素或带 `tabindex="0"` + `role`；
  侧栏、命令面板、标签页、表格分页、图谱条件区均可纯键盘操作。
- **快捷键**：`Ctrl/⌘ + K` 命令面板，`Ctrl/⌘ + B` 折叠侧栏，`Esc` 关闭浮层。
- **浮层焦点**：焦点约束在浮层内部循环；关闭后焦点恢复到触发元素。
- **状态不只靠颜色**：状态点必须伴随中文文本；加载与错误必须有可感知文本
  （`aria-busy`、`role="alert"`、`aria-live="polite"` 区域）。
- **表格语义**：数据表使用 `el-table`（内部为语义 `<table>`），证据/字段对照使用语义
  `<table>` + `aria-label`，表头为 `<th scope>` 或 `role="columnheader"`。
- **图表替代**：所有图表与图谱提供同源表格或文字替代视图，画布带 `role="img"` + 描述性 `aria-label`。
- **表单关联**：`FilterField` 用 `<label>` 包裹控件形成隐式关联；无法包裹时用 `for`/`id` 或 `aria-label`。
- **触摸目标**：`lg` 以下交互元素最小高度 40px，表单输入 44px。
- **对比度**：正文与背景对比度 ≥ 4.5:1，次要文本 ≥ 4.5:1，状态色文本在其 12% 底色上 ≥ 4.5:1。

## 10. 状态规范汇总（正常/悬停/聚焦/禁用/加载/空/错误/成功）

| 元素 | 正常 | 悬停 | 聚焦 | 禁用 | 加载 |
| --- | --- | --- | --- | --- | --- |
| 主按钮 | `--primary` 底 + `--primary-foreground` | `--primary-hover` | `--ring` 2px 焦点环 | `--muted` 底 + `--muted-foreground/.6` | 内置 spinner + 文本不变、禁止重复点击 |
| 次按钮 | `--secondary` 底 + hairline | `--accent` 底 | 同上 | 同上 | 同上 |
| 危险按钮 | `--destructive` 底 | 提亮一档 | 同上 | 同上 | 同上 |
| 文本/幽灵按钮 | 透明底 + `--foreground` | `--accent` 底 | 同上 | `--muted-foreground/.6` | — |
| 输入 | `--input-background` 底 + `--input` 边框 | 边框转 `--border-strong` | 同一圈边框加粗为 2px，使用 `--ring`，无内框或第二圈 outline | `--muted` 底 | — |
| 表格行 | 所在工作表面，行背景透明 | `--table-row-hover` | 行内控件各自聚焦 | — | 保留内容，65% 不透明度与局部加载状态 |
| 导航项 | `--sidebar` 底 + `--sidebar-foreground` | `--sidebar-accent` 底 | 焦点环 | 不渲染（无权限） | — |
| 导航项（当前） | `--sidebar-accent` 底 + `--sidebar-active` 左侧 2px 指示条 + `--font-medium` | 同上加深 | 焦点环 | — | — |

## 11. 验收视口与检查项

| 视口 | 用途 |
| --- | --- |
| 1440 × 900 | 默认桌面验收 |
| 1920 × 1080 | 大屏密度与最大宽度约束 |
| 390 × 844 | 窄屏适配（侧栏转抽屉、详情转抽屉、单列筛选） |

每次视觉变更至少检查：横向溢出（`document.documentElement.scrollWidth <= window.innerWidth`）、
浮层遮挡与层级、滚动容器、键盘可达与焦点恢复、控制台错误为 0、同一操作不产生重复请求、
浅色与深色两主题下的对比度与边框可见性、`prefers-reduced-motion` 下的可读性。

## 12. 已落实的状态和生命周期边界

Pinia `session` 统一会话/权限，`preferences` 统一主题/侧栏，`graph` 持有图结果、焦点与待执行查询。
页面离开和账号变化清理旧请求、图结果及轮询；请求控制器、计时器和图实例留在闭包或组件中。
所有数据表必须显式提供稳定 `getRowId`。图谱窄屏工具栏允许换行，详情使用抽屉。
统计接口分别保留成功结果；失败区域显示原因，运维未知计数显示 `--`。
运维标签页只挂载当前页的表格，页面保留各页筛选与分页状态，避免隐藏表格反复测量尺寸。
长表单的提交错误放在弹窗标题区域，滚动到表单底部后仍可看到失败原因。
本轮视口、截图、实际命令与已知限制见 [工作台升级验收](docs/frontend-redesign-acceptance.md)；
此前迁移记录见 [迁移清单](docs/frontend-migration-inventory.md)。
