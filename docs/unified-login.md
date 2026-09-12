# 系统登录与账号管理

更新日期：2026-09-12。项目只保留学术成果信息采集及可视化系统。指定项目记忆仍是 `integration-baseline.md` 和 `development.md`。

打开根地址自动进入 `/crawler/`，未登录时进入 `/crawler/login`。使用现有 crawler 账号；账号管理为 `/crawler/users`。首次初始化不自动创建用户，手动 admin 建号步骤见 [README](../README.md)。没有新增账号库或变更密码。

## 会话与权限

成果前端使用原生会话 store、路由守卫及用户菜单。网关沿用根路径 `PORTAL_SESSION` HttpOnly、SameSite=Lax Cookie，仅将合法且唯一的会话转换为后端 `CRAWLER_SESSION`。这些内部名称用于兼容已有会话，与已删除的门户页面无关。

- `/crawler/api/v1/auth/csrf`、`me` 通过现有代理访问 crawler。
- `/crawler/api/v1/auth/login`、`logout` 转入已有认证代理，保留 Origin、CSRF、会话轮换与错误处理。
- `/__integration/auth/*` 保留兼容；不接受其他系统的 Cookie 作为成果系统身份。
- 后端账号状态和 USER_LIST、AUDIT_READ 等权限仍由原服务实时验证。操作与登录日志沿用后端审计接口，网关请求日志功能已移除；返回静态页面不等同于获得数据权限。
- 登录前保存业务深链接，刷新重新读取会话；退出清理用户数据与请求，服务端会话失效。

## 旧地址与管理页面

| 原地址 | 当前地址 |
| --- | --- |
| `/` | `/crawler/` |
| `/login` | `/crawler/login` |
| `/management/users` | `/crawler/users` |
| `/management/users/overview` | `/crawler/users/overview` |
| `/management/logs` | `/crawler/logs` |
| `/management/audits` | `/crawler/logs` |

旧 `/?workspace=...` 书签只接受现有业务或管理路径，外站、编码路径逃逸、API 地址和登录循环被拒绝。relation、extraction、scholar 的页面及 API 均为 404。成果系统本身的学术关系图谱继续保留。

原多系统统一登录浏览器脚本已由 `scripts/Test-SingleSystemBrowser.mjs` 的完整单系统生产页面验证替代。该测试使用模拟账号和后端；真实账号、真实数据运行验收未在本轮执行。更多结果见 [单系统调整](single-system.md)。
