# 账号管理与日志验收记录

本文件主体记录功能实现阶段的范围与验证。后续用户授权的实际备份、启动和V14升级结果见文末“实际备份与启动”。

## 范围与结果

2026-09-05，按用户确认的方案完成账号管理与日志增量。沿用 Spring Boot、MyBatis、Vue、ECharts 和现有 UI 组件，不增加依赖。保留任务开始前的未提交修改，不执行提交、部署、业务数据库迁移、实际备份恢复或样例数据注入。

| 功能 | 最终行为 |
| --- | --- |
| 日志层 | 复用 `audit_log`；登录成功、失败和退出归为 LOGIN，其余归为 OPERATION。`/logs` 提供分类、账号字面关键字、时间、结果、操作类型及服务端分页，沿用 AUDIT_READ；原运行监控审计入口保留。 |
| 账号概览 | 顶部左侧环形图，右侧最近 10 条登录活动。数据库统计所有状态及所有分页的账号，按 ADMIN → DATA_OPERATOR → RESEARCHER 最高角色每人计一次。列表、统计、日志独立加载及报错。 |
| 统一编辑 | Sheet 一次提交资料、角色、状态和预期版本，用户名只读，密码单独重置。409 保留输入，由用户主动重新加载。原角色、启停和密码接口保留。 |
| 用户资料 | 姓名、邮箱、联系电话、所属单位、部门/院系、备注保存在 sys_user；新建页面要求姓名，旧创建 API 和初始管理员引导允许省略资料，历史账号不伪造资料。 |

资料长度依次为 64、254、32、128、128、500 字符，前后端共同验证邮箱、电话、长度和控制字符。备注允许换行。资料不自动关联学术作者或机构实体。

## 事务、安全与兼容约定

- `PUT /api/v1/users/{userId}` 要求管理员、USER_UPDATE、CSRF，在一个事务中更新资料、角色、状态及成功审计。无变化保存不推进版本；角色未变化时保留原分配时间。
- `version` 用于乐观并发控制，`security_version` 用于会话有效性。资料更新保持登录；角色、状态、密码变化推进安全版本。旧序列化会话缺少新字段时返回现有会话过期响应，升级后需重新登录一次。
- 所有相关账号安全写入口先锁固定 ADMIN 角色行，再读取和验证用户，保护当前管理员不自我停用或降权，并防止并发移除最后一个可用管理员。创建只增加账号，不参与移除检查。
- 成功审计保留既有业务事务关系。关键变更和导出下载接口的失败请求由显式端点列表记录为 OPERATION_FAILED，独立事务保存安全错误码和原操作标识。无效超长目标标识不写入，仍记录所属操作。
- 如果成功审计写入失败，业务更新回滚；如果失败审计本身不可写，保留原失败响应并输出只含 traceId 与异常类型的故障日志。不会输出异常正文、完整请求体、资料全文、密码或会话信息。
- 来源 IP 取服务器连接地址，不直接信任转发头；User-Agent 清理控制字符并限制为 512 字符。历史缺失值显示 `--`。来源声明不作为可信设备身份。
- 异步事件的“请求已受理/已提交”与任务完成分别显示；失败请求不会同时显示“已受理”。原操作类型筛选同时包含对应失败事件。
- V14 只新增可空资料列、安全版本、来源列及审计排序索引，不改写 V1–V13。备份允许版本为 11、12、13、14，未知版本仍拒绝。样例初始化工具同步要求完整 V1–V14，保留原本机目标和凭据边界。

## 已执行验证

以下命令从项目根目录运行，日期为 2026-09-05。表中只记录实际观察到的结果。

| 命令 | 结果与范围 |
| --- | --- |
| `.\mvnw.cmd -f .\backend\pom.xml -DskipTests compile` | 实施过程编译通过。 |
| `.\mvnw.cmd -f .\backend\pom.xml "-Djdk.net.unixdomain.tmpdir=F:\Program\Java\AACV_System\backend\target" "-Dtest=UserAccountServiceTests,SecurityIntegrationTests,AuditRecordTests,FlywayMigrationTests" test` | 早期定向 36 项通过。 |
| `.\mvnw.cmd -f .\backend\pom.xml "-Djdk.net.unixdomain.tmpdir=F:\Program\Java\AACV_System\backend\target" verify` | 完整 197 项通过，0 失败、0 错误、0 跳过，应用打包成功。 |
| `.\mvnw.cmd -f .\backend\pom.xml "-Dtest=FailedOperationAuditFilterTests,AuditQueryTests,AccountFreshnessFilterTests,UserProfileTests" test` | 最后补充超长失败目标和审计基础设施故障测试后，相关 8 项通过，其中 6 项与完整回归重叠。 |
| `.\mvnw.cmd -f .\backend\pom.xml -DskipTests package` | 最终定向验证后重新打包通过；本命令未运行测试。 |
| `npm --prefix .\frontend run test` | 最终 18 个文件、43 项测试全部通过。 |
| `npm --prefix .\frontend run build` | 最终 TypeScript 检查及生产构建通过；保留已有约 637 kB 图表包的体积提醒。 |
| `npm --prefix .\frontend run test:e2e` | 完整 24 项通过。 |
| `npm --prefix .\frontend run test:e2e -- account-management.spec.ts` | 最后补充离开页面后的迟到响应用例后，6 项账号与日志浏览器定向测试通过。 |
| PowerShell AST 静态检查（实际命令见文末） | Windows PowerShell 5.1.26100.9168 对两份备份脚本和样例初始化脚本语法检查通过；备份允许列表精确为 11、12、13、14，拒绝 10 和 15。未执行脚本的数据库操作。 |
| `python -c "import yaml; print(yaml.__version__)"` 及 `python -` 内联 YAML/引用检查 | 使用现有 PyYAML 6.0.3，OpenAPI YAML 解析及 360 个内部引用解析通过；新增模型位于 schemas，requestBodies 保留正确 content 结构。未新增依赖。 |
| `git diff --check` | 未发现差异空白错误；Windows 换行转换提示不作为测试失败。另对任务开始时文件快照与最终文件逐项比较，确认本次范围并保留已有修改。 |

后端集成测试使用实际应用逻辑、MockMvc、隔离 Testcontainers 数据库和会话基础设施，验证真实事务、SQL、迁移及认证规则。浏览器测试使用路由模拟 API，只证明交互、请求契约和响应处理，不作为连接实际业务库的端到端验收。

重点覆盖：V13 → V14 历史用户/角色/日志保留、空库迁移、旧创建 API、资料编辑后会话保留、安全变更失效、旧会话字段缺失、无变化保存、版本冲突、完整编辑回滚、失败审计独立事务、管理员自我保护及并发保护、全库多角色去重与零账号统计、权限与 CSRF、日志安全字段及来源清理、分类及筛选、迟到响应与卸载取消。桌面浅色、深色及 390 px 窄屏截图已人工复查；窄屏表格使用内部横向滚动，页面本身无横向溢出，抽屉和日志详情支持键盘关闭。

实施中的失败已区分处理：沙箱曾阻止 Vite/Vitest 子进程和 Docker 命名管道访问，获准在可访问环境运行后通过；最初类型检查发现的接口错误访问及未使用导入已修复；新增导航后侧栏数量断言已更新并补充日志入口断言。最终未遗留上述失败。

## 项目记忆同步

已读取 README、需求分析、系统设计、开发计划、权限矩阵、交接文档、备份恢复、已知限制、OpenAPI 和前期优化验收记录。未发现独立指定的项目记忆文件，未另建记忆体系。

本次在既有文档中同步新增 API、字段、权限、并发/安全版本、日志事务与来源口径、V14 工具边界和验收证据。通过 Mapper、过滤器、迁移、脚本条件和测试核对，发现原文档及样例工具仍以 V13 为当前上限，已同步为 V14；历史 V13 恢复结果保留历史身份，没有改写为 V14 恢复验证。前期优化文档标注为原阶段快照并链接本记录。

## 风险、限制与后续操作

- 真实业务库尚未应用 V14；升级前按既有备份流程准备，启动新版后端时 Flyway 执行迁移。不可直接在业务库执行测试 SQL 或样例注入。
- 后端升级后原会话需重新登录一次；之后仅资料更新不会要求重新登录。界面热更新不能代替后端升级。
- 本次没有执行实际业务备份、恢复、部署、可信代理 IP 解析、日志清理/导出、用户删除或用户名修改。
- 尚未执行浏览器连接实际业务后端的联合验收。已有图表分包体积提醒保留，未借此扩展依赖或构建优化范围。
- 使用现有启动流程启用新版后端与前端后，以管理员登录，访问“用户管理 → 账号管理”和“系统状态 → 日志管理”。生产升级及实际备份恢复需按既有操作文档另行执行。

## 本次变更文件

以下 81 个文件以任务开始时的工作区内容为比较基线；不将此前已有的优化修改、未跟踪文件或生成产物计入本次修改。

| 文件 | 本次变化 | 说明 |
| --- | --- | --- |
| [README.md](../README.md) | 修改 | 记录账号、日志功能及 V14 升级入口。 |
| [backend/src/main/java/com/aacv/system/identity/api/AuthController.java](../backend/src/main/java/com/aacv/system/identity/api/AuthController.java) | 修改 | 登录失败改用独立审计并避免重复记录。 |
| [backend/src/main/java/com/aacv/system/identity/api/CreateUserRequest.java](../backend/src/main/java/com/aacv/system/identity/api/CreateUserRequest.java) | 修改 | 创建请求增加可选资料，保留原字段兼容。 |
| [backend/src/main/java/com/aacv/system/identity/api/UpdateUserRequest.java](../backend/src/main/java/com/aacv/system/identity/api/UpdateUserRequest.java) | 新增 | 定义完整编辑请求、必需角色状态与版本。 |
| [backend/src/main/java/com/aacv/system/identity/api/UserController.java](../backend/src/main/java/com/aacv/system/identity/api/UserController.java) | 修改 | 增加统一编辑和全库统计端点。 |
| [backend/src/main/java/com/aacv/system/identity/api/UserResponse.java](../backend/src/main/java/com/aacv/system/identity/api/UserResponse.java) | 修改 | 响应附带六项用户资料。 |
| [backend/src/main/java/com/aacv/system/identity/application/AdminUserService.java](../backend/src/main/java/com/aacv/system/identity/application/AdminUserService.java) | 修改 | 新增 USER_UPDATE 与统计权限边界。 |
| [backend/src/main/java/com/aacv/system/identity/application/CreateUserCommand.java](../backend/src/main/java/com/aacv/system/identity/application/CreateUserCommand.java) | 修改 | 携带资料并保留旧构造调用。 |
| [backend/src/main/java/com/aacv/system/identity/application/UpdateUserCommand.java](../backend/src/main/java/com/aacv/system/identity/application/UpdateUserCommand.java) | 新增 | 封装版本、资料、角色与状态。 |
| [backend/src/main/java/com/aacv/system/identity/application/UserAccountService.java](../backend/src/main/java/com/aacv/system/identity/application/UserAccountService.java) | 修改 | 原子编辑、无变化保存、会话规则和管理员保护。 |
| [backend/src/main/java/com/aacv/system/identity/application/port/UserAccountRepository.java](../backend/src/main/java/com/aacv/system/identity/application/port/UserAccountRepository.java) | 修改 | 增加资料保存、统计及管理员锁接口。 |
| [backend/src/main/java/com/aacv/system/identity/domain/Permission.java](../backend/src/main/java/com/aacv/system/identity/domain/Permission.java) | 修改 | 新增管理员 USER_UPDATE 权限。 |
| [backend/src/main/java/com/aacv/system/identity/domain/UserAccount.java](../backend/src/main/java/com/aacv/system/identity/domain/UserAccount.java) | 修改 | 携带资料与安全版本，兼容已有构造调用。 |
| [backend/src/main/java/com/aacv/system/identity/domain/UserProfile.java](../backend/src/main/java/com/aacv/system/identity/domain/UserProfile.java) | 新增 | 统一资料归一化及格式、长度验证。 |
| [backend/src/main/java/com/aacv/system/identity/domain/UserStatistics.java](../backend/src/main/java/com/aacv/system/identity/domain/UserStatistics.java) | 新增 | 定义互斥角色人数和总数。 |
| [backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/MyBatisUserAccountRepository.java](../backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/MyBatisUserAccountRepository.java) | 修改 | 实现资料与角色更新、统计和固定锁。 |
| [backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/UserAccountMapper.java](../backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/UserAccountMapper.java) | 修改 | 新增持久化参数与查询接口。 |
| [backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/UserAccountRow.java](../backend/src/main/java/com/aacv/system/identity/infrastructure/persistence/UserAccountRow.java) | 修改 | 映射资料和安全版本字段。 |
| [backend/src/main/java/com/aacv/system/identity/infrastructure/security/AccountFreshnessFilter.java](../backend/src/main/java/com/aacv/system/identity/infrastructure/security/AccountFreshnessFilter.java) | 修改 | 按安全版本判断会话，并使旧格式会话过期。 |
| [backend/src/main/java/com/aacv/system/identity/infrastructure/security/SecurityConfiguration.java](../backend/src/main/java/com/aacv/system/identity/infrastructure/security/SecurityConfiguration.java) | 修改 | 接入失败操作审计过滤器。 |
| [backend/src/main/java/com/aacv/system/identity/infrastructure/security/UserPrincipal.java](../backend/src/main/java/com/aacv/system/identity/infrastructure/security/UserPrincipal.java) | 修改 | 保存可兼容反序列化的安全版本。 |
| [backend/src/main/java/com/aacv/system/operations/api/AuditController.java](../backend/src/main/java/com/aacv/system/operations/api/AuditController.java) | 修改 | 接收分类、账号、日期、结果和操作过滤。 |
| [backend/src/main/java/com/aacv/system/operations/api/AuditLogResponse.java](../backend/src/main/java/com/aacv/system/operations/api/AuditLogResponse.java) | 修改 | 增加分类、账号和来源响应字段。 |
| [backend/src/main/java/com/aacv/system/operations/application/AuditService.java](../backend/src/main/java/com/aacv/system/operations/application/AuditService.java) | 修改 | 独立事务写入失败审计、来源采集和分类查询。 |
| [backend/src/main/java/com/aacv/system/operations/application/port/AuditLogRepository.java](../backend/src/main/java/com/aacv/system/operations/application/port/AuditLogRepository.java) | 修改 | 扩展分页查询过滤契约。 |
| [backend/src/main/java/com/aacv/system/operations/domain/AuditAction.java](../backend/src/main/java/com/aacv/system/operations/domain/AuditAction.java) | 修改 | 登记统一编辑与操作失败事件。 |
| [backend/src/main/java/com/aacv/system/operations/domain/AuditCategory.java](../backend/src/main/java/com/aacv/system/operations/domain/AuditCategory.java) | 新增 | 从现有事件类型导出分类。 |
| [backend/src/main/java/com/aacv/system/operations/domain/AuditLogEntry.java](../backend/src/main/java/com/aacv/system/operations/domain/AuditLogEntry.java) | 修改 | 携带查询账号及来源信息。 |
| [backend/src/main/java/com/aacv/system/operations/domain/AuditQuery.java](../backend/src/main/java/com/aacv/system/operations/domain/AuditQuery.java) | 新增 | 验证分类、时间区间和字面账号筛选。 |
| [backend/src/main/java/com/aacv/system/operations/domain/AuditRecord.java](../backend/src/main/java/com/aacv/system/operations/domain/AuditRecord.java) | 修改 | 扩展可空来源并保留安全摘要校验。 |
| [backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/AuditLogMapper.java](../backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/AuditLogMapper.java) | 修改 | 增加审计过滤查询参数。 |
| [backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/AuditLogRow.java](../backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/AuditLogRow.java) | 修改 | 映射账号、IP 与 User-Agent。 |
| [backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/MyBatisAuditLogRepository.java](../backend/src/main/java/com/aacv/system/operations/infrastructure/persistence/MyBatisAuditLogRepository.java) | 修改 | 持久化来源并返回过滤后的分页。 |
| [backend/src/main/java/com/aacv/system/operations/infrastructure/web/AuditRequestMetadata.java](../backend/src/main/java/com/aacv/system/operations/infrastructure/web/AuditRequestMetadata.java) | 新增 | 采集连接地址并清理、限制客户端声明。 |
| [backend/src/main/java/com/aacv/system/operations/infrastructure/web/FailedOperationAuditFilter.java](../backend/src/main/java/com/aacv/system/operations/infrastructure/web/FailedOperationAuditFilter.java) | 新增 | 记录关键接口失败，隔离超长目标及审计故障。 |
| [backend/src/main/java/com/aacv/system/shared/infrastructure/web/ApiExceptionHandler.java](../backend/src/main/java/com/aacv/system/shared/infrastructure/web/ApiExceptionHandler.java) | 修改 | 提供安全错误码属性，非法查询枚举返回 400。 |
| [backend/src/main/java/com/aacv/system/shared/infrastructure/web/ProblemResponseWriter.java](../backend/src/main/java/com/aacv/system/shared/infrastructure/web/ProblemResponseWriter.java) | 修改 | 向审计层提供认证/授权错误码。 |
| [backend/src/main/resources/db/migration/V14__extend_user_profiles_and_audit_context.sql](../backend/src/main/resources/db/migration/V14__extend_user_profiles_and_audit_context.sql) | 新增 | 新增资料、安全版本、审计来源和排序索引。 |
| [backend/src/main/resources/mapper/identity/UserAccountMapper.xml](../backend/src/main/resources/mapper/identity/UserAccountMapper.xml) | 修改 | 实现全库去重统计、资料更新和管理员锁 SQL。 |
| [backend/src/main/resources/mapper/operations/AuditLogMapper.xml](../backend/src/main/resources/mapper/operations/AuditLogMapper.xml) | 修改 | 实现分类、账号和时间等过滤，关联账号与来源。 |
| [backend/src/test/java/com/aacv/system/AacvSystemApplicationTests.java](../backend/src/test/java/com/aacv/system/AacvSystemApplicationTests.java) | 修改 | 同步空库完整迁移版本断言。 |
| [backend/src/test/java/com/aacv/system/identity/api/AccountFreshnessFilterTests.java](../backend/src/test/java/com/aacv/system/identity/api/AccountFreshnessFilterTests.java) | 新增 | 验证资料修改、安全版本变化与旧会话。 |
| [backend/src/test/java/com/aacv/system/identity/api/SecurityIntegrationTests.java](../backend/src/test/java/com/aacv/system/identity/api/SecurityIntegrationTests.java) | 修改 | 覆盖编辑、权限、事务、日志、统计及管理员并发保护。 |
| [backend/src/test/java/com/aacv/system/identity/application/UserAccountServiceTests.java](../backend/src/test/java/com/aacv/system/identity/application/UserAccountServiceTests.java) | 修改 | 适配当前操作人依赖，保留原服务测试。 |
| [backend/src/test/java/com/aacv/system/identity/domain/UserProfileTests.java](../backend/src/test/java/com/aacv/system/identity/domain/UserProfileTests.java) | 新增 | 验证字段归一化、长度、格式及非法字符。 |
| [backend/src/test/java/com/aacv/system/infrastructure/database/FlywayMigrationTests.java](../backend/src/test/java/com/aacv/system/infrastructure/database/FlywayMigrationTests.java) | 修改 | 验证空库和 V13 升级历史数据保留。 |
| [backend/src/test/java/com/aacv/system/infrastructure/database/RenderingSampleDataSqlTests.java](../backend/src/test/java/com/aacv/system/infrastructure/database/RenderingSampleDataSqlTests.java) | 修改 | 验证样例 SQL 在 V14 完整迁移后的兼容性。 |
| [backend/src/test/java/com/aacv/system/operations/domain/AuditQueryTests.java](../backend/src/test/java/com/aacv/system/operations/domain/AuditQueryTests.java) | 新增 | 验证日志过滤、客户端长度和转发头边界。 |
| [backend/src/test/java/com/aacv/system/operations/infrastructure/web/FailedOperationAuditFilterTests.java](../backend/src/test/java/com/aacv/system/operations/infrastructure/web/FailedOperationAuditFilterTests.java) | 新增 | 验证超长失败目标、失败审计故障和安全错误日志。 |
| [docs/account-management-acceptance.md](../docs/account-management-acceptance.md) | 新增 | 记录本次结果、完整文件清单及验证与限制。 |
| [docs/authorization-matrix.md](../docs/authorization-matrix.md) | 修改 | 同步新权限、端点、会话和管理员保护。 |
| [docs/backup-and-recovery.md](../docs/backup-and-recovery.md) | 修改 | 说明 V14 允许列表与未执行实际恢复的边界。 |
| [docs/development-handoff.md](../docs/development-handoff.md) | 修改 | 同步当前完整迁移版本。 |
| [docs/development-plan.md](../docs/development-plan.md) | 修改 | 登记已批准的账号与日志增量及验收入口。 |
| [docs/known-limitations.md](../docs/known-limitations.md) | 修改 | 同步最新验证入口、V14 会话和日志来源限制。 |
| [docs/openapi.yaml](../docs/openapi.yaml) | 修改 | 定义新增端点、资料、统计与日志查询契约。 |
| [docs/optimization-acceptance.md](../docs/optimization-acceptance.md) | 修改 | 保留前期快照并指向最新账号日志验收。 |
| [docs/requirements-analysis.md](../docs/requirements-analysis.md) | 修改 | 登记已确认的四项增量需求。 |
| [docs/system-design.md](../docs/system-design.md) | 修改 | 同步资料、安全版本、审计事务与工具基线。 |
| [frontend/e2e/account-management.spec.ts](../frontend/e2e/account-management.spec.ts) | 新增 | 覆盖账号表单、冲突、日志、局部失败、迟到响应和主题布局。 |
| [frontend/src/components/business/AppSidebar.test.ts](../frontend/src/components/business/AppSidebar.test.ts) | 修改 | 校验新增日志入口与导航数量。 |
| [frontend/src/components/business/AuditLogTable.vue](../frontend/src/components/business/AuditLogTable.vue) | 新增 | 复用分页日志表及来源、traceId 详情。 |
| [frontend/src/components/business/UserProfileFields.vue](../frontend/src/components/business/UserProfileFields.vue) | 新增 | 复用新增与编辑表单字段。 |
| [frontend/src/components/business/UserRoleChart.test.ts](../frontend/src/components/business/UserRoleChart.test.ts) | 新增 | 验证互斥人数文案和空数据。 |
| [frontend/src/components/business/UserRoleChart.vue](../frontend/src/components/business/UserRoleChart.vue) | 新增 | 展示环形图、总数、人数占比与统计口径。 |
| [frontend/src/config/nav.ts](../frontend/src/config/nav.ts) | 修改 | 在系统状态组增加权限受控的日志入口。 |
| [frontend/src/router/index.ts](../frontend/src/router/index.ts) | 修改 | 增加受 AUDIT_READ 保护的日志路由。 |
| [frontend/src/services/audits.ts](../frontend/src/services/audits.ts) | 新增 | 封装服务端日志过滤与取消信号。 |
| [frontend/src/services/business.ts](../frontend/src/services/business.ts) | 修改 | 保留 userApi 原导出，复用独立用户服务。 |
| [frontend/src/services/users.ts](../frontend/src/services/users.ts) | 新增 | 封装统一编辑、资料创建及统计请求。 |
| [frontend/src/types/api.ts](../frontend/src/types/api.ts) | 修改 | 扩展用户资料、统计、日志和权限类型。 |
| [frontend/src/utils/audit.test.ts](../frontend/src/utils/audit.test.ts) | 新增 | 验证异步受理、完成和失败的区分文案。 |
| [frontend/src/utils/audit.ts](../frontend/src/utils/audit.ts) | 新增 | 集中事件文案及浏览器声明展示。 |
| [frontend/src/utils/user-profile.test.ts](../frontend/src/utils/user-profile.test.ts) | 新增 | 验证新建必填、历史空资料和输入边界。 |
| [frontend/src/utils/user-profile.ts](../frontend/src/utils/user-profile.ts) | 新增 | 集中字段定义、表单转换和验证。 |
| [frontend/src/views/LogsView.test.ts](../frontend/src/views/LogsView.test.ts) | 新增 | 验证分类切换的迟到响应与卸载取消。 |
| [frontend/src/views/LogsView.vue](../frontend/src/views/LogsView.vue) | 新增 | 实现分类、筛选、分页、刷新与错误状态。 |
| [frontend/src/views/UsersView.vue](../frontend/src/views/UsersView.vue) | 修改 | 实现顶部概览、资料创建、统一编辑及独立请求生命周期。 |
| [tools/development/Initialize-RenderingSampleData.ps1](../tools/development/Initialize-RenderingSampleData.ps1) | 修改 | 同步 V14 必需迁移文件和精确版本检查。 |
| [tools/stage8/New-Stage8DatabaseBackup.ps1](../tools/stage8/New-Stage8DatabaseBackup.ps1) | 修改 | 明确允许 V14，保留未知版本拒绝。 |
| [tools/stage8/Test-Stage8BackupRecovery.ps1](../tools/stage8/Test-Stage8BackupRecovery.ps1) | 修改 | 明确接受 V14 备份元数据，保留恢复安全边界。 |

## 脚本静态核验命令

以下为实际运行的 Windows PowerShell 5.1 命令。仅解析脚本 AST，不调用数据库、备份或恢复逻辑；AST 中不可静态求值的非版本表达式跳过，版本集合必须精确匹配。

```powershell
powershell.exe -NoProfile -Command '$taskPaths = @("tools/stage8/New-Stage8DatabaseBackup.ps1", "tools/stage8/Test-Stage8BackupRecovery.ps1", "tools/development/Initialize-RenderingSampleData.ps1"); foreach ($taskPath in $taskPaths) { $taskTokens = $null; $taskErrors = $null; $taskAst = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path (Get-Location).Path $taskPath), [ref]$taskTokens, [ref]$taskErrors); if ($taskErrors.Count) { throw "Script parse failed: $taskPath" }; if ($taskPath -like "tools/stage8/*") { $taskVersions = @($taskAst.FindAll({param($node) $node -is [System.Management.Automation.Language.ArrayExpressionAst]}, $true) | ForEach-Object { try { $taskValue = $_.SafeGetValue(); if ($taskValue -contains "11" -and $taskValue -contains "14") { $taskValue } } catch {} }); if (($taskVersions -join ",") -cne "11,12,13,14" -or $taskVersions -contains "10" -or $taskVersions -contains "15") { throw "Version boundary failed: $taskPath" } }; Write-Output "PASS $taskPath" }; Write-Output "Parser version: $($PSVersionTable.PSVersion)"'
```

## 实际备份与启动（2026-09-05 后续授权）

用户在功能交付后明确授权从本机 `.env` 使用凭据完成备份并启动新版。本段更新实际运行状态，前文“未执行业务迁移”等表述保留功能实现阶段的历史范围。

| 核验 | 结果 |
| --- | --- |
| 升级前业务库 | Flyway V13，66 张表、3 个账号、12 条成果；采集、图维护、导出没有正在执行的任务。 |
| 备份文件 | `E:\AACV_System_Backups\daily\aacv_system_20260905T135154246.sql`，158,969 字节；同时生成当周副本及各自 SHA-256、元数据文件。 |
| 备份安全与完整性 | 已复核受限 ACL、两个副本与 SHA-256 及元数据的一致性、66 条建表语句和转储完成标记。没有执行保留清理或恢复。证据见 [account-upgrade-backup-evidence.json](./account-upgrade-backup-evidence.json)。 |
| 新版后端 | 经进程归属和端口核对后停止本项目旧进程，使用原启动脚本后台启动；Flyway 成功应用 V14，14 个版本全部成功，七个资料/安全版本列存在。 |
| 数据保留 | 3 个原账号与12条成果保留；历史姓名仍为空，安全版本均初始化为0。 |
| 实际 HTTP | 后端 liveness、readiness、graph 均 HTTP 200 / UP；前端 `/login` HTTP 200；经前端代理匿名访问用户统计返回 HTTP 401。 |
| 管理员登录 | 本机 `.env` 有数据库凭据，但没有应用管理员密码；首次验证请求返回400，已停止该登录验证，没有重置或修改账号密码。需使用现有账号密码登录。 |
| Neo4j | 容器探针已有 unhealthy 状态，但应用实际 graph 健康为UP。探针配置问题保留为独立待查项。 |

本次实际执行的主要命令（变量仅在内存中持有，不包含凭据值）：

```powershell
& .\tools\stage8\New-Stage8DatabaseBackup.ps1 -Credential $taskCredential -DatabaseHost $taskUri.Host -DatabasePort $taskUri.Port -DatabaseName aacv_system -OutputPath .\docs\account-upgrade-backup-evidence.json
```

`$taskCredential` 由用户授权读取的 `.env` 数据库配置构造。备份脚本增加可选 `PSCredential` 参数，默认仍使用 `Get-Credential`，并通过 Windows PowerShell 5.1 AST 语法及参数类型检查。脚本保持 UTF-8 BOM，不保存凭据。

前后端由隐藏 PowerShell 进程分别执行现有 `Start-Development.ps1 -Component Backend`、`Start-Development.ps1 -Component Frontend`。仅停止已核对属于本项目的8080、5173监听进程；MySQL及Neo4j保持运行。启动日志确认新版应用启动及一项新迁移成功。直接读取运行中日志首次遇到共享锁限制，改用共享读取后验证通过，不是应用启动失败。

本次只变更备份脚本的凭据接收方式和运维文档，未改应用代码，未重复执行前文的完整应用测试；以实际备份、迁移、数据计数、端口及HTTP检查验证本次操作。实际业务恢复、保留策略和RPO/RTO没有据此推定通过。

## 登录空白页恢复（2026-09-05 后续反馈）

上一阶段只核验 `/login` 的 HTTP 状态，未确认浏览器渲染；用户反馈后在实际 5173 页面复现空白。HTML 和入口脚本返回 200，但入口引用的 Vue 依赖返回 504，`frontend/node_modules/.vite/deps` 已缺失，Vue 未挂载。缓存目录为何缺失尚未查明，不能推定是账号功能或某个测试命令删除所致。

核对进程归属后，仅重启本项目前端，以 `npm --prefix .\frontend run dev -- --force` 重建依赖缓存。实际浏览器显示登录标题、用户名、密码与登录按钮，刷新后正常；入口引用的 Vue 依赖请求恢复 200。

新增 `frontend/e2e/login-rendering.spec.ts`，覆盖桌面及小屏首次打开、刷新、输入控件可编辑、空表单提示，同时断言无页面脚本异常、失败的脚本或样式请求。同步交接文档的启动验收与缓存恢复方法。

本次验证：

- `npm --prefix .\frontend run test:e2e -- login-rendering.spec.ts`：1 项通过（Microsoft Edge，模拟匿名会话）。
- `npm --prefix .\frontend run test`：18 个文件、43 项通过。
- 测试期间保留 5173 开发服务，测试后再次核验实际浏览器与依赖 HTTP 请求，页面正常，未复现缓存丢失。

本次未修改应用业务代码、依赖、前端配置、凭据或数据库，也没有执行真实账号登录；不据此扩张管理员登录验收范围。前后端保持运行。
