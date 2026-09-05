# 已知限制

- 历史阶段8运营验收尚未全部关闭。2026-09-05已取得真实OpenAlex/Crossref重复采集、临时依赖断连恢复、隔离合成数据逻辑恢复及六项十万条SQL性能证据；当前结果以`optimization-acceptance.md`为准。这些测试不等同于实际业务备份目录的ACL、保留策略和RPO/RTO验收。
- 当前主机MySQL80为8.0.41，低于文档兼容基线8.0.42一个补丁版本。阶段8隔离环境固定8.0.42；本机服务未升级，也未宣称两者一致。
- Windows默认Java临时目录曾使Neo4j Java Driver创建Netty selector时触发AF_UNIX `Invalid argument: connect`。2026-09-05通过成对selector实验定位，进程指定可写短目录后通信恢复。启动脚本使用项目内`.local/java-sockets`；测试使用`-Djdk.net.unixdomain.tmpdir=F:\Program\Java\AACV_System\backend\target`，其他环境应替换路径。本轮完整后端验证结果见`optimization-acceptance.md`，不再将V12前的163项结果视为当前证明。
- Playwright浏览器流程使用路由模拟，只证明浏览器交互回归，不替代真实账号、真实数据库和真实外部来源联合验收。优化阶段结果见`optimization-acceptance.md`；账号与日志增量的最新测试结果见`account-management-acceptance.md`。
- 当前没有`lint`脚本；不得声称执行`npm run lint`。现有验证入口为Vitest、`vue-tsc -b`随生产构建执行、Vite构建和Playwright。
- 业务备份工具允许V11至V14，支持可选PSCredential且保留交互输入；Windows PowerShell 5.1语法通过。2026-09-05已按后续授权完成真实V13业务备份，受限目录ACL、每日/每周副本SHA-256及转储完成标记通过，随后实际应用V14；证据见`account-upgrade-backup-evidence.json`。实际业务恢复、7日/4周自动保留及RPO 24小时/RTO 4小时仍未实测。本地阶段8不以备份静态加密作为验收项。
- 每个采集任务仍最多5页、500条，固定范围复查不等于全量同步；没有自动移动窗口、来源删除对账或全球采集完整性保证。来源学术字段和机构旧名称需复查采集后逐步补齐。
- 最终六项SQL测量为单并发合成数据，不能替代真实分布、高并发或HTTP性能验证；覆盖查询较优化前增加开销，具体原始样本已保留。

- V14升级后旧格式会话需要重新登录一次；资料修改保留会话，角色、状态或密码变化使旧会话失效。日志IP为服务器实际连接地址，未配置可信代理解析；User-Agent仅代表客户端声明。历史日志缺少的来源信息不回填，未增加日志自动清理、导出或地理定位。
- 2026-09-05实际升级检查时，Neo4j容器健康探针标为unhealthy，但后端graph健康接口为UP；探针配置原因尚未处理。实际管理员登录未完成复验，因为本地配置没有应用管理员密码；数据库凭据有效，原账号密码未修改。
