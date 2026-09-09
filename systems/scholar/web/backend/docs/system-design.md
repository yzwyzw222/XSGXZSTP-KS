# AACV 系统设计

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                    前端 (Vue 3 + Element Plus)               │
│             原生 fetch → Session Cookie + CSRF               │
└────────────────────────────────┬────────────────────────────┘
                                 │ /api/v1
                                 ▼
┌─────────────────────────────────────────────────────────────┐
│                  后端 (Spring Boot 3.x)                      │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌──────────┐ │
│  │ Controller │→│ Service   │→│ Domain    │→│ JPA Repo │ │
│  │ (API)      │  │ (App)     │  │ (Model)   │  │ (Infra)  │ │
│  └───────────┘  └───────────┘  └───────────┘  └──────────┘ │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Security: Session Auth + CSRF + AuthorizationPolicy │   │
│  └──────────────────────────────────────────────────────┘   │
└────────────────────────────────┬────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    ▼                         ▼
              ┌──────────┐            ┌──────────┐
              │  MySQL   │            │  Neo4j   │
              │ (Primary)│            │ (Graph)  │
              └──────────┘            └──────────┘
```

## 分层职责

| 层 | 包 | 职责 |
|----|----|------|
| API | `api` | Controller、DTO、请求/响应适配、参数校验 |
| Application | `application` | 业务编排、事务管理、鉴权委托 |
| Domain | `domain` | 核心业务模型、枚举、Repository 接口 |
| Infrastructure | `infrastructure` | 安全配置、JPA 实现、外部依赖集成 |

## 安全模型

- 认证：服务端 Session + HttpOnly Cookie
- CSRF：Cookie-based Token，非安全方法需携带 Header
- 授权：Spring Security 路由保护 + `AuthorizationPolicy` 细粒度校验
- 密码：BCrypt 加密

## 数据存储

- MySQL：业务权威数据源
- Neo4j：可恢复的图投影（从 MySQL 通过 Outbox 同步）
- 并发控制：`@Version` 乐观锁

## 关键约定

- 基础路径：`/api/v1`
- 成功响应：直接返回资源 DTO，无通用包装
- 错误响应：`application/problem+json`
- 分页：`items`、`page`、`size`、`totalElements`、`totalPages`
- 时间：ISO 8601 UTC