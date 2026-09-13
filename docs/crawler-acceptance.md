# 当前系统验证指南

当前有效系统为 crawler，来源 `feature/Luo` 的固定 SHA 为 `3a0127019052c182962d6630981e0acec2d66d53`，集成路径 `/crawler/`。此文件保留运行配置要求的验收文档入口，描述当前检查范围，不把旧多系统验收当作当前版本通过证据。

## 自动化验证

- 前端：Vitest 单元测试、TypeScript/Vite 构建、Playwright 登录及业务路由回归。
- 后端：普通单元测试、OpenAPI 契约、Flyway V1～V18 及历史版本升级；Testcontainers 隔离 MySQL/Neo4j 验证作者导入、导师编目、图投影、导出和权限。
- 兼容清理：共享 Batch/Quartz 配置仍可使用，旧采集和 ORCID Job 仅清理原调度；Payload 清理保持有界、保留证据，不重新启用采集。
- 网关：`node --test scripts/tests/*.test.mjs scripts/lib/source-adaptations.test.mjs`。
- 来源：`node scripts/check-source.mjs`，未提交的其他源码变化需要单独核对。

命令及环境要求见[开发说明](development.md#测试与检查)。Docker 不可用、依赖不足或权限受限时应报告阻塞，不能跳过有效测试后声称全部通过。

## 运行验收

启动授权范围内的组件后，检查网关与后端 readiness、图数据库健康，使用真实账号验证登录、权限、导入、检索和三类图谱。确认网关实际返回的前端文件与预期产物一致，运行 JAR 与构建版本一致。

前端模拟接口测试不证明真实数据库行为；匿名登录跳转不证明图谱已绘制；构建成功不等于服务已经更新。构建脚本使用 `-DskipTests`，测试需独立执行。公网、HTTPS 及 Nginx 模板必须另行验收，当前默认入口是本机 Node/Vite 网关。
