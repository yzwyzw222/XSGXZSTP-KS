# 已知限制

- 阶段8尚未完成：100,000条成果/1,000,000条图关系容量和四项P95已经实测通过；受控故障复验、真实OpenAlex/Crossref联合运行、业务备份与隔离恢复仍待用户在`Get-Credential`窗口输入凭据后实测。
- 当前主机MySQL80为8.0.41，低于文档兼容基线8.0.42一个补丁版本。阶段8隔离环境固定8.0.42；本机服务未升级，也未宣称两者一致。
- Codex派生进程可启动MySQL/Neo4j Testcontainers，但Neo4j Java Driver创建Netty selector时触发Windows AF_UNIX `Invalid argument: connect`。新完整`verify`因此为160项、0断言失败、36环境错误，不能记为通过；需在普通PowerShell原样重跑。
- Playwright的7项流程使用路由模拟，只证明浏览器交互回归，不替代真实账号、真实数据库和真实外部来源联合验收。
- 当前没有`lint`脚本；不得声称执行`npm run lint`。现有验证入口为Vitest、`vue-tsc -b`随生产构建执行、Vite构建和Playwright。
- 业务逻辑备份、SHA-256、7日/4周保留、独立MySQL恢复和独立Neo4j重建工具已经实现并通过静态解析；`E:\AACV_System_Backups`尚未创建，实际备份、隔离恢复、RPO 24小时和RTO 4小时仍未实测。本地阶段8不以备份静态加密作为验收项。
