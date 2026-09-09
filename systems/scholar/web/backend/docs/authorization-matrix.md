# AACV 权限矩阵

## 角色定义

| 角色 | 描述 |
|------|------|
| ADMIN | 系统管理员，拥有全部权限 |
| OPERATOR | 操作员，可执行日常业务操作 |
| VIEWER | 只读用户，只能查看数据 |

## 权限矩阵

| 功能 | 端点 | 方法 | ADMIN | OPERATOR | VIEWER |
|------|------|------|-------|----------|--------|
| **认证** | | | | | |
| 获取 CSRF | `/api/v1/auth/csrf` | GET | ✓ | ✓ | ✓ |
| 登录 | `/api/v1/auth/login` | POST | ✓ | ✓ | ✓ |
| 退出 | `/api/v1/auth/logout` | POST | ✓ | ✓ | ✓ |
| 当前用户 | `/api/v1/auth/me` | GET | ✓ | ✓ | ✓ |

## 授权规则

- 所有 `/api/v1/**` 接口默认需要认证
- 登录和 CSRF 接口公开可访问
- 对象级权限在 application service 层通过 `AuthorizationPolicy` 校验
- 前端隐藏路由和菜单仅用于交互提示，不作为安全边界