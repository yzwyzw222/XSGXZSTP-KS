---
name: aacv-frontend-style-spec
description: Implement or review AACV System Vue 3 interfaces and styling. Use when changing layouts, CSS tokens, Element Plus components, responsive behavior, accessibility, authentication UI, request states, charts, graphs, or frontend tests.
---

# AACV 前端样式规范

## 使用前准备

1. 读取 [references/frontend-style.md](references/frontend-style.md)。
2. 检查当前 `frontend/src/style.css`、应用布局、目标 View、`api.ts`、`session.ts`、路由配置与相关测试。
3. 以当前代码和视觉令牌为准复用既有结构；发现文档与实现不一致时先验证，不凭印象扩展设计系统。

## 技术与结构边界

- 保持 Vue 3、Vue Router、Element Plus、原生 `fetch` 的现有组合。
- 未经明确授权，不引入 Pinia、Axios、另一套 UI 框架、自动导入插件或新的图表库。
- 全局视觉令牌和通用组件样式归入现有样式入口；页面私有样式与页面组件保持同一责任边界。
- 请求逻辑、Session 状态和业务视图分离，页面不直接复制 CSRF、超时或错误解析逻辑。

## 视觉实现规则

- 使用现有深海蓝底、亮蓝主色与青绿色成功色；颜色必须通过 CSS 变量引用，避免页面散落硬编码。
- 桌面端保持 228px 固定侧栏与 78px 吸顶顶栏；内容区以清晰的卡片层级、紧凑表格和可扫描状态为主。
- 组件圆角控制在 4px 至 10px；间距以 4px 倍数为主，常用内容间距为 12px 或 16px。
- 保持信息密度适合桌面管理台，不用营销页式巨型标题、浮夸渐变或装饰性玻璃拟态替代业务层级。
- Element Plus 组件通过已有变量和局部 class 定制，避免深层、脆弱的全局选择器覆盖。

## 状态与交互

- 每个异步区域明确呈现 loading、empty、error、permission denied、success 等状态，禁止只用空白表示失败。
- 401 清理会话并进入登录流程；403 保留用户上下文并展示无权限状态；409 提醒刷新最新数据后再操作。
- 登录页不得承诺或实现本地保存 Token；Session Cookie 由浏览器管理，CSRF Token 只保存在内存。
- 取消重复请求，使用项目既有超时与 `AbortController` 机制；组件卸载时清理定时器、动画帧、请求、图表和图实例。
- 动画保持克制，并为 `prefers-reduced-motion` 提供可用替代。

## 可访问性与响应式

- 文本与背景保持足够对比度；焦点样式清晰，键盘可达，表单控件具有标签和可理解错误提示。
- 状态不能只靠颜色表达，应同时使用文本、图标或形状。
- 按 1180px、900px、680px、480px 断点逐步压缩布局；小屏优先保证操作可达和信息可读，不机械缩小全部元素。
- 图表和关系图提供标题、图例、文字摘要或替代表达；Cytoscape 节点数量维持项目既有 300 上限。

## 验证流程

1. 运行受影响组件、请求层或 Session 状态的聚焦 Vitest 测试。
2. 运行前端完整测试与构建命令，以项目 `package.json` 为准。
3. 在桌面宽度及 1180px、900px、680px、480px 临界范围检查溢出、遮挡、键盘操作和状态切换。
4. 验证 `prefers-reduced-motion`、网络失败、401、403、409、空数据和长文本场景。
5. 只报告实际运行并观察到的测试、构建和视觉检查结果。

## 禁止事项

- 不在 `localStorage`、`sessionStorage` 或业务状态中保存 Session ID、Cookie 或长期认证 Token。
- 不以隐藏按钮代替后端权限校验，也不在 UI 中泄露敏感错误详情。
- 不用大范围 `!important`、全局标签选择器或重复硬编码颜色解决局部样式问题。
- 未经明确需求，不重构全站样式、不替换组件库、不修改既有公共 API 或构建系统。