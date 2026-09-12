# ORCID 获取停用说明

适用代码：2026-09-12 的 crawler 系统。按用户要求移除 ORCID 后台获取，作者导入页继续显示内部 `authorId`。

## 当前行为

- 单文件和多文件导入不再为作者、导师安排 ORCID 查询，也不自动重试历史任务。
- 已删除查询客户端、候选处理器、补全服务与专用 MyBatis 映射；不再通过这条流程访问 ORCID、OpenAlex 或 Crossref。
- `/api/v1/author-orcids`、`/overview`、`/backfill`、`/apply`、`/{id}/retry` 和 `/{id}/confirm` 均已移除；原有补全配置不再被读取。
- 前端补全面板和专用服务已删除。导入、成果目录及图谱使用原有接口和作者内部标识。

## 历史任务与数据

启动清理组件 `OrcidQuartzCleanup` 在原 JDBC 事务中调用 Quartz `deleteJobs`，仅传入任务 `aacv-author-orcid.author-orcid`，清理该任务及其触发器。该方式按任务键删除，避免单任务删除接口先读取缺失的 SIMPLE 明细而失败。重复启动不会重新创建查询任务，也不清理其他调度。

数据库中的旧任务保存了 `OrcidQuartzJob` 类名，因此保留该类的兼容实现。如果旧触发器先于启动清理执行，它只删除自身调度，不查询外部服务；清理失败会报告错误，不吞掉异常或立即循环重试。

保留 Flyway V18、`author_orcid_task` 中的历史任务和候选、`author_external_id` 中的已有编号及历史审计。编号校验、成果目录和图谱中的已有 ORCID 字段继续使用。没有新增迁移、删除业务数据或改变导入权限、MySQL 导入事务与 Outbox 投影。

## IDEA 更新

停止旧后端后重新编译并运行 `AacvSystemApplication`。删除的源文件需要从编译输出中清理，避免旧控制器或调度类残留。后台获取移除后不需要配置 ORCID 开关或访问令牌。

本次修改不自动重启正在运行的 IDEA JVM，也不替换旧 JAR；启动清理在新版后端运行时执行。

## 验证范围

相关用例包括 `OrcidQuartzCleanupTests`、`OrcidIdTests`、`AuthorImportIntegrationTests` 和 `OpenApiDocumentTests`。隔离数据库用例覆盖正常及 SIMPLE 明细缺失的历史调度、重复清理、已有编号和任务历史保留、作者导入不入队、移除接口返回 404，以及原图谱投影和导入权限。

早期启用 ORCID 的记录位于 `.local/orcid-review-20260912-104302/`，只代表当时版本。本次删除前的文件备份和验证材料位于 Git 忽略的 `.local/orcid-retirement-20260912/`，不代表业务库已清理或当前 JVM 已重启。
