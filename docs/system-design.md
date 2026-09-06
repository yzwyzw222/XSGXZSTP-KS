# 学术关系知识图谱构建平台 — 系统设计

> 状态：骨架版，随实现持续更新。代码与本文档冲突时，以验证后的当前实现为准。

## 1. 系统概述

面向计算机领域学术数据（论文、作者、机构、关键词），提供数据管理、学术知识图谱构建、多维度学术关系分析与可视化交互的 B/S 平台。

## 2. 技术栈

- 后端：Java 17、Spring Boot 3.5.x、Spring Security（Session + CSRF）、Spring Data JPA、Spring Data Neo4j 7（Neo4j 4.4）
- 存储：MySQL 8.0（权威数据源）、Neo4j 4.4（图投影，可重建）
- 前端：Vue 3.5、Vue Router、Element Plus、原生 fetch、ECharts、Cytoscape
- 接口文档：springdoc-openapi（`docs/openapi.yaml` 为契约基线）

## 3. 架构与数据一致性

- MySQL 保存用户、角色与学术实体主数据；Neo4j 保存可重建的图投影。
- 跨存储写入：MySQL 事务内记录 Outbox，后台任务幂等投影到 Neo4j（TODO：实现）。
- 受并发写影响的资源使用 `version` 乐观锁，冲突返回 409。

## 4. 功能模块

1. 认证与权限：注册、登录、登出、会话恢复；角色：ADMIN（管理员）、ANALYST（普通用户）。
2. 学术数据管理：论文、作者、机构、关键词四类实体的增删改查（分页、搜索）。
3. 图谱构建与导入：数据导入 → Neo4j 图投影（合作、引用、从属、主题关联）。
4. 多维分析：作者合作网络（强度/聚类）、引用分析（热度/路径）、主题演化（按年关键词趋势）、机构影响力。
5. 可视化：Cytoscape 力导向图（≤300 节点，查询/筛选/缩放/钻取）+ ECharts 图表。

## 5. 数据模型（TODO：细化）

- MySQL 表：`users`、`roles`、`user_roles`、`papers`、`authors`、`institutions`、`keywords`、关联表、Outbox。
- 图模式（Neo4j）：节点 `(:Author)`、`(:Paper)`、`(:Institution)`、`(:Keyword)`；关系 `[:COOPERATES_WITH]`、`[:CITES]`、`[:AFFILIATED_WITH]`、`[:HAS_KEYWORD]`。

## 6. 安全设计

- HttpOnly `SESSION` Cookie（SameSite=Lax），登录成功后旋转 Session ID。
- 非安全方法必须携带 CSRF Header；Token 仅存前端内存。
- 后端为最终权限边界：Controller 与业务层执行角色、权限与对象级校验。
- 错误响应：`application/problem+json`（status/title/detail/instance/errorCode/traceId/fieldErrors），不泄露堆栈/SQL/Cypher/口令。

## 7. API 契约

- 基础路径 `/api/v1`；成功响应直接返回 DTO 或分页对象，无通用包装。
- 分页：`{items,page,size,totalElements,totalPages}`，`page` 从 0 起，`size` 默认 20 最大 100。
- 详见 `docs/openapi.yaml` 与 `docs/authorization-matrix.md`。
