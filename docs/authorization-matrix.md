# 权限矩阵

> 与实现同步（2026-09-09 对照 SecurityConfig 与各 Controller 核对）。
> 前端隐藏按钮只是交互提示，后端校验才是最终权限边界。

## 角色

| 角色 | 编码 | 说明 |
|---|---|---|
| 管理员 | `ADMIN` | 管理用户与角色、数据导入、在线爬取、实体抽取触发、手动触发图谱同步，同时拥有 ANALYST 全部权限 |
| 普通用户 | `ANALYST` | 学术数据增删改查、图谱与分析查询（注册默认授予） |

角色来自 `sys_user_role` → `sys_role`，由 `DatabaseUserDetailsService` 加载为 `ROLE_ADMIN` / `ROLE_ANALYST`。

## 资源 × 角色

| 资源/操作 | 匿名 | ANALYST | ADMIN |
|---|---|---|---|
| 注册 / 登录 / 获取 CSRF Token | ✓ | ✓ | ✓ |
| 当前用户 /me、登出 | ✗ | ✓ | ✓ |
| 论文 / 作者 / 机构 / 关键词 / 渠道 增删改查 | ✗ | ✓ | ✓ |
| 图谱分析查询（合作/引用/主题演化/机构影响力） | ✗ | ✓ | ✓ |
| 抽取图谱数据（`/extraction/graph-data`，只读） | ✗ | ✓ | ✓ |
| 用户与角色管理（`/admin/users*`） | ✗ | ✗ | ✓ |
| 数据导入（`/admin/import/papers`） | ✗ | ✗ | ✓ |
| 在线爬取（`/admin/crawl/openalex`） | ✗ | ✗ | ✓ |
| 实体抽取触发与查询（`/admin/extraction/*`） | ✗ | ✗ | ✓ |
| 手动触发图谱同步（`/admin/sync-graph`） | ✗ | ✗ | ✓ |
| Swagger UI / openapi 文档 | ✓ | ✓ | ✓ |

## 对象级规则

- 当前版本**不做"仅能维护自己创建的记录"限制**：任何登录用户都能增删改全部学术数据（对象级所有权留待二期启用，`created_by` 字段已预留）。
- 401 表示未登录或会话失效（`AUTH_REQUIRED`）；403 表示已登录但无权限（`FORBIDDEN`），两者不混淆。
- 角色校验在 `SecurityConfig` 以路径前缀兜底（`/api/v1/admin/**` 要求 ADMIN），业务层另有 `@PreAuthorize` 可做更细粒度控制。

## CSRF 规则

- 所有非安全方法（POST/PUT/DELETE）必须带 `X-CSRF-TOKEN` 请求头，否则 403（防跨站请求伪造）。
- 例外（`csrf.ignoringRequestMatchers`）：`/api/v1/auth/login`、`/api/v1/auth/register` —— 这两个接口在登录前调用，且本身无害。
- Token 获取：`GET /api/v1/auth/csrf` 返回 `{token}` 并下发 `XSRF-TOKEN` Cookie；前端只把 Token 存内存。
