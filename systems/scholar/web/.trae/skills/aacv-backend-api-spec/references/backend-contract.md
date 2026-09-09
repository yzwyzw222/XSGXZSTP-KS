# AACV 后端接口契约参考

## 1. 基础约定

- 基础路径：`/api/v1`
- 数据格式：JSON，错误响应除外；错误使用 `application/problem+json`
- 时间：使用带时区的 ISO 8601 字符串并统一按 UTC 解释
- 成功响应：直接返回资源 DTO、集合或分页对象，不套通用响应外壳
- 分页查询：`page` 从 0 开始；`size` 默认 20，最大 100

分页响应固定结构：

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

## 2. 登录、Session 与 CSRF

浏览器端标准流程：

1. `GET /api/v1/auth/csrf`，接收 CSRF Token 并建立同源 `SESSION` Cookie。
2. `POST /api/v1/auth/login`，请求携带同一个 Cookie，并通过响应约定的 Header 发送 Token。
3. 登录成功后旋转 Session ID，将认证上下文保存到 Session。
4. `GET /api/v1/auth/me` 恢复当前用户、角色和权限集合。
5. 所有 `POST`、`PUT`、`PATCH`、`DELETE` 请求都携带当前 CSRF Header。
6. `POST /api/v1/auth/logout` 使用同样的 CSRF 保护，并清理客户端会话状态。

关键约束：

- 使用 HttpOnly `SESSION` Cookie；当前本地配置为 `SameSite=Lax`、`httpOnly=true`、`secure=false`。
- 生产 HTTPS 环境必须启用 Cookie `Secure`，并校验代理转发与同源配置。
- 前端不得读取、持久化或记录 Session ID；不得把 Session 改成 Authorization Header。
- CSRF Token 只放在内存状态中；401 后清理会话和 CSRF 缓存，再按产品流程回到登录页。
- 登录失败返回统一提示，避免暴露用户名是否存在、账户状态细节或口令校验差异。
- 账户状态、口令或角色权限发生安全相关变化后，应使既有 Session 失效。

## 3. 授权边界

- Spring Security 负责认证过滤和基础路由保护。
- `AuthorizationPolicy` 与 application service 负责角色、细粒度权限和对象级规则。
- 前端隐藏按钮或路由守卫不是安全边界；每个敏感后端操作都必须独立校验。
- 对导出文件、项目数据和受限管理资源，校验资源创建者、所属关系或管理员权限。
- 403 只表示当前已登录用户无权执行操作，不能通过模糊 404 代替全部授权决策，除非已有契约明确要求。

## 4. 错误模型

基础结构：

```json
{
  "status": 400,
  "title": "Bad Request",
  "detail": "请求参数不合法",
  "instance": "/api/v1/example",
  "errorCode": "VALIDATION_FAILED",
  "traceId": "0123456789abcdef0123456789abcdef",
  "fieldErrors": [
    {"field": "name", "message": "不能为空"}
  ]
}
```

状态语义：

| 状态码 | 语义 | 客户端处理 |
|---|---|---|
| 400 | 请求或字段校验失败 | 展示安全提示并定位字段 |
| 401 | 未登录、Session 失效 | 清空本地会话状态并重新登录 |
| 403 | 已登录但缺少角色、权限或对象权限 | 保留当前页，展示无权限状态 |
| 404 | 资源不存在 | 展示空结果或返回列表 |
| 409 | 乐观锁、重复操作或业务状态冲突 | 刷新最新状态后决定是否重试 |
| 410 | 导出结果已过期或不再可用 | 重新提交导出任务 |
| 422 | 请求格式有效但业务规则不满足 | 展示可修复的业务提示 |
| 503/504 | 图数据库或其他依赖不可用、超时 | 展示降级状态并保留重试入口 |

错误处理必须：

- 返回 32 位十六进制 `traceId`，并在响应 Header 中携带相同标识。
- 保持错误文本面向用户且不包含堆栈、SQL、Cypher、类名、文件路径或密钥。
- 将底层异常转换为稳定的领域或 API 错误码，不能直接透传数据库异常。

## 5. 数据一致性与异步接口

- MySQL 是业务权威数据源；Neo4j 仅保存可重建的图投影。
- 需要跨存储写入时，先在 MySQL 事务内记录 Outbox，再由后台任务幂等投影到 Neo4j。
- 受并发写影响的资源沿用 `version` 字段和乐观锁；冲突返回 409，不做无界重试。
- 异步导出提交接口返回 202；状态接口公开排队、运行、成功、失败和过期；下载接口校验创建者或管理员身份。
- 单次导出保持项目规定的 10,000 条上限，避免无界内存和长事务。

## 6. Controller 与 DTO 评审清单

- 输入使用 Bean Validation 并补充跨字段或业务规则校验。
- DTO 不直接暴露持久化实体、口令摘要、内部状态或安全上下文。
- URL、枚举、排序字段、分页大小和批量数量使用白名单或显式边界。
- Controller 只完成协议适配、鉴权入口和调用编排；业务规则归属 application/domain 层。
- 无权限、资源不存在、冲突和依赖失败路径均有测试。
- 非安全方法具备 CSRF 测试；对象级权限至少覆盖所有者、非所有者和管理员。
- OpenAPI 与实现同步，示例和状态码不包含虚构包装或 Bearer 认证。

## 7. 推荐验证顺序

1. 运行受影响 Controller、security 或 application service 的聚焦测试。
2. 检查 `docs/openapi.yaml` 与实际 DTO 字段、状态码、媒体类型一致。
3. 运行 `\.\mvnw.cmd -f .\backend\pom.xml verify`。
4. 对登录、CSRF、401、403、409、导出权限和依赖异常执行回归验证。