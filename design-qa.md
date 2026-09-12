# 登录参考图专项验收（2026-09-12）

final result: passed

本节只覆盖本次登录页改造。下面的全系统及门户记录保留为历史证据，其测试数不计入本次结果。

## 参考与实际页面

- 参考：用户提供的 `ChatGPT Image 2026年9月12日 15_21_31.png`，1672 × 941。
- 实际页面：`http://127.0.0.1:18000/crawler/login`，当前生产构建，由既有本机网关提供。通过 Codex 内置浏览器检查，没有使用真实凭据登录。
- 桌面对照为 1672 × 941 CSS 像素、设备像素比约 1，同为空表单、浅色。原图和实际截图在同一次视觉输入中对照，并逐项检查标题、表单和底部说明；文字在该尺寸下可直接辨读，无需放大裁图。
- 最终截图与检查材料在 Git 忽略的 `.local/login-reference-review/`；`login-1672.png`、`login-390.png` 为最终修正后截图。其他宽度截图记录布局检查，`responsive.json` 保存尺寸与控件边界。

## 视觉检查与迭代

| 检查面 | 结论 |
| --- | --- |
| 字体与排版 | 沿用项目中文字体栈，匹配深蓝标题、英文副标题、标签与辅助说明的层级；桌面主标题保持单行，320px 标题自然换行。操作标签可读；不额外下载字体 |
| 布局与间距 | 参考尺寸右卡约 600 × 666、左边距约 998px、顶部约 160px；保持全屏背景、顶部居中系统名、左下文案与三项能力、底部分隔线。320/390/768/1440/1672 均无横向溢出，较矮视口自然纵向滚动，主要输入及按钮可达 |
| 颜色与状态 | 深蓝正文、蓝色主按钮、白色输入框和浅蓝边框。主按钮文字对比度实测约 5.16:1；占位符改深为 #607493，并固定白色输入底，对比度约 4.75:1。焦点与错误提示可区分 |
| 图片与图标 | 内置 image_gen 从参考移除 UI 和文字，生成独立 1672 × 941 背景；山、桥、校园、湖面位置保留，背景浓度已对照调整。图片通过 BASE_URL 加载，浏览器确认资源加载成功。使用现有 lucide 图标，桌面卡片复用背景底部纹理，窄屏隐藏该纹理层 |
| 文案与功能 | 系统名、主标题、说明、三项能力、表单与账号提示按参考还原。表单仍为真实 Vue/Element Plus 控件，未将文字或交互烘焙进图片；原登录请求、校验与会话提示保持 |

首次对照发现并修正：空密码时缺少显隐图标、背景偏浓、输入区域宽度与间距偏差、卡片纹理局部截断产生色块，以及占位符过浅。显隐按钮现为 44 × 44 可操作区域，带动态名称和 aria-pressed，键盘 Enter 可切换；输入框白底、较深占位符和窄屏纹理处理已在最终构建中重新截图确认。

最终复核无待处理 P0/P1/P2。P3 差异：插画补全部分存在水彩纹理变化；字体受本机回退字体影响，图标使用现有库的近似款式；主按钮使用对比度更稳定的实蓝色，卡片波纹更淡。不宣称逐像素一致。

## 验证结果与边界

| 实际执行 | 结果 |
| --- | --- |
| 前端：`npm.cmd run test -- src/views/LoginView.test.ts src/stores/session.test.ts src/router/index.test.ts` | 3 个文件、50 项通过。最初受限环境发生 `spawn EPERM` 和本机原生依赖加载错误；在允许子进程的本机环境重试后通过，未改依赖。日志：`unit-tests.log` |
| 前端：`npm.cmd run build -- --base=/crawler/` | 最终 TypeScript 检查与生产构建通过。仍有现有图表分块超过 500 kB 的提示，未修改拆包或依赖。日志：`build.log` |
| 内置浏览器交互检查 | 5 种视口、图片加载、空输入按钮提交、密码框 Enter 提交、密码显示/隐藏及其键盘切换通过；expired/unavailable 提示及窄屏多条错误提示可读、可滚动；控制台 error/warn 为空 |
| 根目录：`node .local/login-reference-review/sync-source.mjs` | 仅同步登录组件和新增背景两项来源记录，保留其他记录 |
| 根目录：`node scripts/check-source.mjs` | 失败于既有 `crawler/backend/src/main/resources/application.yml` 未登记适配。该文件开始时即有未提交修改，本次未读取其配置内容或改动它；未扩大范围补登记后端配置 |
| 根目录：`git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab diff --check -- systems/crawler/frontend/src/views/LoginView.vue docs/development.md docs/integration-baseline.md docs/source-adaptations.json design-qa.md` | 通过；仅有 Windows 行尾转换提示 |

没有新增依赖、修改后端或数据库，没有运行真实账号的完整登录链路。会话及路由兼容性由现有单元测试和提交函数对照验证；生产页面的视觉与本地表单交互另行验证，不混淆两类证据。

项目记忆已读取并同步 `docs/development.md`、`docs/integration-baseline.md`：补充独立素材、局部样式、响应式与认证保持约定。其余既有用户改动保留。

实施核对：已接入独立资产；已保留登录逻辑；已完成视觉迭代、键盘和窄屏检查；已构建；已同步本次来源记录及项目记忆；全仓来源校验的既有后端配置差异已单独报告。

# 浅色学术研究页面验收（2026-09-12）

本节为当前 crawler 前端的验收记录。下方旧门户记录完整保留，仅用于追溯历史，不代表当前页面样式或系统范围。

final result: passed

## 任务范围与实现

依据用户提供的十一张参考图，将工作台、大屏、成果目录、实体编目、三类学术图谱、统计、导入、日志与账号管理统一为浅色学术界面，同时覆盖登录和共享弹层。保留当前 API、权限、导入确认、搜索分页、导出、详情与图谱证据操作；没有修改后端、数据库、依赖或 Git 历史。执行前读取两份指定项目记忆、当前路由和页面，检查既有工作区变更；本轮计划为共享主题和导航先行、按现有数据重排页面、再进行单测、构建与浏览器验证。

本轮以浅灰蓝背景、白色卡片、深蓝正文、适量蓝色主操作代替原深色科技风。取消电路线、发光和网格装饰，图表、图谱及弹出控件使用同一浅色配色。旧本地深色偏好迁移到浅色，系统深色偏好不会改变页面。工作台的概览调用现有统计接口并检查权限，最近导入以六行表格呈现；大屏采用三列卡片，作者与机构图只展示接口返回的合作关系。

## 参考与证据

- 参考：本次用户附件 `ChatGPT Image 2026年9月12日 14_10_04.png` 及 `14_35_15` 至 `14_36_09` 的十张页面图片，位于本机 Downloads，共十一张，均为 1672 × 941。
- 主视觉验收：`.local/light-theme-review/final-acceptance/light-theme-浅色页面、真实图形绘制与参考视口截图-desktop-edge/`，包含十一页截图与 `user-dialog.png`。使用现有 Playwright 和本机 Edge，视口 1672 × 941、设备像素比 1，关闭截图动效，没有二次缩放。
- 工作台：`workbench.png`；大屏：`dashboard.png`；列表：`catalog.png`、`entities.png`、`logs.png`、`users.png`；图谱：`relations.png`、`achievements.png`、`background.png`；统计与导入：`analytics.png`、`import.png`。
- 登录：`.local/light-theme-review/final-acceptance/light-theme-登录及弹出控件使用浅色，文字和主操作可读-desktop-edge/login.png`，1440 × 900。
- 窄屏：同一 `final-acceptance` 目录的三项“窄屏检索、导入及导航”结果，宽度 320、390、768；另验证大屏 1440、2550、390 与系统深色偏好下的 1440 视口。既有主题测试覆盖 1280、1366、1440、1920，日志测试覆盖 1440、2549、390。
- 较多记录的宽屏预览：`systems/crawler/.local/research-redesign/dashboard-1920.png`，1920 × 1080，五行合作排行与三条导入记录均通过完整可见断言。
- 在同次图片检查中同时打开参考与对应实现，比较完整页面；另复核图谱标签、机构网络、弹层表单、六行导入表、四个指标与字段覆盖率的可见性。原始尺寸已能辨识文字，未对图片作修图。
- 页面与图形在浏览器中真实渲染，API 使用测试夹具，数据均为模拟数据。截图中的人物、机构、成果数量不代表当前数据库。真实网关仅校验静态产物，没有使用真实账号执行导入或管理写操作。

## 视觉与交互检查

| 检查面 | 结论与依据 |
| --- | --- |
| 排版 | 系统名称、页面标题、卡片标题、正文和辅助信息形成层级；列表正文及输入恢复易读尺寸，长标题换行，窄屏内容区独立滚动 |
| 布局与间距 | 桌面统一十项主导航；工作台采用检索、导入与真实概览并列布局；大屏三列；列表保持右上紧凑筛选；三类图谱保留大画布和时间线；手机通过原抽屉访问模块，无页面横向溢出 |
| 颜色 | 浅灰蓝底、白卡、深色字，边框与斑马纹较轻；成功、失败、选中、焦点仍可区分；登录主按钮及工作台主按钮/指标文字对比度测试均不低于 4.5 |
| 图片与图标 | 新校园插画 2048 × 768，仅作装饰，顶栏与各页面通过 BASE_URL 加载；1672 参考尺寸无破损图；图标继续使用现有 lucide 组件 |
| 内容与功能 | 搜索、导入入口、导航、筛选、分页、失败重试、账号编辑弹层及图谱证据入口保留；未填入参考图的虚构人物照片、学历、任职、增长率或机构数量 |
| 图形绘制 | 实际 Cytoscape canvas 与 ECharts 图形均检查；普通署名/指导边按悬停或选中显示文字，合作边保留共同创作标签；稀疏机构网络以圆形布局正常绘制，不以计数代替图形证据 |
| 键盘与窄屏 | 保留原导航、命令面板与焦点规则；抽屉可通过 Escape 关闭；320、390、768 的检索、导入跳转通过；窄屏主操作高度至少 40px |

## 迭代与已修正问题

1. 目录与日志标题增大后，首行位置超过原紧凑布局验收阈值。收紧顶部留白而保留字体和行间距，相关桌面与移动端回归通过。
2. 成果图普通边标签在作者中心叠加，影响阅读。改为悬停或选中显示，合作边继续常显；增加实际 Cytoscape 样式单测，图谱浏览器回归通过。
3. 机构合作网络只有两个节点时，旧手动坐标导致节点被拉大。改用现有 ECharts circular 布局，仍只使用真实返回节点和合作对；最终截图的节点和连线正常。
4. 工作台六条导入记录、大屏指标和覆盖率底部存在裁切。调整局部卡片间距与高度，最终参考视口显示六条导入记录、四项指标和四项覆盖率。追加较多数据的宽屏预览时，发现第五行作者排行显示不完整；给列表卡片设置内容所需的最低高度并收紧行间距，保留原字号和完整可见断言。列表及时间线保留正常内容滚动。
5. 若干既有测试仍引用已被前序业务改动移除的筛选或统计导航名称；按当前源码更新测试定位和当前分类入口，保留同等交互和对比度验证。新增截图测试的时间线定位、登录按钮定位以及新增 Cytoscape 测试的重载类型问题已修正。
6. 多机构截图中名称密集叠加。注册已安装 ECharts 的 LabelLayout 特性，仅在大屏合作网络启用 hideOverlap，避免名称相互覆盖；保留全部节点、连线、悬停详情及原交互。

最终视觉复核未发现需要阻断本轮交付的 P0/P1/P2 问题。插画和图标按现有素材与组件实现，不要求逐像素复制。数据能力差异有意保留：背景图展示已有成果时间线，不把导入的成果机构推断为学者任职；统计数量随真实返回值变化，空数据有空状态。

## 实际执行与结果

下列前端命令工作目录为 `systems/crawler/frontend`；根级命令已注明。

| 实际命令 | 观察结果 |
| --- | --- |
| `npm.cmd run test` | 42 个测试文件、228 项测试通过，覆盖主题、工作台权限/失败处理、图谱及原有业务单元测试 |
| `npm.cmd run test -- src/components/EChartCanvas.test.ts` | 最后注册标签避让特性后，图表组件 3 项测试复测通过 |
| `npx.cmd vue-tsc -b` | 修正新增测试的 Cytoscape 重载类型后通过 |
| `npm.cmd run build -- --base=/crawler/` | 最终类型检查与生产构建通过；保留现有图表大分包警告，没有修改依赖或构建拆包策略 |
| `npm.cmd run test:e2e -- e2e/light-theme.spec.ts e2e/academic-graph.spec.ts e2e/compact-research.spec.ts e2e/research-theme.spec.ts --output=../../../.local/light-theme-review/browser-v2` | 当时全部 25 项通过；后续局部修正另按下面命令复测 |
| `node node_modules/@playwright/test/cli.js test e2e/light-theme.spec.ts e2e/academic-graph.spec.ts e2e/research-dashboard.spec.ts e2e/logs-management.spec.ts --grep-invert '保存全系统' --output=../../../.local/light-theme-review/final-browser` | 19 项中 16 项通过；新增登录测试定位、两项日志顶部间距失败已修复，并由下一命令复测通过；图谱 3 项和大屏功能 6 项通过 |
| `node node_modules/@playwright/test/cli.js test e2e/light-theme.spec.ts e2e/logs-management.spec.ts --output=../../../.local/light-theme-review/acceptance` | 最终全部 10 项通过：十一页截图、弹层、登录、三个窄屏和日志筛选/分页/失败重试；页面脚本和控制台 error 检查通过 |
| `node node_modules/@playwright/test/cli.js test e2e/redesign-interactions.spec.ts --grep '固定浅色工作台\|深浅系统偏好' --output=../../../.local/light-theme-review/layout-acceptance` | 四项布局通过；对比度用例因旧筛选按钮已不存在而超时，随后改用当前真实工作台控件，未降低对比度阈值 |
| `node node_modules/@playwright/test/cli.js test e2e/redesign-interactions.spec.ts --grep '深浅系统偏好' --output=../../../.local/light-theme-review/contrast-acceptance` | 最终 1 项通过，深浅系统偏好下均保持浅色；主按钮、辅助文字对比度及窄屏触摸高度满足断言 |
| `node node_modules/@playwright/test/cli.js test e2e/research-dashboard.spec.ts --grep '保存全系统' --output=../../../.local/light-theme-review/preview-acceptance` | 修正排行裁切后 1 项通过，包含大屏完整列表、高级图谱 7 节点 9 连线、统计分类、日志/账号弹层与控制台错误检查 |
| `node node_modules/@playwright/test/cli.js test e2e/research-dashboard.spec.ts --grep '保存全系统' --output=../../../.local/light-theme-review/labels-acceptance` | 最后启用标签避让后 1 项复测通过，最终宽屏截图没有机构名称相互叠压 |
| 根目录：`node .local/light-theme-review/sync-adaptations.mjs` | 首次更新 23 项、新增 10 项；布局复测后更新 2 项，最后标签避让再更新 1 项、新增 1 项。白名单最终包含 34 个前端文件；219 项白名单外记录完全不变，旧 sourceBlob 均保持 |
| 根目录：`node scripts/check-source.mjs` | crawler 原始来源树、来源祖先、全部文件与适配记录检查通过 |
| 根目录：`node .local/light-theme-review/verify-served.mjs` | 网关全部 108 个前端文件 HTTP 200，SHA256 与最终 dist 完全一致；明细为 `.local/light-theme-review/served-verification.json` |
| 根目录：`node .local/light-theme-review/verify-scope.mjs` | 与开始时保存的 diff 对照，21 个已有业务、路由、配置等变更的 diff 完全保持 |
| 根目录：`git -c core.whitespace=blank-at-eol,blank-at-eof,space-before-tab diff --check` | 退出码 0，没有空白错误；Windows Git 提示既有行尾转换约定，不影响结果 |

最终统一浏览器回归执行以下完整命令，43 项全部通过（2.4 分钟）；它包含前述独立图谱、列表、统计、工作台、大屏、日志、浅色与窄屏用例，并包含完整的宽屏预览用例。没有把未执行的历史浏览器用例计入通过数。

```powershell
node node_modules/@playwright/test/cli.js test e2e/light-theme.spec.ts e2e/academic-graph.spec.ts e2e/compact-research.spec.ts e2e/research-theme.spec.ts e2e/research-dashboard.spec.ts e2e/logs-management.spec.ts e2e/redesign-interactions.spec.ts --grep 'light-theme.spec.ts|academic-graph.spec.ts|compact-research.spec.ts|research-theme.spec.ts|research-dashboard.spec.ts|logs-management.spec.ts|固定浅色工作台|深浅系统偏好' --output=../../../.local/light-theme-review/final-acceptance
```

早期 Windows 沙箱启动 Vite/Edge 或子进程 Git 遇到 `spawn EPERM` / `spawnSync git EPERM`，在获准的正常权限环境重试后完成；未通过安装额外依赖或放宽应用权限绕过。应用内浏览器工具无法取得浏览器清单，本轮采用仓库现有 Edge/Playwright 验收，没有将其描述为应用内浏览器操作成功。

## 本轮变更文件

以下仅列本轮修改；仓库原有单系统、ORCID、后端和其他未提交变更均不计入本次变更。前端相对目录为 `systems/crawler/frontend/`。

| 文件 | 本轮变化 |
| --- | --- |
| `index.html` | 静态浅色首帧和旧主题偏好迁移 |
| `src/styles/tokens.css` | 浅色语义颜色、字体、组件尺寸与状态 |
| `src/styles/index.css` | 移除深色组件库样式导入 |
| `src/styles/element-plus.css` | 统一浅色组件、下拉框及弹层 |
| `src/styles/research.css` | 共享浅色页面、导航、卡片、表格与响应式布局 |
| `src/composables/useTheme.ts`、`useTheme.test.ts` | 固定浅色状态及初始化验证 |
| `src/composables/useChartTheme.ts` | 图表与图谱浅色回退配色 |
| `src/layouts/BusinessLayout.vue` | 大屏与业务页统一主导航 |
| `src/components/EChartCanvas.vue` | 注册现有图表库的标签避让特性 |
| `src/components/business/AppTopbar.vue` | 校园装饰与浅色顶栏 |
| `src/components/business/AchievementPreview.vue` | 预览标题前景色 |
| `src/components/business/CompactFieldSearch.vue` | 紧凑搜索控件尺寸与字色 |
| `src/components/business/DataTable.vue` | 表格行间距 |
| `src/components/business/GraphCanvas.vue`、`GraphCanvas.test.ts` | 普通关系文字交互显示与实际样式验证 |
| `src/views/WorkbenchView.vue`、`WorkbenchView.test.ts`（新增测试） | 参考布局、真实概览、六行导入表、权限与错误分支测试 |
| `src/views/OverviewView.vue` | 浅色三列大屏与真实合作图布局 |
| `src/views/LoginView.vue` | 浅色登录和校园装饰 |
| `src/views/CatalogView.vue`、`LogsView.vue` | 标题字号与紧凑顶部留白 |
| `src/views/AnalyticsView.vue` | 标题和环形图边框色 |
| `src/views/AuthorImportView.vue` | 上传步骤、文件选择区和说明卡片 |
| `src/views/AcademicGraphView.vue` | 学术图谱与背景时间线的浅色阅读布局 |
| `src/views/GraphView.vue` | 高级概览去除深色标题样式 |
| `src/utils/academic-graph.ts`、`academic-graph.test.ts` | 节点分类色、交互标签标记和语义测试 |
| `e2e/research-theme.spec.ts` | 固定浅色、共享导航与当前统计分类验证 |
| `e2e/research-dashboard.spec.ts` | 大屏导航、浅色与预览验证 |
| `e2e/redesign-interactions.spec.ts` | 响应式浅色布局、主题偏好迁移和当前控件对比度验证 |
| `e2e/academic-graph.spec.ts` | 图谱绘制颜色采样适配浅色 |
| `e2e/light-theme.spec.ts`（新增） | 十一页截图、图形、弹层、登录、窄屏与脚本错误验证 |
| `public/images/campus-banner.png`（新增） | 浅色校园插画 |
| 根目录 `docs/integration-baseline.md`、`docs/development.md` | 同步固定浅色、数据权限、导航和图谱约定 |
| 根目录 `docs/source-adaptations.json` | 仅同步本轮前端文件的来源适配记录 |
| 根目录 `design-qa.md` | 新增当前验收记录，保留旧门户历史 |

截图、构建产物与本机验证脚本位于既有忽略目录，不加入交付源码。两份项目记忆中的旧门户和阶段性验证说明已与当前源码区分；本轮新增浅色约定，没有覆盖原单系统或 ORCID 工作，没有新建项目记忆体系。

## 限制与使用

- 前端模拟浏览器验收与真实账号业务验收有明确区分；本轮未改真实账号、导入真实文件或验证所有真实数据组合。
- 使用本机 Edge，未宣称 Firefox、Safari 或整套历史浏览器用例已全部执行。
- 部分图表依赖分包仍超过 500 kB，构建成功但保留原分包警告；本轮没有扩大到性能拆包改造。
- 本地网关已读到最终产物。访问 `http://127.0.0.1:18000/crawler/`，旧页面用 Ctrl+F5 刷新即可查看；不需要重启后端或更改数据库。

---

# 统一入口视觉验收（历史）

> 历史记录：本文的门户、多系统入口与相关测试脚本已在 2026-09-12 单系统调整中退役。当前范围、入口及验证以 [单系统调整记录](docs/single-system.md) 为准。

> 历史范围说明：本文保留对应阶段的设计或验收记录。2026-09-10 已删除 extraction、scholar，当前仅保留 relation、crawler；原四系统描述、已删除文件和历史命令不代表当前运行范围。当前配置与说明见 `deploy/systems.json`、`docs/development.md`。

核对日期：2026-09-09。范围仅为 `portal/` 入口视觉、入口内交互与相关说明；未调整任何子系统页面。

final result: passed

## 参考与证据

- 参考图：`D:/User/Downloads/ChatGPT Image 2026年9月9日 16_40_05.png`，1672 × 941。
- 实际页面：`http://127.0.0.1:18000/`，复用仓库已运行的 Demo 进程。
- 最终桌面截图：`.local/browser/portal-desktop-final.png`，1672 × 941。浏览器设置视口 1672 × 941，DOM 报告高度因取整为 942，设备像素比约 1；截图与参考图像素尺寸一致，无二次缩放。
- 手机截图：`.local/browser/portal-mobile-final.png`，375 × 3348，视口设置 390 × 844，截图不包含浏览器滚动条。
- 最窄屏截图：`.local/browser/portal-narrow-320.png`，304 × 3467，视口设置 320 × 780。
- 中等桌面与平板截图：`.local/browser/portal-desktop-1440.png`、`.local/browser/portal-tablet-final.png`，分别以 1440 × 900、1024 × 900 视口核对。
- 对照状态：首页、搜索为空、全部三个系统显示、趋势为近 5 年、说明弹窗和菜单关闭。实际系统均为维护中，区别于参考图的开放入口，属于保留现有业务状态的有意差异。
- 全图对比：同次检查同时打开参考图与最终桌面截图。重点复核顶部导航、主标题、三列卡片、左右信息面板、数据总览与底部圆台。
- 局部对比：`.local/browser/portal-cards-comparison.png`（三卡片）、`.local/browser/portal-overview-comparison.png`（数据总览）；上方是参考图，下方是实现，同坐标裁切后比较，没有修改页面素材。

截图和源 PNG 位于现有忽略目录 `.local/`，不加入交付源码。交付图片均为 `portal/public/assets/portal/images/*.webp`。

## 视觉检查结果

| 检查面 | 结果 |
| --- | --- |
| 字体与排版 | 使用本机中文无衬线字体，保留大标题、卡片名称与辅助说明的层级；长名称在中等和小屏合理换行，320px 下没有单字尾行；没有遮挡入口按钮 |
| 布局与间距 | 桌面保留约 1:4:1 的三栏结构；卡片顶部、底部和数据总览位置接近参考；辅助面板在较窄屏幕移到入口后，手机采用单列卡片 |
| 颜色与视觉样式 | 深蓝背景、蓝青/蓝紫/青绿三张入口卡片、发光边框及胶囊按钮与参考保持同一方向；图表与数字使用清晰的浅蓝文字 |
| 图片 | 五张独立生成素材均加载成功，按容器等比例显示；消除了插图的明显矩形底色；WebP 转换保留原尺寸、透明度和可见像素 |
| 内容 | 标题和三个完整系统名称保留；统计标注为演示内容；按钮、维护状态和跳转保持真实接入语义；顶部输入框明确用于搜索系统与研究方向 |
| 图标 | 使用本地 Bootstrap Icons 子集及附带许可证；没有依赖外部 CDN；图标及菜单控件实际可见 |
| 可访问性 | 保留跳转链接、语义标题与输入标签；图表有替代描述和数值图例；弹窗可通过关闭按钮和 Escape 关闭；焦点样式与减少动态效果规则存在 |

## 迭代与修正

1. 首次素材挂载使用根级 `/images/`、`/icons/`，被现有网关返回 404。修正为 `/assets/portal/`，保持网关及其白名单不变。最终页面图片均完成加载。
2. 首轮对照发现卡片图像矩形底色明显、页面高度比参考超出约 26px（P2）。证据为 `.local/browser/portal-reference-desktop-round1.png`，1657 × 932，存在滚动条。调整图片混合方式、卡片与面板纵向间距后，在最终参考尺寸没有页面溢出；最终桌面截图为修正后证据。
3. 390px 下主导航发生横向滚动，插图英文标签与维护状态接近（P2）。缩小导航间隙、调整标签纵向位置后，`.main-nav.scrollWidth <= .main-nav.clientWidth + 1`，最终手机截图显示完整导航。
4. 1440px 下实体抽取平台名称溢出（P2）。允许中等屏幕长名称换行，统一标题区域高度；复核 1440px 与 1024px 均无标题或统计数值横向溢出。
5. 320px 下平台名称末尾出现单字换行（P2）。调整最窄屏标题字号及页头排列，最终窄屏截图中完整名称正常展示。

最终全图与两处局部对照未发现仍需阻断交付的 P0/P1/P2 问题。P3 差异：图标采用标准图标库；插画按参考重新生成，节点位置、机器人造型及卡片高光纹理不是逐像素复制。

## 实际验证

| 验证方式 | 观察结果 |
| --- | --- |
| `npm.cmd --prefix portal run test` | 正常权限环境中 8 项通过，0 失败；维护页、异常配置与 API 隔离验证通过 |
| `npm.cmd --prefix portal run check:source` | 三个子系统的原始导入树、当前子树及来源祖先检查通过 |
| `npm.cmd --prefix portal run build` | 最终构建通过；JS 82.32 kB，gzip 32.61 kB；CSS 21.83 kB，gzip 6.00 kB；维护页和状态快照正常生成 |
| `node --check scripts/Test-PortalBrowser.mjs` | 语法检查通过 |
| 应用内浏览器：搜索“爬虫” | 显示 1 张爬虫卡片 |
| 应用内浏览器：搜索无结果、清除筛选 | 显示空状态，清除后恢复 3 张卡片 |
| 应用内浏览器：更多关键词、大语言模型 | 更多/收起正常；选择大语言模型仅保留实体抽取平台 |
| 应用内浏览器：近 3 年/近 5 年 | 图表与替代说明同步切换，近 3 年为 2022–2024 |
| 应用内浏览器：帮助、通知与用户说明 | 显示真实接入计数和使用说明；关闭按钮与 Escape 均有效 |
| 应用内浏览器：数据资源、首页导航 | 分别跳转到 `#data-overview` 与 `#home` |
| 应用内浏览器：三个系统卡片和返回门户 | 分别显示对应维护页，返回后恢复系统目录 |
| 应用内浏览器：1672、1440、1024、390、320px | 无主要控件、系统名称或统计数值的横向溢出；桌面无破损图片 |
| 应用内浏览器日志 | 最终复核 error/warn 为空 |
| 素材格式验证 | 5 张图片约 9.15 MB 转为约 6.41 MB，减少 30%；透明度及所有可见像素一致 |

初次构建、Node 测试和来源检查在 Windows 沙箱中分别遭遇 `spawn EPERM` 或 `spawnSync git EPERM`，在正常权限环境重试后通过。启动命令 `.\scripts\Start-Integration.ps1 -System portal -Mode Development` 提示门户已经运行，因此复用了现有 Demo 预览，没有停止或替换该进程。

## 验证边界

- 本轮通过应用内浏览器完成实际交互检查。更新后的独立 Edge/Playwright 脚本只做了语法检查，没有把它表述为本轮已运行的端到端测试。
- 配置读取失败后的 UI 重试、请求超时、无 JavaScript 模式本轮未重新注入测试；原有读取和取消处理保持不变，已有脚本继续保留对应检查。可在原有本地 Edge/Playwright 环境执行：`node scripts/Test-PortalBrowser.mjs C:/Users/likecandy/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs`。
- 没有启用任何子系统，因此未验证子系统登录、真实采集、业务图谱或真实统计；这些工作不属于本次入口样式范围。
- 未进行 Firefox/Safari 或真实手机设备测试；不把 Chromium 视口模拟等同于所有浏览器兼容验收。

## 项目记忆同步

已读取 `docs/integration-baseline.md`、`docs/development.md`，并参考 `docs/integration-plan.md`、`docs/integration-acceptance.md` 和根目录 README。实际状态与“独立 Vue 门户、三个系统均维护、固定接入路径”的记录一致，没有发现本次涉及的记忆矛盾。

更新 `docs/development.md` 的入口视觉、响应式规则、演示数据边界、素材目录与交互验证方式。接入状态、来源 SHA 和子系统结构未变，因此不改写基线及历史验收结果；没有新增项目记忆体系。
