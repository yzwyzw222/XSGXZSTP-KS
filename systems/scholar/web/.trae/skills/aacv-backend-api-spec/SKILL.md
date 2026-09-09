---
name: aacv-backend-api-spec
description: Implement or review AACV System Spring Boot REST APIs. Use when changing endpoints, DTOs, pagination, session authentication, CSRF, authorization, error responses, OpenAPI contracts, exports, or backend API tests.
---

# AACV 后端接口规范

## 使用前准备

1. 先读取当前项目的 `docs/openapi.yaml`、`docs/authorization-matrix.md` 和 `docs/system-design.md` 中相关章节。
2. 再检查涉及的 Controller、application service、domain model、security 配置与现有测试；文档与代码冲突时，以验证后的当前实现为准。
3. 读取 [references/backend-contract.md](references/backend-contract.md)，将其中契约作为本项目接口设计与评审基线。

## 必须保持的契约

- 所有业务接口位于 `/api/v1`；成功响应直接返回资源 DTO 或分页对象，不增加通用 `ApiResponse` 包装层。
- 分页对象固定为 `items`、`page`、`size`、`totalElements`、`totalPages`；默认 `size=20`，最大 `size=100`。
- 登录鉴权使用服务端 Session 与 CSRF，不引入 JWT 或 Bearer Token。浏览器必须携带同源 Cookie，非安全方法必须发送服务端下发的 CSRF Header。
- 前端路由和菜单控制只提供交互提示；Controller 和业务层必须完成角色、权限及对象级授权，后端是最终权限边界。
- 错误响应使用 `application/problem+json`，至少包含 `status`、`title`、`detail`、`instance`、`errorCode`、`traceId`；字段校验错误补充 `fieldErrors`。
- 在 API 边界校验输入，不向客户端或日志泄露堆栈、SQL、Cypher、口令、Session ID、Cookie 或 CSRF Token。
- 需要并发保护的更新沿用 `version` 乐观锁；MySQL 是权威数据源，Neo4j 是可恢复投影，跨存储同步沿用事务 Outbox。
- 异步导出沿用提交任务、查询状态、下载结果的三段式接口；保持数量上限、创建者或管理员访问控制与到期语义。

## 实施流程

1. 从 OpenAPI、权限矩阵和现有调用方确认输入、输出、状态码与兼容性。
2. 在既有 `api`、`application`、`domain`、`infrastructure` 边界内实现最小变更，避免把业务规则放入 Controller。
3. 为角色权限、对象级权限、非法输入、冲突、未登录、CSRF 失败和依赖异常补齐相应路径。
4. 同步 `docs/openapi.yaml` 及受影响的权限或设计文档；不要制造代码与契约的双重事实源。
5. 先运行受影响的聚焦测试，再运行 `\.\mvnw.cmd -f .\backend\pom.xml verify`；只报告实际执行并观察到的结果。

## 禁止事项

- 不把现有 Session 鉴权改成 JWT、Bearer Token 或本地存储 Token。
- 不新增通用响应包装、任意远程 URL 接口、未经约束的批量操作或绕过后端授权的捷径。
- 未经明确授权，不修改数据库模式、依赖版本、认证模型、公共接口兼容性、部署配置或远程资源。
- 不吞掉异常、不弱化验证、不把敏感详情返回给客户端，也不为通过测试而删除或放宽有效断言。

## 交付检查

- 接口实现、OpenAPI、权限矩阵和测试相互一致。
- 401、403、409、字段校验、资源不存在及依赖故障语义明确且可验证。
- 写操作具备 CSRF、防越权和必要的并发保护。
- 响应携带可关联的 `traceId`，同时不泄露敏感信息。
- 变更保持最小、可回滚，并符合项目既有分层与命名约定。